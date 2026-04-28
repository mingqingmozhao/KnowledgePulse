<script setup lang="ts">
import { Client } from '@stomp/stompjs'
import SockJS from 'sockjs-client'
import { computed, onBeforeUnmount, onMounted, reactive, ref, shallowRef, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useRoute, useRouter } from 'vue-router'
import PageHero from '@/components/PageHero.vue'
import CollapsiblePanel from '@/components/CollapsiblePanel.vue'
import FolderTreePanel from '@/components/FolderTreePanel.vue'
import TagCloudPanel from '@/components/TagCloudPanel.vue'
import RichTextEditor from '@/components/RichTextEditor.vue'
import CollaboratorStrip from '@/components/CollaboratorStrip.vue'
import NoteSearchSelect from '@/components/NoteSearchSelect.vue'
import { getAttachments, uploadAttachment } from '@/api/attachment'
import { createNoteComment, deleteNoteComment, getNoteComments } from '@/api/comment'
import { addCollaborator, getCollaborators, removeCollaborator, updateCollaboratorPermission } from '@/api/collaborator'
import { addRelation, getGraphData } from '@/api/graph'
import { createNote, exportNote, getNoteById, getNoteVersions, restoreNoteVersion, updateNote } from '@/api/note'
import { generateShareLink, revokeShare } from '@/api/share'
import { getTemplateById } from '@/api/template'
import { searchUsers } from '@/api/user'
import { useAuthStore } from '@/stores/auth'
import {
  useNoteWorkspaceStore,
  type NoteWorkspaceSnapshot,
  type NoteWorkspaceTab
} from '@/stores/noteWorkspace'
import { useWorkspaceStore } from '@/stores/workspace'
import type { AttachmentItem, Collaborator, GraphData, Note, NoteComment, NoteTemplate, NoteTemplateRequest, NoteVersion, User } from '@/types'
import {
  MAX_ATTACHMENT_BYTES,
  attachmentIcon,
  attachmentMarkdown,
  attachmentTypeLabel,
  extractAttachmentIds,
  formatFileSize,
  isSupportedAttachmentFile,
  normalizeAttachmentLinks
} from '@/utils/attachment'
import { resolveAvatarSrc } from '@/utils/avatar'
import { formatDateOnly, formatDateTime, initials, normalizeTags } from '@/utils/format'
import {
  NOTE_DRAFT_QUERY_KEY,
  NOTE_TEMPLATE_QUERY_KEY,
  buildDraftNoteRoute,
  buildNoteEditRoute
} from '@/utils/noteWorkspace'
import { buildPublicAppUrl } from '@/utils/publicUrl'

type EditorExpose = {
  setValue: (value: string) => void
  getValue: () => string
  getHTML: () => string
  focus: () => void
  getActiveRegion?: () => string
  scrollToHeading?: (headingText: string) => boolean
}

type HtmlChangeMeta = {
  external?: boolean
}

type ExportFormat = 'MARKDOWN' | 'WORD' | 'PDF'
type VersionDiffMode = 'current' | 'previous'
type VersionDiffLineType = 'equal' | 'add' | 'remove'

type VersionDiffLine = {
  id: string
  type: VersionDiffLineType
  oldLineNumber: number | null
  newLineNumber: number | null
  text: string
}

type VersionDiffSummary = {
  added: number
  removed: number
  unchanged: number
  total: number
}

type OutlineItem = {
  id: string
  level: number
  text: string
}

type RelatedNoteItem = {
  noteId: number
  title: string
  relationType: string
  direction: 'incoming' | 'outgoing'
}

type CollaboratorIdentity = {
  id?: number | null
  userId?: number | null
  username?: string | null
  email?: string | null
  nickname?: string | null
  avatar?: string | null
  permission?: string | null
}

const DEFAULT_NOTE_CONTENT = '# 新笔记\n\n从这里开始整理你的想法。'

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()
const workspaceStore = useWorkspaceStore()
const noteWorkspaceStore = useNoteWorkspaceStore()

const editorRef = ref<EditorExpose | null>(null)
const loading = ref(false)
const saving = ref(false)
const templateSaving = ref(false)
const favoriteSubmitting = ref(false)
const exportingFormat = ref<ExportFormat | null>(null)
const versions = ref<NoteVersion[]>([])
const versionDiffDialogVisible = ref(false)
const versionDiffMode = ref<VersionDiffMode>('current')
const selectedDiffVersion = ref<NoteVersion | null>(null)
const comments = ref<NoteComment[]>([])
const commentsLoading = ref(false)
const commentSubmitting = ref(false)
const attachments = ref<AttachmentItem[]>([])
const attachmentsLoading = ref(false)
const attachmentUploading = ref(false)
const attachmentInputRef = ref<HTMLInputElement | null>(null)
const collaborators = ref<Collaborator[]>([])
const collaboratorCandidates = ref<User[]>([])
const collaboratorSearchLoading = ref(false)
const typingUsers = ref<Collaborator[]>([])
const activeUsers = ref<Collaborator[]>([])
const noteGraph = ref<GraphData>({
  nodes: [],
  links: []
})
const noteGraphLoading = ref(false)
const quickOpenNoteId = ref<number | null>(null)
const shareLink = ref('')
const lastSavedAt = ref('')
const lastSyncMessage = ref('')
const realtimeState = ref<'offline' | 'connecting' | 'online'>('offline')
const stompClient = shallowRef<Client | null>(null)
const dirty = ref(false)
const hydratingForm = ref(false)
const skipNextRouteSnapshot = ref(false)
const lastLocalInputAt = ref(0)
const restoredDraftNoticeVisible = ref(false)
const activeEditingRegion = ref('正文')

const noteForm = reactive({
  id: null as number | null,
  title: '',
  content: DEFAULT_NOTE_CONTENT,
  htmlContent: '',
  tagsText: '',
  folderId: null as number | null,
  dailyNoteDate: '',
  dailyNote: false,
  ownerUserId: null as number | null,
  ownerUsername: '',
  ownerNickname: '',
  ownerAvatar: '',
  currentUserPermission: '',
  currentUserCanManage: false,
  isPublic: 0,
  favorited: false
})

const shareForm = reactive({
  isPublic: 0 as 0 | 1 | 2,
  password: ''
})

const collaboratorForm = reactive({
  userId: null as number | null,
  permission: 'EDIT' as Collaborator['permission']
})

const relationForm = reactive({
  targetNoteId: null as number | null,
  relationType: '相关'
})

const commentForm = reactive({
  content: ''
})

const shareModeOptions = [
  {
    label: '关闭分享',
    value: 0 as const,
    description: '只有你和协作者可以访问'
  },
  {
    label: '公开分享',
    value: 1 as const,
    description: '拿到链接的人都可以访问'
  },
  {
    label: '登录可见',
    value: 2 as const,
    description: '访问链接前需要先登录'
  }
]

const collaboratorPermissionOptions = ['READ', 'EDIT', 'OWNER'] as const
const typingTimers = new Map<number, number>()
let syncTimer: number | null = null
let presenceTimer: number | null = null
let localSnapshotTimer: number | null = null
let lastPublishedEditSignature = ''

const routeNoteId = computed(() => {
  const raw = route.params.id

  if (typeof raw !== 'string') {
    return null
  }

  const parsed = Number(raw)
  return Number.isFinite(parsed) ? parsed : null
})

const routeDraftKey = computed(() => {
  const raw = route.query[NOTE_DRAFT_QUERY_KEY]
  return typeof raw === 'string' && raw.trim() ? raw.trim() : null
})

const routeTemplateId = computed(() => {
  const raw = route.query[NOTE_TEMPLATE_QUERY_KEY]

  if (typeof raw !== 'string') {
    return null
  }

  const parsed = Number(raw)
  return Number.isFinite(parsed) && parsed > 0 ? parsed : null
})

const parsedTags = computed(() => normalizeTags(noteForm.tagsText))

const workspaceTabs = computed(() => noteWorkspaceStore.tabs)
const activeWorkspaceTabKey = computed(() => noteWorkspaceStore.activeKey)
const activeWorkspaceTab = computed(() => noteWorkspaceStore.activeTab)
const workspaceDirtyCount = computed(() => workspaceTabs.value.filter((tab) => tab.dirty).length)
const workspaceSavedCount = computed(() => workspaceTabs.value.length - workspaceDirtyCount.value)
const workspacePinnedCount = computed(() => workspaceTabs.value.filter((tab) => tab.pinned).length)
const workspaceClosableSavedCount = computed(() =>
  workspaceTabs.value.filter(
    (tab) => tab.key !== activeWorkspaceTabKey.value && !tab.dirty && !tab.pinned
  ).length
)
const workspaceOtherDirtyCount = computed(() =>
  workspaceTabs.value.filter(
    (tab) => tab.key !== activeWorkspaceTabKey.value && tab.dirty && !tab.pinned
  ).length
)
const workspaceTabsToRight = computed(() => {
  const activeIndex = workspaceTabs.value.findIndex((tab) => tab.key === activeWorkspaceTabKey.value)

  if (activeIndex === -1) {
    return []
  }

  return workspaceTabs.value.filter((tab, index) => index > activeIndex && !tab.pinned)
})
const workspaceRightClosableCount = computed(() => workspaceTabsToRight.value.length)
const activeWorkspacePinned = computed(() => Boolean(activeWorkspaceTab.value?.pinned))
const currentWorkspaceLabel = computed(() => (noteForm.id ? `笔记 #${noteForm.id}` : '草稿标签'))
const currentWorkspaceFolderLabel = computed(() => {
  if (noteForm.folderId === null) {
    return '未归档'
  }

  return workspaceStore.folderOptions.find((item) => item.value === noteForm.folderId)?.label || '当前文件夹'
})
const currentWorkspaceStateLabel = computed(() => {
  if (dirty.value) {
    return '当前标签未保存'
  }

  if (noteForm.id) {
    return '当前标签已同步'
  }

  return '草稿待保存'
})
const currentPermissionLabel = computed(() => noteForm.currentUserPermission || (noteForm.id ? 'READ' : 'OWNER'))
const workspacePreviewTags = computed(() => parsedTags.value.slice(0, 4))
const restoredDraftNoticeTitle = computed(() => noteForm.title.trim() || '未命名草稿')
const commentCount = computed(() => comments.value.length)
const attachmentCount = computed(() => attachments.value.length)
const referencedAttachmentIds = computed(() => extractAttachmentIds(noteForm.content, noteForm.htmlContent))
const noteLookup = computed(() => {
  const map = new Map<number, Note>()
  workspaceStore.notes.forEach((note) => {
    map.set(note.id, note)
  })
  return map
})
const noteConnections = computed(() => {
  if (!noteForm.id) {
    return {
      incoming: [] as RelatedNoteItem[],
      outgoing: [] as RelatedNoteItem[]
    }
  }

  const noteId = noteForm.id
  const nodesById = new Map(noteGraph.value.nodes.map((node) => [node.id, node]))
  const incoming: RelatedNoteItem[] = []
  const outgoing: RelatedNoteItem[] = []

  noteGraph.value.links.forEach((link) => {
    if (link.source === noteId && link.target !== noteId) {
      const targetNode = nodesById.get(link.target)
      outgoing.push({
        noteId: link.target,
        title: targetNode?.name || noteLookup.value.get(link.target)?.title || `笔记 ${link.target}`,
        relationType: link.relationType,
        direction: 'outgoing'
      })
    }

    if (link.target === noteId && link.source !== noteId) {
      const sourceNode = nodesById.get(link.source)
      incoming.push({
        noteId: link.source,
        title: sourceNode?.name || noteLookup.value.get(link.source)?.title || `笔记 ${link.source}`,
        relationType: link.relationType,
        direction: 'incoming'
      })
    }
  })

  return {
    incoming,
    outgoing
  }
})
const outlineItems = computed(() => buildOutline(noteForm.content))
const otherEditingUsers = computed(() => typingUsers.value.filter((member) => member.userId !== authStore.user?.id))
const sameRegionEditors = computed(() =>
  otherEditingUsers.value.filter(
    (member) =>
      Boolean(member.editingRegion) &&
      member.editingRegion === activeEditingRegion.value
  )
)
const sameRegionEditorText = computed(() => {
  if (!sameRegionEditors.value.length) {
    return ''
  }

  return sameRegionEditors.value.map((member) => displayUserName(member)).join('、')
})
const editingRegionNoticeTitle = computed(() => {
  if (sameRegionEditors.value.length) {
    return `${sameRegionEditorText.value} 正在编辑「${activeEditingRegion.value}」`
  }

  if (otherEditingUsers.value.length === 1) {
    const member = otherEditingUsers.value[0]
    return `${displayUserName(member)} 正在编辑「${member.editingRegion || '正文'}」`
  }

  return `${otherEditingUsers.value.length} 位协作者正在编辑`
})
const editingRegionNoticeDescription = computed(() =>
  sameRegionEditors.value.length
    ? '你当前也在这个区域，建议先切到其他章节或稍后再改，避免双方在同一区域反复覆盖。'
    : '这是协作软提示，不会阻止编辑；如果要减少冲突，可以优先选择不同章节处理。'
)
const editingRegionNoticeTags = computed(() =>
  otherEditingUsers.value.map((member) => `${displayUserName(member)} / ${member.editingRegion || '正文'}`)
)

const selectedDiffPreviousVersion = computed(() => {
  if (!selectedDiffVersion.value) {
    return null
  }

  const selectedVersionNumber = selectedDiffVersion.value.version
  return (
    versions.value
      .filter((version) => version.version < selectedVersionNumber)
      .sort((left, right) => right.version - left.version)[0] ?? null
  )
})

const effectiveVersionDiffMode = computed<VersionDiffMode>(() =>
  versionDiffMode.value === 'previous' && selectedDiffPreviousVersion.value ? 'previous' : 'current'
)

const versionDiffOldLabel = computed(() => {
  if (!selectedDiffVersion.value) {
    return '历史版本'
  }

  if (effectiveVersionDiffMode.value === 'previous' && selectedDiffPreviousVersion.value) {
    return `版本 ${selectedDiffPreviousVersion.value.version}`
  }

  return `版本 ${selectedDiffVersion.value.version}`
})

const versionDiffNewLabel = computed(() => {
  if (!selectedDiffVersion.value) {
    return '当前内容'
  }

  if (effectiveVersionDiffMode.value === 'previous') {
    return `版本 ${selectedDiffVersion.value.version}`
  }

  return dirty.value ? '当前编辑内容（含未保存）' : '当前已保存内容'
})

const versionDiffRows = computed<VersionDiffLine[]>(() => {
  if (!selectedDiffVersion.value) {
    return []
  }

  const oldContent =
    effectiveVersionDiffMode.value === 'previous' && selectedDiffPreviousVersion.value
      ? selectedDiffPreviousVersion.value.contentSnapshot
      : selectedDiffVersion.value.contentSnapshot
  const newContent =
    effectiveVersionDiffMode.value === 'previous'
      ? selectedDiffVersion.value.contentSnapshot
      : noteForm.content

  return buildLineDiff(oldContent ?? '', newContent ?? '')
})

const versionDiffSummary = computed<VersionDiffSummary>(() => {
  const added = versionDiffRows.value.filter((line) => line.type === 'add').length
  const removed = versionDiffRows.value.filter((line) => line.type === 'remove').length
  const unchanged = versionDiffRows.value.filter((line) => line.type === 'equal').length

  return {
    added,
    removed,
    unchanged,
    total: versionDiffRows.value.length
  }
})

const noteOptions = computed(() =>
  workspaceStore.notes
    .filter((note) => note.id !== noteForm.id)
    .map((note) => ({
      label: note.title,
      value: note.id
    }))
)

const ownerMember = computed<Collaborator | null>(() => {
  if (!noteForm.ownerUserId) {
    return null
  }

  return {
    userId: noteForm.ownerUserId,
    permission: 'OWNER',
    username: noteForm.ownerUsername || undefined,
    nickname: noteForm.ownerNickname || noteForm.ownerUsername || '笔记拥有者',
    avatar: noteForm.ownerAvatar || undefined,
    active: noteForm.ownerUserId === authStore.user?.id
  }
})

const canManageCollaborators = computed(() => noteForm.currentUserCanManage)

const availableCollaboratorCandidates = computed(() => {
  const existingIds = new Set(collaborators.value.map((member) => member.userId))

  if (noteForm.ownerUserId) {
    existingIds.add(noteForm.ownerUserId)
  }

  return collaboratorCandidates.value.filter((user) => !existingIds.has(user.id))
})

const collaboratorRoster = computed(() => {
  const merged = new Map<number, Collaborator>()

  if (ownerMember.value) {
    merged.set(ownerMember.value.userId, ownerMember.value)
  }

  collaborators.value.forEach((member) => {
    merged.set(member.userId, {
      ...merged.get(member.userId),
      ...member,
      nickname: displayUserName(member),
      active: member.userId === authStore.user?.id || Boolean(member.active)
    })
  })

  activeUsers.value.forEach((member) => {
    const mergedMember = {
      ...merged.get(member.userId),
      ...member
    }

    merged.set(member.userId, {
      ...mergedMember,
      nickname: displayUserName(mergedMember),
      active: true
    })
  })

  typingUsers.value.forEach((member) => {
    const mergedMember = {
      ...merged.get(member.userId),
      ...member
    }

    merged.set(member.userId, {
      ...mergedMember,
      nickname: displayUserName(mergedMember),
      typing: true,
      active: true
    })
  })

  return [...merged.values()]
})

const realtimeLabel = computed(() => {
  if (realtimeState.value === 'online') {
    return '协作已连接'
  }

  if (realtimeState.value === 'connecting') {
    return '协作连接中'
  }

  return '协作未连接'
})

const saveLabel = computed(() => (lastSavedAt.value ? formatDateTime(lastSavedAt.value) : '尚未保存'))

const shareModeSummary = computed(() => {
  if (shareForm.isPublic === 2) {
    return '当前分享需要登录后访问，适合团队内部协作。'
  }

  if (shareForm.isPublic === 1) {
    return '当前分享为公开链接，适合临时对外展示。'
  }

  return '当前未开启分享。'
})
const canManageShare = computed(() => noteForm.currentUserCanManage)
const sharePermissionSummary = computed(() => {
  if (canManageShare.value) {
    return shareModeSummary.value
  }

  const permission = noteForm.currentUserPermission || 'READ'
  return `你当前是 ${permission} 权限，不能生成、修改或关闭分享链接。只有笔记拥有者或 OWNER 协作者可以管理分享。`
})
const shareActionLabel = computed(() => {
  if (!canManageShare.value) {
    return '无分享权限'
  }

  if (shareForm.isPublic !== 0) {
    return '生成链接'
  }

  return shareLink.value ? '关闭分享' : '生成公开链接'
})

const collaboratorHelpText = computed(() => {
  if (canManageCollaborators.value) {
    return '通过昵称、用户名或邮箱搜索用户后即可加入当前笔记。'
  }

  const permission = noteForm.currentUserPermission || 'READ'
  return `你当前是 ${permission} 权限，只能查看成员信息，不能调整协作者权限。`
})

const ownerSummary = computed(() => {
  if (!ownerMember.value) {
    return ''
  }

  const segments: string[] = []

  if (noteForm.ownerUsername) {
    segments.push(`@${noteForm.ownerUsername}`)
  }

  segments.push(noteForm.ownerUserId === authStore.user?.id ? '当前由你拥有' : '笔记拥有者')
  return segments.join(' / ')
})

const pageTitle = computed(() => {
  if (noteForm.title.trim()) {
    return noteForm.title.trim()
  }

  return noteForm.id ? '编辑笔记' : '新建草稿'
})

const pageDescription = computed(() => {
  if (noteForm.dailyNote) {
    return '这是每日笔记。适合记录当天推进、灵感闪念和后续待续写的内容。'
  }

  if (noteForm.id) {
    return '在同一个工作区里打开多篇笔记，切换时保留上下文和未保存内容。'
  }

  return '当前是草稿标签，保存前也可以先切去查看别的笔记。'
})

const dailyNoteBannerTitle = computed(() =>
  noteForm.dailyNoteDate ? `${formatDateOnly(noteForm.dailyNoteDate)} 的每日笔记` : ''
)

const dailyNoteBannerDescription = computed(() => {
  if (!noteForm.dailyNoteDate) {
    return ''
  }

  if (noteForm.dailyNoteDate === new Date().toISOString().slice(0, 10)) {
    return '今天的记录会和工作区标签一起保留，适合边写边整理当天的推进节奏。'
  }

  return '这是一篇按日期归档的每日笔记，适合补写复盘、追踪上下文或继续延展当天的想法。'
})

function displayUserName(member: CollaboratorIdentity): string {
  return member.nickname?.trim() || member.username?.trim() || member.email?.trim() || `成员 ${member.userId ?? member.id}`
}

function commentAuthorName(comment: NoteComment): string {
  return comment.nickname?.trim() || comment.username?.trim() || `成员 ${comment.userId}`
}

function collaboratorMeta(member: CollaboratorIdentity): string {
  const segments: string[] = []

  if (member.username) {
    segments.push(`@${member.username}`)
  }

  if (member.email) {
    segments.push(member.email)
  }

  if (member.permission) {
    segments.push(member.permission)
  }

  return segments.join(' / ') || '暂无更多资料'
}

function collaboratorOptionLabel(user: User) {
  return displayUserName(user)
}

function collaboratorOptionMeta(user: User) {
  const segments: string[] = []

  if (user.username) {
    segments.push(`@${user.username}`)
  }

  if (user.email) {
    segments.push(user.email)
  }

  return segments.join(' / ') || '暂无更多资料'
}

function relatedNoteMeta(item: RelatedNoteItem) {
  const note = noteLookup.value.get(item.noteId)
  const segments = [item.relationType]

  if (note?.folderName) {
    segments.push(note.folderName)
  }

  return segments.join(' / ')
}

function buildOutline(content: string) {
  const lines = content.split('\n')
  const items: OutlineItem[] = []
  let insideCodeBlock = false

  lines.forEach((line, index) => {
    const trimmed = line.trim()

    if (trimmed.startsWith('```')) {
      insideCodeBlock = !insideCodeBlock
      return
    }

    if (insideCodeBlock) {
      return
    }

    const matched = /^(#{1,6})\s+(.+)$/.exec(trimmed)

    if (!matched) {
      return
    }

    items.push({
      id: `outline-${index}`,
      level: matched[1].length,
      text: matched[2].trim()
    })
  })

  return items
}

function splitDiffLines(content: string) {
  const normalized = content.replace(/\r\n/g, '\n').replace(/\r/g, '\n')
  return normalized ? normalized.split('\n') : []
}

function buildLineDiff(oldContent: string, newContent: string): VersionDiffLine[] {
  const oldLines = splitDiffLines(oldContent)
  const newLines = splitDiffLines(newContent)
  const oldCount = oldLines.length
  const newCount = newLines.length
  const lcsTable = Array.from({ length: oldCount + 1 }, () => Array<number>(newCount + 1).fill(0))

  for (let oldIndex = oldCount - 1; oldIndex >= 0; oldIndex -= 1) {
    for (let newIndex = newCount - 1; newIndex >= 0; newIndex -= 1) {
      lcsTable[oldIndex][newIndex] =
        oldLines[oldIndex] === newLines[newIndex]
          ? lcsTable[oldIndex + 1][newIndex + 1] + 1
          : Math.max(lcsTable[oldIndex + 1][newIndex], lcsTable[oldIndex][newIndex + 1])
    }
  }

  const rows: VersionDiffLine[] = []
  let oldIndex = 0
  let newIndex = 0

  while (oldIndex < oldCount || newIndex < newCount) {
    if (oldIndex < oldCount && newIndex < newCount && oldLines[oldIndex] === newLines[newIndex]) {
      rows.push({
        id: `equal-${oldIndex}-${newIndex}`,
        type: 'equal',
        oldLineNumber: oldIndex + 1,
        newLineNumber: newIndex + 1,
        text: oldLines[oldIndex]
      })
      oldIndex += 1
      newIndex += 1
      continue
    }

    if (
      newIndex < newCount &&
      (oldIndex >= oldCount || lcsTable[oldIndex][newIndex + 1] >= lcsTable[oldIndex + 1][newIndex])
    ) {
      rows.push({
        id: `add-${oldIndex}-${newIndex}`,
        type: 'add',
        oldLineNumber: null,
        newLineNumber: newIndex + 1,
        text: newLines[newIndex]
      })
      newIndex += 1
      continue
    }

    if (oldIndex < oldCount) {
      rows.push({
        id: `remove-${oldIndex}-${newIndex}`,
        type: 'remove',
        oldLineNumber: oldIndex + 1,
        newLineNumber: null,
        text: oldLines[oldIndex]
      })
      oldIndex += 1
    }
  }

  return rows
}

function formatDiffLineText(text: string) {
  return text || '（空行）'
}

function openVersionDiff(version: NoteVersion, mode: VersionDiffMode = 'current') {
  selectedDiffVersion.value = version
  versionDiffMode.value = mode
  versionDiffDialogVisible.value = true
}

function getWorkspaceTabTitle() {
  if (noteForm.title.trim()) {
    return noteForm.title.trim()
  }

  return noteForm.id ? `笔记 ${noteForm.id}` : '未命名草稿'
}

function createDefaultSnapshot(): NoteWorkspaceSnapshot {
  return {
    id: null,
    title: '',
    content: DEFAULT_NOTE_CONTENT,
    htmlContent: '',
    tagsText: '',
    folderId: workspaceStore.activeFolderId,
    dailyNoteDate: '',
    dailyNote: false,
    ownerUserId: authStore.user?.id ?? null,
    ownerUsername: authStore.user?.username || '',
    ownerNickname: authStore.displayName,
    ownerAvatar: authStore.user?.avatar || '',
    currentUserPermission: 'OWNER',
    currentUserCanManage: true,
    isPublic: 0,
    favorited: false,
    shareMode: 0,
    shareLink: '',
    lastSavedAt: '',
    dirty: false
  }
}

function createSnapshotFromTemplate(template: NoteTemplate): NoteWorkspaceSnapshot {
  return {
    ...createDefaultSnapshot(),
    title: template.name,
    content: template.content || DEFAULT_NOTE_CONTENT,
    htmlContent: template.htmlContent || '',
    tagsText: (template.tags ?? []).join(', '),
    dirty: true
  }
}

function createSnapshotFromNote(note: Note): NoteWorkspaceSnapshot {
  return {
    id: note.id,
    title: note.title,
    content: note.content ?? '',
    htmlContent: note.htmlContent ?? '',
    tagsText: (note.tags ?? []).join(', '),
    folderId: note.folderId ?? null,
    dailyNoteDate: note.dailyNoteDate ?? '',
    dailyNote: Boolean(note.dailyNote ?? note.dailyNoteDate),
    ownerUserId: note.ownerUserId ?? null,
    ownerUsername: note.ownerUsername ?? '',
    ownerNickname: note.ownerNickname ?? '',
    ownerAvatar: note.ownerAvatar ?? '',
    currentUserPermission: note.currentUserPermission ?? '',
    currentUserCanManage: Boolean(note.currentUserCanManage),
    isPublic: note.isPublic ?? 0,
    favorited: Boolean(note.favorited),
    shareMode: ((note.isPublic ?? 0) as 0 | 1 | 2) || 0,
    shareLink: '',
    lastSavedAt: note.updateTime ?? '',
    dirty: false
  }
}

function areTagListsEqual(leftTags: string[], rightTags: string[]) {
  const normalizedLeft = [...leftTags].sort((left, right) => left.localeCompare(right, 'zh-CN'))
  const normalizedRight = [...rightTags].sort((left, right) => left.localeCompare(right, 'zh-CN'))

  return (
    normalizedLeft.length === normalizedRight.length &&
    normalizedLeft.every((tag, index) => tag === normalizedRight[index])
  )
}

function isSnapshotSameAsPersistedNote(snapshot: NoteWorkspaceSnapshot, note: Note) {
  return (
    snapshot.id === note.id &&
    snapshot.title.trim() === note.title.trim() &&
    normalizeAttachmentLinks(snapshot.content ?? '') === normalizeAttachmentLinks(note.content ?? '') &&
    (snapshot.folderId ?? null) === (note.folderId ?? null) &&
    areTagListsEqual(normalizeTags(snapshot.tagsText), normalizeTags((note.tags ?? []).join(', ')))
  )
}

function applySnapshot(snapshot: NoteWorkspaceSnapshot) {
  hydratingForm.value = true
  const normalizedContent = normalizeAttachmentLinks(snapshot.content)
  const normalizedHtmlContent = normalizeAttachmentLinks(snapshot.htmlContent)
  const snapshotDirty = snapshot.dirty

  noteForm.id = snapshot.id
  noteForm.title = snapshot.title
  noteForm.content = normalizedContent
  noteForm.htmlContent = normalizedHtmlContent
  noteForm.tagsText = snapshot.tagsText
  noteForm.folderId = snapshot.folderId
  noteForm.dailyNoteDate = snapshot.dailyNoteDate
  noteForm.dailyNote = snapshot.dailyNote
  noteForm.ownerUserId = snapshot.ownerUserId
  noteForm.ownerUsername = snapshot.ownerUsername
  noteForm.ownerNickname = snapshot.ownerNickname
  noteForm.ownerAvatar = snapshot.ownerAvatar
  noteForm.currentUserPermission = snapshot.currentUserPermission
  noteForm.currentUserCanManage = snapshot.currentUserCanManage
  noteForm.isPublic = snapshot.isPublic
  noteForm.favorited = snapshot.favorited

  shareForm.isPublic = snapshot.shareMode
  shareForm.password = ''
  shareLink.value = snapshot.shareLink

  collaboratorCandidates.value = []
  collaboratorForm.userId = null
  collaboratorForm.permission = 'EDIT'
  relationForm.targetNoteId = null
  relationForm.relationType = '相关'
  typingUsers.value = []
  activeUsers.value = []
  lastSavedAt.value = snapshot.lastSavedAt
  lastSyncMessage.value = ''
  dirty.value = snapshotDirty

  editorRef.value?.setValue(normalizedContent)
  window.setTimeout(() => {
    hydratingForm.value = false
    dirty.value = snapshotDirty
    syncActiveTabMeta()
  }, 0)
}

function applyNote(note: Note) {
  applySnapshot(createSnapshotFromNote(note))
}

function captureSnapshot(overrides: Partial<NoteWorkspaceSnapshot> = {}): NoteWorkspaceSnapshot {
  return {
    id: noteForm.id,
    title: noteForm.title,
    content: noteForm.content,
    htmlContent: noteForm.htmlContent,
    tagsText: noteForm.tagsText,
    folderId: noteForm.folderId,
    dailyNoteDate: noteForm.dailyNoteDate,
    dailyNote: noteForm.dailyNote,
    ownerUserId: noteForm.ownerUserId,
    ownerUsername: noteForm.ownerUsername,
    ownerNickname: noteForm.ownerNickname,
    ownerAvatar: noteForm.ownerAvatar,
    currentUserPermission: noteForm.currentUserPermission,
    currentUserCanManage: noteForm.currentUserCanManage,
    isPublic: noteForm.isPublic,
    favorited: noteForm.favorited,
    shareMode: shareForm.isPublic,
    shareLink: shareLink.value,
    lastSavedAt: lastSavedAt.value,
    dirty: dirty.value,
    ...overrides
  }
}

function getCurrentWorkspaceSnapshotKey() {
  if (noteForm.id) {
    return `note-${noteForm.id}`
  }

  return routeDraftKey.value ?? noteWorkspaceStore.activeKey
}

function syncActiveTabMeta() {
  if (!noteWorkspaceStore.activeKey) {
    return
  }

  noteWorkspaceStore.touchTab(noteWorkspaceStore.activeKey, {
    title: getWorkspaceTabTitle(),
    dirty: dirty.value
  })
}

function persistActiveTabSnapshot(overrides: Partial<NoteWorkspaceSnapshot> = {}) {
  const targetKey = getCurrentWorkspaceSnapshotKey()

  if (!targetKey) {
    return
  }

  noteWorkspaceStore.saveSnapshot(targetKey, captureSnapshot(overrides))

  if (targetKey === noteWorkspaceStore.activeKey) {
    syncActiveTabMeta()
  }
}

function clearLocalSnapshotTimer() {
  if (localSnapshotTimer !== null) {
    window.clearTimeout(localSnapshotTimer)
    localSnapshotTimer = null
  }
}

function flushLocalWorkspaceSnapshot() {
  clearLocalSnapshotTimer()

  if (hydratingForm.value || !noteWorkspaceStore.activeKey) {
    return
  }

  persistActiveTabSnapshot()
}

function flushWorkspaceBeforeLeaving() {
  if (dirty.value) {
    publishEdit({
      silent: true
    })
  }

  publishPresence('leave')
  flushLocalWorkspaceSnapshot()
}

function scheduleLocalWorkspaceSnapshot(delay = 180) {
  if (hydratingForm.value || !noteWorkspaceStore.activeKey) {
    return
  }

  clearLocalSnapshotTimer()
  localSnapshotTimer = window.setTimeout(() => {
    localSnapshotTimer = null
    persistActiveTabSnapshot()
  }, delay)
}

function handleDocumentVisibilityChange() {
  if (document.visibilityState === 'hidden') {
    flushLocalWorkspaceSnapshot()
  }
}

function markDirty(nextDirty = true) {
  if (dirty.value === nextDirty) {
    syncActiveTabMeta()
    return
  }

  dirty.value = nextDirty

  if (nextDirty) {
    persistActiveTabSnapshot()
    return
  }

  syncActiveTabMeta()
}

function mergeFavoriteState(note: Note) {
  noteForm.favorited = Boolean(note.favorited)
  noteForm.isPublic = note.isPublic ?? noteForm.isPublic
  persistActiveTabSnapshot()
}

function clearTypingTimers() {
  typingTimers.forEach((timerId) => {
    window.clearTimeout(timerId)
  })
  typingTimers.clear()
}

function clearPresenceTimer() {
  if (presenceTimer) {
    window.clearInterval(presenceTimer)
    presenceTimer = null
  }
}

function publishPresence(action: 'join' | 'heartbeat' | 'leave' = 'heartbeat') {
  if (!noteForm.id || !stompClient.value?.connected || !authStore.user?.id) {
    return
  }

  stompClient.value.publish({
    destination: `/app/note/${noteForm.id}/presence`,
    body: JSON.stringify({
      action,
      userId: authStore.user.id,
      username: authStore.user.username,
      nickname: authStore.displayName,
      avatar: authStore.user.avatar,
      permission: noteForm.currentUserPermission || 'EDIT',
      sentAt: new Date().toISOString()
    })
  })
}

function disconnectRealtime() {
  if (syncTimer) {
    window.clearInterval(syncTimer)
    syncTimer = null
  }

  publishPresence('leave')
  clearPresenceTimer()
  clearTypingTimers()
  typingUsers.value = []
  activeUsers.value = []
  realtimeState.value = 'offline'

  if (stompClient.value) {
    void stompClient.value.deactivate()
    stompClient.value = null
  }
}

function setActiveEditingRegion(region?: string | null) {
  const normalizedRegion = region?.replace(/\s+/g, ' ').trim()
  activeEditingRegion.value = normalizedRegion || '正文'
}

function publishTyping(region = activeEditingRegion.value) {
  if (!noteForm.id || !stompClient.value?.connected) {
    return
  }

  setActiveEditingRegion(region)

  stompClient.value.publish({
    destination: `/app/note/${noteForm.id}/typing`,
    body: JSON.stringify({
      userId: authStore.user?.id ?? 0,
      nickname: authStore.displayName,
      permission: noteForm.currentUserPermission || 'EDIT',
      editingRegion: activeEditingRegion.value
    })
  })
}

function buildRealtimeEditSignature() {
  return JSON.stringify([noteForm.id, noteForm.title, noteForm.content, noteForm.htmlContent])
}

function publishEdit(options: { force?: boolean; silent?: boolean } = {}) {
  if (!noteForm.id || !stompClient.value?.connected) {
    return
  }

  const editSignature = buildRealtimeEditSignature()

  if (!options.force && editSignature === lastPublishedEditSignature) {
    return
  }

  stompClient.value.publish({
    destination: `/app/note/${noteForm.id}/edit`,
    body: JSON.stringify({
      userId: authStore.user?.id ?? 0,
      nickname: authStore.displayName,
      title: noteForm.title,
      content: noteForm.content,
      htmlContent: noteForm.htmlContent,
      updatedAt: new Date().toISOString()
    })
  })

  lastPublishedEditSignature = editSignature
  if (!options.silent) {
    lastSyncMessage.value = '协作内容已广播，仍需点击保存才会写入笔记'
  }
  persistActiveTabSnapshot()
}

function handleTypingPayload(body: string) {
  try {
    const payload = JSON.parse(body) as {
      userId?: number
      nickname?: string
      permission?: string
      editingRegion?: string
    }

    if (!payload.userId || payload.userId === authStore.user?.id) {
      return
    }

    typingUsers.value = [
      ...typingUsers.value.filter((member) => member.userId !== payload.userId),
      {
        userId: payload.userId,
        nickname: payload.nickname || `成员 ${payload.userId}`,
        permission: payload.permission || 'EDIT',
        editingRegion: payload.editingRegion || '正文',
        typing: true,
        active: true
      }
    ]

    const existingTimer = typingTimers.get(payload.userId)

    if (existingTimer) {
      window.clearTimeout(existingTimer)
    }

    const timerId = window.setTimeout(() => {
      typingUsers.value = typingUsers.value.filter((member) => member.userId !== payload.userId)
      typingTimers.delete(payload.userId!)
    }, 9000)

    typingTimers.set(payload.userId, timerId)
  } catch {
    return
  }
}

function handlePresencePayload(body: string) {
  try {
    const payload = JSON.parse(body) as {
      users?: Array<{
        userId?: number
        username?: string
        nickname?: string
        avatar?: string | null
        permission?: string
        active?: boolean
      }>
    }

    activeUsers.value = (payload.users ?? [])
      .filter((member) => Boolean(member.userId))
      .map((member) => ({
        userId: member.userId!,
        username: member.username || undefined,
        nickname: member.nickname || member.username || `成员 ${member.userId}`,
        avatar: member.avatar || undefined,
        permission: member.permission || 'EDIT',
        active: Boolean(member.active ?? true)
      }))
  } catch {
    return
  }
}

function handleRemoteEdit(body: string) {
  try {
    const payload = JSON.parse(body) as {
      userId?: number
      nickname?: string
      title?: string
      content?: string
      htmlContent?: string
    }

    if (!payload.userId || payload.userId === authStore.user?.id) {
      return
    }

    lastSyncMessage.value = `${payload.nickname || `成员 ${payload.userId}`} 刚刚同步了内容`

    if (Date.now() - lastLocalInputAt.value <= 1200) {
      return
    }

    let appliedRemoteChanges = false

    if (typeof payload.title === 'string') {
      noteForm.title = payload.title
      appliedRemoteChanges = true
    }

    if (typeof payload.content === 'string') {
      const normalizedContent = normalizeAttachmentLinks(payload.content)
      noteForm.content = normalizedContent
      editorRef.value?.setValue(normalizedContent)
      appliedRemoteChanges = true
    }

    if (typeof payload.htmlContent === 'string') {
      noteForm.htmlContent = normalizeAttachmentLinks(payload.htmlContent)
      appliedRemoteChanges = true
    }

    if (appliedRemoteChanges) {
      dirty.value = true
      lastPublishedEditSignature = buildRealtimeEditSignature()
      persistActiveTabSnapshot()
    }
  } catch {
    return
  }
}

function connectRealtime() {
  disconnectRealtime()

  if (!noteForm.id) {
    return
  }

  realtimeState.value = 'connecting'

  const client = new Client({
    webSocketFactory: () => new SockJS('/api/v1/ws'),
    reconnectDelay: 5000,
    connectHeaders: authStore.accessToken
      ? {
          Authorization: `Bearer ${authStore.accessToken}`
        }
      : {},
    onConnect: () => {
      realtimeState.value = 'online'

      client.subscribe(`/topic/note/${noteForm.id}`, (message) => {
        handleRemoteEdit(message.body)
      })

      client.subscribe(`/topic/note/${noteForm.id}/typing`, (message) => {
        handleTypingPayload(message.body)
      })

      client.subscribe(`/topic/note/${noteForm.id}/presence`, (message) => {
        handlePresencePayload(message.body)
      })

      publishPresence('join')
      presenceTimer = window.setInterval(() => {
        publishPresence('heartbeat')
      }, 10000)

      syncTimer = window.setInterval(() => {
        if (dirty.value) {
          publishEdit()
        }
      }, 800)
    },
    onWebSocketClose: () => {
      clearPresenceTimer()
      activeUsers.value = []
      realtimeState.value = 'offline'
    },
    onStompError: () => {
      clearPresenceTimer()
      activeUsers.value = []
      realtimeState.value = 'offline'
    }
  })

  stompClient.value = client
  client.activate()
}

function handleEditorInput(value: string) {
  const contentChanged = value !== noteForm.content
  noteForm.content = value

  if (hydratingForm.value || !contentChanged) {
    return
  }

  setActiveEditingRegion(editorRef.value?.getActiveRegion?.() || activeEditingRegion.value)
  lastLocalInputAt.value = Date.now()
  markDirty(true)
  scheduleLocalWorkspaceSnapshot()
  publishTyping()
}

function handleEditingRegionChange(region: string) {
  setActiveEditingRegion(region)
}

function handleTitleEditing() {
  setActiveEditingRegion('标题')
  publishTyping('标题')
}

function handleHtmlChange(value: string, meta?: HtmlChangeMeta) {
  const htmlChanged = value !== noteForm.htmlContent
  noteForm.htmlContent = value

  if (hydratingForm.value || meta?.external || !htmlChanged) {
    return
  }

  markDirty(true)
  scheduleLocalWorkspaceSnapshot()
}

async function loadNoteGraph() {
  if (!noteForm.id) {
    noteGraph.value = {
      nodes: [],
      links: []
    }
    return
  }

  noteGraphLoading.value = true

  try {
    noteGraph.value = await getGraphData(noteForm.id)
  } catch {
    noteGraph.value = {
      nodes: [],
      links: []
    }
  } finally {
    noteGraphLoading.value = false
  }
}

async function loadVersions() {
  if (!noteForm.id) {
    versions.value = []
    return
  }

  versions.value = await getNoteVersions(noteForm.id)
}

async function loadCollaborators() {
  if (!noteForm.id) {
    collaborators.value = []
    return
  }

  const data = await getCollaborators(noteForm.id)
  collaborators.value = data.map((member) => ({
    ...member,
    nickname: displayUserName(member)
  }))
}

async function loadComments() {
  if (!noteForm.id) {
    comments.value = []
    return
  }

  commentsLoading.value = true

  try {
    comments.value = await getNoteComments(noteForm.id)
  } catch (error) {
    comments.value = []
    ElMessage.error(error instanceof Error ? error.message : '加载评论失败')
  } finally {
    commentsLoading.value = false
  }
}

async function loadAttachments() {
  attachmentsLoading.value = true

  try {
    attachments.value = await getAttachments()
  } catch {
    attachments.value = []
  } finally {
    attachmentsLoading.value = false
  }
}

async function searchCollaboratorUsers(keyword: string) {
  const normalizedKeyword = keyword.trim()

  if (!normalizedKeyword) {
    collaboratorCandidates.value = []
    return
  }

  collaboratorSearchLoading.value = true

  try {
    collaboratorCandidates.value = await searchUsers(normalizedKeyword)
  } catch {
    collaboratorCandidates.value = []
  } finally {
    collaboratorSearchLoading.value = false
  }
}

function handleCollaboratorPickerVisibleChange(visible: boolean) {
  if (!visible) {
    collaboratorCandidates.value = []
  }
}

async function ensureWorkspaceTabForRoute() {
  if (routeNoteId.value) {
    const noteSummary = workspaceStore.notes.find((item) => item.id === routeNoteId.value)
    return noteWorkspaceStore.openNoteTab(routeNoteId.value, noteSummary?.title)
  }

  if (!routeDraftKey.value) {
    const latestDraftTab = noteWorkspaceStore.latestDraftTab

    if (latestDraftTab) {
      skipNextRouteSnapshot.value = true
      await router.replace(buildDraftNoteRoute(latestDraftTab.key))
      return null
    }

    skipNextRouteSnapshot.value = true
    await router.replace(buildDraftNoteRoute())
    return null
  }

  return noteWorkspaceStore.openDraftTab(routeDraftKey.value)
}

async function createInitialDraftSnapshot() {
  if (!routeTemplateId.value) {
    return createDefaultSnapshot()
  }

  try {
    const template = await getTemplateById(routeTemplateId.value)
    ElMessage.success(`已套用模板「${template.name}」`)
    return createSnapshotFromTemplate(template)
  } catch (error) {
    ElMessage.warning(error instanceof Error ? error.message : '模板加载失败，已打开空白草稿')
    return createDefaultSnapshot()
  }
}

async function loadPage() {
  loading.value = true
  disconnectRealtime()
  restoredDraftNoticeVisible.value = false

  try {
    if (!workspaceStore.notes.length || !workspaceStore.folders.length) {
      await workspaceStore.loadExplorer()
    }

    if (!attachments.value.length) {
      void loadAttachments()
    }

    const workspaceTab = await ensureWorkspaceTabForRoute()

    if (!workspaceTab) {
      return
    }

    if (workspaceTab.snapshot) {
      let snapshotToApply = workspaceTab.snapshot
      let shouldCheckRestoreNotice = true

      if (routeNoteId.value && workspaceTab.snapshot.dirty) {
        try {
          const persistedNote = await getNoteById(routeNoteId.value)

          if (isSnapshotSameAsPersistedNote(workspaceTab.snapshot, persistedNote)) {
            snapshotToApply = createSnapshotFromNote(persistedNote)
            noteWorkspaceStore.saveSnapshot(workspaceTab.key, snapshotToApply)
            shouldCheckRestoreNotice = false
          }
        } catch {
          // Keep the local snapshot if the server note cannot be compared.
        }
      }

      applySnapshot(snapshotToApply)
      syncActiveTabMeta()
      if (shouldCheckRestoreNotice) {
        maybeShowRestoredDraftNotice(workspaceTab)
      }

      if (noteForm.id) {
        await Promise.all([loadVersions(), loadCollaborators(), loadNoteGraph(), loadComments()])
        connectRealtime()
      } else {
        versions.value = []
        collaborators.value = []
        comments.value = []
        noteGraph.value = {
          nodes: [],
          links: []
        }
      }

      return
    }

    if (!routeNoteId.value) {
      applySnapshot(await createInitialDraftSnapshot())
      comments.value = []
      noteGraph.value = {
        nodes: [],
        links: []
      }
      persistActiveTabSnapshot()
      return
    }

    const note = await getNoteById(routeNoteId.value)
    applyNote(note)
    persistActiveTabSnapshot()
    noteWorkspaceStore.syncNote(note, captureSnapshot())
    await Promise.all([loadVersions(), loadCollaborators(), loadNoteGraph(), loadComments()])
    connectRealtime()
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '加载笔记失败')

    if (routeNoteId.value) {
      noteWorkspaceStore.removeNoteTab(routeNoteId.value)
    }

    await router.push('/folder')
  } finally {
    loading.value = false
  }
}

async function navigateToWorkspaceTab(key: string) {
  const targetTab = noteWorkspaceStore.tabs.find((tab) => tab.key === key)

  if (!targetTab) {
    return
  }

  if (targetTab.routeKind === 'note' && targetTab.noteId) {
    await router.push(buildNoteEditRoute(targetTab.noteId))
    return
  }

  await router.push(buildDraftNoteRoute(targetTab.key))
}

function maybeShowRestoredDraftNotice(workspaceTab: NoteWorkspaceTab) {
  const shouldShow =
    Boolean(workspaceTab.snapshot?.dirty) &&
    noteWorkspaceStore.shouldShowRestoreNotice(workspaceTab.key)

  if (!shouldShow) {
    return
  }

  restoredDraftNoticeVisible.value = true
  noteWorkspaceStore.consumeRestoreNotice(workspaceTab.key)
}

function dismissRestoredDraftNotice() {
  restoredDraftNoticeVisible.value = false
}

async function navigateAfterClosingActiveTab(nextKey: string | null) {
  if (!nextKey) {
    await router.push('/folder')
    return
  }

  await navigateToWorkspaceTab(nextKey)
}

function handleWorkspaceTabClick(tabContext: { paneName?: string | number }) {
  const key = String(tabContext.paneName ?? '')

  if (!key) {
    return
  }

  void navigateToWorkspaceTab(key)
}

async function handleWorkspaceTabRemove(tabKey: string | number) {
  const key = String(tabKey)
  const targetTab = noteWorkspaceStore.tabs.find((tab) => tab.key === key)

  if (!targetTab) {
    return
  }

  if (key === noteWorkspaceStore.activeKey) {
    persistActiveTabSnapshot()
  }

  const shouldWarn =
    key === noteWorkspaceStore.activeKey ? dirty.value : Boolean(targetTab.dirty || targetTab.snapshot?.dirty)

  if (shouldWarn) {
    try {
      await ElMessageBox.confirm(
        '这个标签里还有未保存内容。关闭只会退出你本机的工作区标签，不会删除笔记；关闭前会尽量把当前协作草稿同步给仍在线的协作者。',
        '关闭工作标签',
        {
          type: 'warning',
          confirmButtonText: '关闭标签',
          cancelButtonText: '继续编辑'
        }
      )
    } catch {
      return
    }
  }

  if (key !== noteWorkspaceStore.activeKey) {
    noteWorkspaceStore.closeTab(key)
    return
  }

  if (dirty.value) {
    persistActiveTabSnapshot()
    publishEdit({
      force: true,
      silent: true
    })
  }

  const nextKey = noteWorkspaceStore.closeTab(key)
  skipNextRouteSnapshot.value = true
  await navigateAfterClosingActiveTab(nextKey)
}

async function openQuickSelectedNote() {
  if (!quickOpenNoteId.value) {
    ElMessage.warning('请先选择要打开的笔记')
    return
  }

  const targetNoteId = quickOpenNoteId.value
  quickOpenNoteId.value = null
  await router.push(buildNoteEditRoute(targetNoteId))
}

function openNewDraftTab() {
  void router.push(buildDraftNoteRoute())
}

function toggleActiveWorkspacePin() {
  const activeKey = noteWorkspaceStore.activeKey

  if (!activeKey) {
    return
  }

  const pinned = noteWorkspaceStore.togglePinTab(activeKey)
  ElMessage.success(pinned ? '当前标签已固定' : '当前标签已取消固定')
}

function closeSavedWorkspaceTabs() {
  const activeKey = noteWorkspaceStore.activeKey
  const closableCount = workspaceClosableSavedCount.value

  if (!activeKey || !closableCount) {
    ElMessage.info('当前没有可关闭的已保存标签')
    return
  }

  noteWorkspaceStore.closeCleanTabs(activeKey)
  ElMessage.success(`已关闭 ${closableCount} 个已保存标签`)
}

async function closeWorkspaceTabsToRight() {
  const activeKey = noteWorkspaceStore.activeKey

  if (!activeKey || !workspaceRightClosableCount.value) {
    ElMessage.info('当前右侧没有可关闭的标签')
    return
  }

  const dirtyRightCount = workspaceTabsToRight.value.filter((tab) => tab.dirty || tab.snapshot?.dirty).length

  if (dirtyRightCount) {
    try {
      await ElMessageBox.confirm(
        `右侧还有 ${dirtyRightCount} 个标签包含未保存内容，关闭后只会丢弃你本机的工作区草稿，确定继续吗？`,
        '关闭右侧标签',
        {
          type: 'warning',
          confirmButtonText: '继续关闭',
          cancelButtonText: '先不处理'
        }
      )
    } catch {
      return
    }
  }

  const closedCount = noteWorkspaceStore.closeTabsToRight(activeKey)
  ElMessage.success(`已关闭右侧 ${closedCount} 个标签`)
}

async function closeOtherWorkspaceTabs() {
  const activeKey = noteWorkspaceStore.activeKey

  if (!activeKey) {
    return
  }

  const otherTabs = workspaceTabs.value.filter((tab) => tab.key !== activeKey && !tab.pinned)

  if (!otherTabs.length) {
    ElMessage.info('当前没有可关闭的其他标签')
    return
  }

  if (workspaceOtherDirtyCount.value) {
    try {
      await ElMessageBox.confirm(
        `还有 ${workspaceOtherDirtyCount.value} 个其他标签包含未保存内容，继续后会直接关闭这些标签，确定继续吗？`,
        '仅保留当前标签',
        {
          type: 'warning',
          confirmButtonText: '继续关闭',
          cancelButtonText: '先不处理'
        }
      )
    } catch {
      return
    }
  }

  noteWorkspaceStore.closeOtherTabs(activeKey)
  const closedCount = otherTabs.length
  ElMessage.success(`已保留当前标签，并关闭 ${closedCount} 个其他标签`)
}

function openExplorer() {
  void router.push('/folder')
}

function openRelatedNote(noteId: number) {
  void router.push(buildNoteEditRoute(noteId))
}

function jumpToOutlineItem(item: OutlineItem) {
  const scrolled = editorRef.value?.scrollToHeading?.(item.text)

  if (!scrolled) {
    editorRef.value?.focus()
  }
}

watch(
  () => route.fullPath,
  (_nextPath, previousPath) => {
    if (previousPath && !skipNextRouteSnapshot.value) {
      persistActiveTabSnapshot()
    }

    skipNextRouteSnapshot.value = false
    void loadPage()
  },
  {
    immediate: true
  }
)

watch(
  [() => noteForm.title, dirty],
  () => {
    syncActiveTabMeta()
  }
)

watch(
  [() => noteForm.title, () => noteForm.tagsText, () => noteForm.folderId],
  (_nextValue, previousValue) => {
    if (!previousValue || hydratingForm.value) {
      return
    }

    markDirty(true)
    scheduleLocalWorkspaceSnapshot()
  }
)

onMounted(() => {
  window.addEventListener('beforeunload', flushWorkspaceBeforeLeaving)
  window.addEventListener('pagehide', flushWorkspaceBeforeLeaving)
  document.addEventListener('visibilitychange', handleDocumentVisibilityChange)
})

onBeforeUnmount(() => {
  flushWorkspaceBeforeLeaving()
  window.removeEventListener('beforeunload', flushWorkspaceBeforeLeaving)
  window.removeEventListener('pagehide', flushWorkspaceBeforeLeaving)
  document.removeEventListener('visibilitychange', handleDocumentVisibilityChange)
  disconnectRealtime()
})

async function ensurePersistedNote() {
  if (noteForm.id) {
    return true
  }

  await saveNote()
  return Boolean(noteForm.id)
}

async function saveNote() {
  if (!noteForm.title.trim()) {
    ElMessage.warning('请先填写笔记标题')
    return
  }

  saving.value = true

  try {
    const payload = {
      title: noteForm.title.trim(),
      content: normalizeAttachmentLinks(noteForm.content),
      htmlContent: normalizeAttachmentLinks(noteForm.htmlContent),
      tags: parsedTags.value,
      folderId: noteForm.folderId,
      attachmentIds: referencedAttachmentIds.value
    }

    if (!noteForm.id) {
      const created = await createNote(payload)
      applyNote(created)
      workspaceStore.syncNote(created)
      await Promise.all([workspaceStore.loadNotes(), workspaceStore.loadTrashNotes()])

      if (noteWorkspaceStore.activeKey) {
        noteWorkspaceStore.promoteDraftTab(noteWorkspaceStore.activeKey, created, captureSnapshot())
      }

      skipNextRouteSnapshot.value = true
      await router.replace(buildNoteEditRoute(created.id))
      void loadAttachments()
      ElMessage.success('笔记已创建')
      return
    }

    const updated = await updateNote(noteForm.id, payload)
    applyNote(updated)
    workspaceStore.syncNote(updated)
    await Promise.all([workspaceStore.loadNotes(), loadVersions(), loadCollaborators(), loadAttachments()])
    persistActiveTabSnapshot()
    noteWorkspaceStore.syncNote(updated, captureSnapshot())

    if (!stompClient.value?.active) {
      connectRealtime()
    }

    ElMessage.success('笔记已保存')
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '保存失败，请稍后重试')
  } finally {
    saving.value = false
  }
}

async function saveCurrentAsTemplate() {
  const normalizedTitle = noteForm.title.trim()
  const normalizedContent = noteForm.content.trim()

  if (!normalizedTitle && !normalizedContent) {
    ElMessage.warning('请先写一点内容，再保存为模板')
    return
  }

  const templateName = normalizedTitle
    ? normalizedTitle.endsWith('模板')
      ? normalizedTitle
      : `${normalizedTitle} 模板`
    : '未命名模板'
  const payload: NoteTemplateRequest = {
    name: templateName,
    description: noteForm.id ? `从笔记「${normalizedTitle || noteForm.id}」保存` : '从当前草稿保存',
    content: noteForm.content,
    htmlContent: noteForm.htmlContent,
    tags: parsedTags.value,
    category: noteForm.dailyNote ? '每日笔记' : '通用'
  }

  templateSaving.value = true

  try {
    await workspaceStore.createTemplate(payload)
    ElMessage.success('已保存为模板，可在模板中心复用')
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '保存模板失败')
  } finally {
    templateSaving.value = false
  }
}

async function deleteCurrentNote() {
  if (!noteForm.id) {
    const nextKey = noteWorkspaceStore.closeTab(noteWorkspaceStore.activeKey || '')
    skipNextRouteSnapshot.value = true
    await navigateAfterClosingActiveTab(nextKey)
    return
  }

  try {
    await ElMessageBox.confirm(
      '笔记会先移动到回收站，30 天内可以恢复。是否继续？',
      '移入回收站',
      {
        type: 'warning',
        confirmButtonText: '移入回收站',
        cancelButtonText: '取消'
      }
    )

    await workspaceStore.removeNote(noteForm.id)
    disconnectRealtime()
    const nextKey = noteWorkspaceStore.removeNoteTab(noteForm.id)
    ElMessage.success('笔记已移入回收站')
    skipNextRouteSnapshot.value = true
    await navigateAfterClosingActiveTab(nextKey)
  } catch (error) {
    if (error instanceof Error) {
      ElMessage.error(error.message)
    }
  }
}

async function toggleFavoriteCurrentNote() {
  if (!noteForm.id) {
    ElMessage.warning('请先保存草稿，再进行收藏')
    return
  }

  favoriteSubmitting.value = true

  try {
    const updated = noteForm.favorited
      ? await workspaceStore.unfavoriteNote(noteForm.id)
      : await workspaceStore.favoriteNote(noteForm.id)

    mergeFavoriteState(updated)
    noteWorkspaceStore.syncNote(updated, captureSnapshot())
    ElMessage.success(updated.favorited ? '已加入收藏' : '已取消收藏')
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '更新收藏状态失败')
  } finally {
    favoriteSubmitting.value = false
  }
}

function triggerBrowserDownload(blob: Blob, fileName: string) {
  const downloadUrl = window.URL.createObjectURL(blob)
  const link = document.createElement('a')

  link.href = downloadUrl
  link.download = fileName
  document.body.appendChild(link)
  link.click()
  document.body.removeChild(link)
  window.URL.revokeObjectURL(downloadUrl)
}

async function exportCurrentNote(format: ExportFormat) {
  const ready = await ensurePersistedNote()

  if (!ready || !noteForm.id) {
    return
  }

  const formatLabelMap: Record<ExportFormat, string> = {
    MARKDOWN: 'Markdown',
    WORD: 'Word',
    PDF: 'PDF'
  }

  const extensionMap: Record<ExportFormat, string> = {
    MARKDOWN: 'md',
    WORD: 'doc',
    PDF: 'pdf'
  }

  try {
    exportingFormat.value = format
    const result = await exportNote(noteForm.id, format)
    const fileName =
      result.fileName || `${(noteForm.title || 'knowledgepulse-note').trim() || 'knowledgepulse-note'}.${extensionMap[format]}`

    triggerBrowserDownload(result.blob, fileName)
    ElMessage.success(`${formatLabelMap[format]} 导出成功`)
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '导出失败，请稍后重试')
  } finally {
    exportingFormat.value = null
  }
}

function triggerAttachmentUpload() {
  attachmentInputRef.value?.click()
}

function insertAttachmentReference(attachment: AttachmentItem) {
  const markdown = attachmentMarkdown(attachment)
  const currentContent = noteForm.content.trimEnd()
  const nextContent = currentContent ? `${currentContent}\n\n${markdown}\n` : `${markdown}\n`

  noteForm.content = nextContent
  editorRef.value?.setValue(nextContent)
  editorRef.value?.focus()
  markDirty(true)
  scheduleLocalWorkspaceSnapshot()
  ElMessage.success('已插入附件引用，保存笔记后会同步使用状态')
}

async function handleAttachmentFileChange(event: Event) {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]

  if (!file) {
    return
  }

  try {
    if (!isSupportedAttachmentFile(file)) {
      ElMessage.warning('仅支持图片、PDF、Word .doc 或 .docx 文件')
      return
    }

    if (file.size > MAX_ATTACHMENT_BYTES) {
      ElMessage.warning('单个附件请控制在 25MB 以内')
      return
    }

    attachmentUploading.value = true
    const uploaded = await uploadAttachment(file)
    attachments.value = [uploaded, ...attachments.value.filter((item) => item.id !== uploaded.id)]
    insertAttachmentReference(uploaded)
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '上传附件失败，请稍后重试')
  } finally {
    attachmentUploading.value = false
    input.value = ''
  }
}

async function copyAttachmentReference(attachment: AttachmentItem) {
  try {
    await navigator.clipboard.writeText(attachmentMarkdown(attachment))
    ElMessage.success('附件引用已复制')
  } catch {
    ElMessage.warning('复制失败，请手动复制引用')
  }
}

function openAttachmentCenter() {
  void router.push('/attachments')
}

async function createShare() {
  if (noteForm.id && !canManageShare.value) {
    ElMessage.warning('当前账号没有这篇笔记的分享管理权限')
    return
  }

  const ready = await ensurePersistedNote()

  if (!ready || !noteForm.id) {
    return
  }

  if (!canManageShare.value) {
    ElMessage.warning('当前账号没有这篇笔记的分享管理权限')
    return
  }

  const requestedShareMode = shareForm.isPublic === 0 && !shareLink.value ? 1 : shareForm.isPublic

  if (requestedShareMode === 0) {
    await closeShare()
    return
  }

  try {
    const urlPath = await generateShareLink(noteForm.id, {
      isPublic: requestedShareMode,
      password: shareForm.password || undefined
    })

    shareLink.value = buildPublicAppUrl(urlPath)
    shareForm.isPublic = requestedShareMode
    noteForm.isPublic = requestedShareMode
    persistActiveTabSnapshot()
    ElMessage.success('分享链接已生成')
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '生成分享链接失败')
  }
}

async function copyShareLink() {
  if (!shareLink.value) {
    ElMessage.warning('请先生成分享链接')
    return
  }

  try {
    await navigator.clipboard.writeText(shareLink.value)
    ElMessage.success('分享链接已复制')
  } catch {
    ElMessage.warning('复制失败，请手动复制链接')
  }
}

async function closeShare() {
  if (noteForm.id && !canManageShare.value) {
    ElMessage.warning('当前账号没有这篇笔记的分享管理权限')
    return
  }

  if (!noteForm.id) {
    shareForm.isPublic = 0
    shareForm.password = ''
    shareLink.value = ''
    persistActiveTabSnapshot()
    return
  }

  try {
    await revokeShare(noteForm.id)
    shareLink.value = ''
    shareForm.password = ''
    shareForm.isPublic = 0
    noteForm.isPublic = 0
    persistActiveTabSnapshot()
    ElMessage.success('分享已关闭')
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '关闭分享失败')
  }
}

async function addCollaboratorAction() {
  if (!collaboratorForm.userId) {
    ElMessage.warning('请先搜索并选择要添加的协作者')
    return
  }

  const ready = await ensurePersistedNote()

  if (!ready || !noteForm.id) {
    return
  }

  try {
    await addCollaborator(noteForm.id, {
      userId: collaboratorForm.userId,
      permission: collaboratorForm.permission
    })

    collaboratorForm.userId = null
    collaboratorForm.permission = 'EDIT'
    collaboratorCandidates.value = []
    await loadCollaborators()
    ElMessage.success('协作者已添加')
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '添加协作者失败')
  }
}

async function updatePermission(member: Collaborator, permission: string) {
  if (!noteForm.id) {
    return
  }

  try {
    await updateCollaboratorPermission(noteForm.id, member.userId, permission)
    await loadCollaborators()
    ElMessage.success('协作者权限已更新')
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '更新协作者权限失败')
  }
}

async function removeCollaboratorAction(userId: number) {
  if (!noteForm.id) {
    return
  }

  try {
    await removeCollaborator(noteForm.id, userId)
    await loadCollaborators()
    ElMessage.success('协作者已移除')
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '移除协作者失败')
  }
}

async function submitComment() {
  const content = commentForm.content.trim()

  if (!noteForm.id) {
    ElMessage.warning('请先保存为笔记，再写评论')
    return
  }

  if (!content) {
    ElMessage.warning('先写一点评论内容吧')
    return
  }

  commentSubmitting.value = true

  try {
    const created = await createNoteComment(noteForm.id, {
      content
    })
    comments.value = [...comments.value, created]
    commentForm.content = ''
    ElMessage.success('评论已发送，相关成员会收到提醒')
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '发送评论失败')
  } finally {
    commentSubmitting.value = false
  }
}

async function deleteCommentAction(comment: NoteComment) {
  if (!noteForm.id) {
    return
  }

  try {
    await ElMessageBox.confirm('确定删除这条评论吗？删除后不会再显示在协作记录里。', '删除评论', {
      type: 'warning',
      confirmButtonText: '删除',
      cancelButtonText: '取消'
    })

    await deleteNoteComment(noteForm.id, comment.id)
    comments.value = comments.value.filter((item) => item.id !== comment.id)
    ElMessage.success('评论已删除')
  } catch (error) {
    if (error instanceof Error) {
      ElMessage.error(error.message)
    }
  }
}

async function restoreVersionAction(version: number) {
  if (!noteForm.id) {
    return
  }

  try {
    const restored = await restoreNoteVersion(noteForm.id, version)
    applyNote(restored)
    workspaceStore.syncNote(restored)
    persistActiveTabSnapshot()
    noteWorkspaceStore.syncNote(restored, captureSnapshot())
    await loadVersions()
    ElMessage.success(`已恢复到版本 ${version}`)
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '恢复版本失败')
  }
}

async function addRelationAction() {
  if (!noteForm.id || !relationForm.targetNoteId) {
    ElMessage.warning('请选择要关联的目标笔记')
    return
  }

  try {
    await addRelation({
      sourceNoteId: noteForm.id,
      targetNoteId: relationForm.targetNoteId,
      relationType: relationForm.relationType
    })

    relationForm.targetNoteId = null
    await loadNoteGraph()
    ElMessage.success('笔记关联已添加')
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '添加关联失败')
  }
}

function appendTag(tag: string) {
  const nextTags = new Set([...parsedTags.value, tag])
  noteForm.tagsText = [...nextTags].join(', ')
}

function jumpToGraph() {
  if (!noteForm.id) {
    ElMessage.warning('请先保存笔记，再查看图谱')
    return
  }

  void router.push({
    path: '/graph',
    query: {
      noteId: noteForm.id
    }
  })
}

function handleFolderSelection(folderId: number | null) {
  noteForm.folderId = folderId
}

function handleFolderRename(payload: { id: number; name: string; parentId: number | null }) {
  void workspaceStore.renameFolder(payload.id, {
    name: payload.name,
    parentId: payload.parentId
  })
}

function handlePermissionChange(member: Collaborator, permission: string | number | boolean) {
  void updatePermission(member, String(permission))
}
</script>

<template>
  <div class="note-editor page-shell">
    <PageHero
      kicker="Editor"
      :title="pageTitle"
      :description="pageDescription"
    >
      <template #actions>
        <el-button type="primary" :loading="saving" @click="saveNote">保存笔记</el-button>
        <el-button plain :loading="templateSaving" @click="saveCurrentAsTemplate">保存为模板</el-button>
        <el-button v-if="noteForm.id" plain :loading="favoriteSubmitting" @click="toggleFavoriteCurrentNote">
          {{ noteForm.favorited ? '取消收藏' : '收藏笔记' }}
        </el-button>
        <el-button plain @click="openExplorer">返回文件与笔记</el-button>
        <el-button plain @click="deleteCurrentNote">{{ noteForm.id ? '移入回收站' : '关闭草稿标签' }}</el-button>
      </template>
    </PageHero>

    <div class="note-editor__mobile-actions panel">
      <div class="note-editor__mobile-actions-copy">
        <span>{{ dirty ? '未保存' : '已同步' }}</span>
        <strong>{{ pageTitle }}</strong>
      </div>

      <div class="note-editor__mobile-actions-buttons">
        <el-button type="primary" size="small" :loading="saving" @click="saveNote">保存</el-button>
        <el-button v-if="noteForm.id" plain size="small" :loading="favoriteSubmitting" @click="toggleFavoriteCurrentNote">
          {{ noteForm.favorited ? '取消收藏' : '收藏' }}
        </el-button>
        <el-button plain size="small" @click="openExplorer">返回</el-button>
      </div>
    </div>

    <section v-if="noteForm.dailyNoteDate" class="note-editor__daily-banner">
      <div class="note-editor__daily-banner-copy">
        <span class="section-kicker">Daily Note</span>
        <strong>{{ dailyNoteBannerTitle }}</strong>
        <p>{{ dailyNoteBannerDescription }}</p>
      </div>

      <div class="note-editor__daily-banner-actions">
        <span class="note-editor__daily-banner-pill">按日期归档</span>
        <span class="note-editor__daily-banner-pill note-editor__daily-banner-pill--soft">
          {{ noteForm.dailyNoteDate }}
        </span>
      </div>
    </section>

    <section v-if="restoredDraftNoticeVisible" class="note-editor__restore-banner">
      <div class="note-editor__restore-banner-copy">
        <span class="section-kicker">Workspace Recovery</span>
        <strong>已恢复上次未保存的内容</strong>
        <p>
          “{{ restoredDraftNoticeTitle }}” 已从本地工作区中恢复回来。你可以继续编辑，确认无误后点击保存即可同步到笔记。
        </p>
      </div>

      <div class="note-editor__restore-banner-actions">
        <span class="note-editor__restore-banner-pill">刷新后自动找回</span>
        <el-button plain @click="dismissRestoredDraftNotice">我知道了</el-button>
      </div>
    </section>

    <section class="panel note-editor__workspace">
      <div class="note-editor__workspace-head">
        <div class="note-editor__workspace-copy">
          <span class="section-kicker">Workspace Tabs</span>
          <strong>当前已打开 {{ workspaceTabs.length }} 个工作区标签</strong>
          <small>切换标签时会自动保留上下文，适合同时对照多篇笔记。</small>
        </div>

        <div class="note-editor__workspace-actions">
          <div class="note-editor__quick-open">
            <NoteSearchSelect
              v-model="quickOpenNoteId"
              :notes="workspaceStore.notes"
              :exclude-note-id="noteForm.id"
              placeholder="搜索后快速打开另一篇笔记"
            />
          </div>
          <el-button class="note-editor__workspace-action" plain @click="openQuickSelectedNote">打开笔记</el-button>
          <el-button class="note-editor__workspace-action" plain @click="openNewDraftTab">新建草稿</el-button>
          <el-button class="note-editor__workspace-action note-editor__workspace-action--mobile-hidden" plain @click="toggleActiveWorkspacePin">
            {{ activeWorkspacePinned ? '取消固定' : '固定当前' }}
          </el-button>
          <el-button class="note-editor__workspace-action note-editor__workspace-action--mobile-hidden" plain @click="closeWorkspaceTabsToRight">关闭右侧</el-button>
          <el-button class="note-editor__workspace-action note-editor__workspace-action--mobile-hidden" plain @click="closeSavedWorkspaceTabs">关闭已保存</el-button>
          <el-button class="note-editor__workspace-action note-editor__workspace-action--mobile-hidden" plain @click="closeOtherWorkspaceTabs">仅留当前</el-button>
        </div>
      </div>

      <div class="note-editor__workspace-strip">
        <div class="note-editor__workspace-badges">
          <span class="note-editor__workspace-badge note-editor__workspace-badge--accent">{{ currentWorkspaceStateLabel }}</span>
          <span class="note-editor__workspace-badge">{{ currentWorkspaceLabel }}</span>
          <span class="note-editor__workspace-badge">{{ currentWorkspaceFolderLabel }}</span>
          <span class="note-editor__workspace-badge">权限 {{ currentPermissionLabel }}</span>
          <span class="note-editor__workspace-badge">未保存 {{ workspaceDirtyCount }}</span>
          <span v-if="workspacePinnedCount" class="note-editor__workspace-badge">已固定 {{ workspacePinnedCount }}</span>
          <span
            v-if="activeWorkspacePinned"
            class="note-editor__workspace-badge note-editor__workspace-badge--pin"
          >
            当前标签已固定
          </span>
          <span
            v-for="tag in workspacePreviewTags"
            :key="tag"
            class="note-editor__workspace-badge note-editor__workspace-badge--tag"
          >
            #{{ tag }}
          </span>
        </div>

        <div class="note-editor__workspace-summary">
          <article class="note-editor__workspace-stat">
            <span>打开中</span>
            <strong>{{ workspaceTabs.length }}</strong>
            <small>随时切换</small>
          </article>
          <article class="note-editor__workspace-stat">
            <span>未保存</span>
            <strong>{{ workspaceDirtyCount }}</strong>
            <small>关闭前提醒</small>
          </article>
          <article class="note-editor__workspace-stat">
            <span>已同步</span>
            <strong>{{ workspaceSavedCount }}</strong>
            <small>状态更清晰</small>
          </article>
          <article class="note-editor__workspace-stat">
            <span>右侧标签</span>
            <strong>{{ workspaceRightClosableCount }}</strong>
            <small>可一键清理</small>
          </article>
        </div>
      </div>

      <el-tabs
        v-if="workspaceTabs.length"
        :model-value="activeWorkspaceTabKey"
        type="card"
        class="note-editor__workspace-tabs"
        @tab-click="handleWorkspaceTabClick"
        @tab-remove="handleWorkspaceTabRemove"
      >
        <el-tab-pane
          v-for="tab in workspaceTabs"
          :key="tab.key"
          :name="tab.key"
          :closable="tab.closable"
        >
          <template #label>
            <span class="note-editor__tab-label">
              <span v-if="tab.dirty" class="note-editor__tab-dot" />
              <span class="note-editor__tab-name">{{ tab.title }}</span>
              <small class="note-editor__tab-meta">{{ tab.routeKind === 'draft' ? '草稿' : '笔记' }}</small>
              <small v-if="tab.pinned" class="note-editor__tab-meta note-editor__tab-meta--pin">固定</small>
            </span>
          </template>
        </el-tab-pane>
      </el-tabs>
    </section>

    <div class="note-editor__layout">
      <aside class="note-editor__aside">
        <FolderTreePanel
          :folders="workspaceStore.folders"
          :current-folder-id="noteForm.folderId"
          :loading="workspaceStore.explorerLoading"
          title="目录与归类"
          :initially-open="true"
          @select="handleFolderSelection"
          @create="workspaceStore.createFolder"
          @rename="handleFolderRename"
          @delete="workspaceStore.removeFolder"
        />

        <TagCloudPanel :tags="workspaceStore.tagBuckets" title="标签云" :initially-open="false" @select="appendTag" />
      </aside>

      <section class="note-editor__main panel">
        <el-skeleton :loading="loading" animated :rows="8">
          <template #default>
            <div class="note-editor__meta">
              <div class="note-editor__meta-main">
                <span class="section-kicker">Writing</span>
                <el-input
                  v-model="noteForm.title"
                  placeholder="输入标题，例如：Spring Security 权限设计"
                  size="large"
                  @focus="handleTitleEditing"
                  @input="handleTitleEditing"
                />
              </div>

              <div class="note-editor__status">
                <span class="pill">{{ realtimeLabel }}</span>
                <span class="pill">上次保存：{{ saveLabel }}</span>
                <span v-if="activeWorkspaceTab" class="pill">
                  {{ noteForm.id ? `当前笔记 #${noteForm.id}` : '当前是草稿标签' }}
                </span>
              </div>
            </div>

            <div class="note-editor__toolbar">
              <el-select v-model="noteForm.folderId" placeholder="选择所属文件夹" clearable>
                <el-option
                  v-for="item in workspaceStore.folderOptions"
                  :key="item.value"
                  :label="item.label"
                  :value="item.value"
                />
              </el-select>

              <el-input v-model="noteForm.tagsText" placeholder="标签，使用中英文逗号分隔" />

              <div class="note-editor__export-actions">
                <el-button plain :loading="exportingFormat === 'MARKDOWN'" @click="exportCurrentNote('MARKDOWN')">
                  导出 Markdown
                </el-button>
                <el-button plain :loading="exportingFormat === 'WORD'" @click="exportCurrentNote('WORD')">
                  导出 Word
                </el-button>
                <el-button plain :loading="exportingFormat === 'PDF'" @click="exportCurrentNote('PDF')">
                  导出 PDF
                </el-button>
              </div>
            </div>

            <section
              v-if="otherEditingUsers.length"
              class="note-editor__region-warning"
              :class="{ 'note-editor__region-warning--conflict': sameRegionEditors.length }"
            >
              <div>
                <span class="section-kicker">Editing Area</span>
                <strong>{{ editingRegionNoticeTitle }}</strong>
                <div class="note-editor__region-tags">
                  <span v-for="tag in editingRegionNoticeTags" :key="tag">{{ tag }}</span>
                </div>
              </div>
              <p>{{ editingRegionNoticeDescription }}</p>
            </section>

            <RichTextEditor
              ref="editorRef"
              :model-value="noteForm.content"
              :height="700"
              @update:model-value="handleEditorInput"
              @html-change="handleHtmlChange"
              @editing-region-change="handleEditingRegionChange"
            />

            <div class="note-editor__hint">
              <span v-if="lastSyncMessage">{{ lastSyncMessage }}</span>
              <span>实时协作状态每 800ms 推送一次增量更新，切换标签时会自动保留当前草稿。</span>
            </div>
          </template>
        </el-skeleton>
      </section>

      <aside class="note-editor__rail">
        <CollapsiblePanel
          class="rail-card"
          kicker="Outline"
          title="笔记大纲"
          body-class="rail-card__body"
          :initially-open="false"
        >
          <div v-if="outlineItems.length" class="rail-card__outline">
            <button
              v-for="item in outlineItems"
              :key="item.id"
              type="button"
              class="rail-card__outline-item"
              :style="{ '--outline-level': String(item.level) }"
              @click="jumpToOutlineItem(item)"
            >
              <span class="rail-card__outline-level">H{{ item.level }}</span>
              <strong>{{ item.text }}</strong>
            </button>
          </div>

          <div v-else class="empty-state">添加 `#`、`##`、`###` 标题后，这里会自动生成结构大纲。</div>
        </CollapsiblePanel>

        <CollapsiblePanel
          class="rail-card"
          kicker="Links"
          title="双向链接"
          body-class="rail-card__body"
          :initially-open="false"
        >
          <div v-if="noteGraphLoading" class="empty-state">正在整理这篇笔记的关联关系...</div>

          <div v-else-if="noteForm.id" class="rail-card__links">
            <div class="rail-card__link-group">
              <div class="rail-card__link-head">
                <strong>反向链接</strong>
                <span>{{ noteConnections.incoming.length }}</span>
              </div>

              <div v-if="noteConnections.incoming.length" class="rail-card__link-list">
                <button
                  v-for="item in noteConnections.incoming"
                  :key="`incoming-${item.noteId}-${item.relationType}`"
                  type="button"
                  class="rail-card__link-item"
                  @click="openRelatedNote(item.noteId)"
                >
                  <strong>{{ item.title }}</strong>
                  <span>{{ relatedNoteMeta(item) }}</span>
                </button>
              </div>

              <div v-else class="empty-state">目前还没有别的笔记指向这篇内容。</div>
            </div>

            <div class="rail-card__link-group">
              <div class="rail-card__link-head">
                <strong>出站链接</strong>
                <span>{{ noteConnections.outgoing.length }}</span>
              </div>

              <div v-if="noteConnections.outgoing.length" class="rail-card__link-list">
                <button
                  v-for="item in noteConnections.outgoing"
                  :key="`outgoing-${item.noteId}-${item.relationType}`"
                  type="button"
                  class="rail-card__link-item"
                  @click="openRelatedNote(item.noteId)"
                >
                  <strong>{{ item.title }}</strong>
                  <span>{{ relatedNoteMeta(item) }}</span>
                </button>
              </div>

              <div v-else class="empty-state">你还没有从这篇笔记发出关联，可在下方知识关联里继续补充。</div>
            </div>
          </div>

          <div v-else class="empty-state">先保存成笔记后，这里会显示类似 Obsidian 的双向链接关系。</div>
        </CollapsiblePanel>

        <CollapsiblePanel
          class="rail-card"
          kicker="Share"
          title="分享设置"
          body-class="rail-card__body"
          :initially-open="false"
        >
          <el-form label-position="top" class="rail-card__form">
            <el-form-item label="访问范围">
              <el-select v-model="shareForm.isPublic" :disabled="!canManageShare">
                <el-option
                  v-for="option in shareModeOptions"
                  :key="option.value"
                  :label="option.label"
                  :value="option.value"
                />
              </el-select>
            </el-form-item>

            <el-form-item label="访问密码（可选）">
              <el-input
                v-model="shareForm.password"
                type="password"
                show-password
                :disabled="!canManageShare"
                placeholder="如果需要额外口令，可在这里设置"
              />
            </el-form-item>
          </el-form>

          <div class="rail-card__modes">
            <div v-for="option in shareModeOptions" :key="option.value" class="rail-card__mode">
              <strong>{{ option.label }}</strong>
              <span>{{ option.description }}</span>
            </div>
          </div>

          <div class="rail-card__actions">
            <el-button type="primary" :disabled="!canManageShare" @click="createShare">
              {{ shareActionLabel }}
            </el-button>
            <el-button plain :disabled="!shareLink" @click="copyShareLink">复制链接</el-button>
            <el-button plain :disabled="!canManageShare" @click="closeShare">清空分享</el-button>
          </div>

          <p class="rail-card__note" :class="{ 'rail-card__note--warning': !canManageShare }">
            {{ sharePermissionSummary }}
          </p>
          <p class="rail-card__note">{{ shareLink || '还没有生成分享链接' }}</p>
        </CollapsiblePanel>

        <CollapsiblePanel
          class="rail-card"
          kicker="Attachments"
          :title="`附件引用 ${referencedAttachmentIds.length}/${attachmentCount}`"
          body-class="rail-card__body"
          :initially-open="false"
        >
          <input
            ref="attachmentInputRef"
            class="rail-card__file-input"
            type="file"
            accept="image/png,image/jpeg,image/webp,image/gif,application/pdf,application/msword,application/vnd.openxmlformats-officedocument.wordprocessingml.document,.png,.jpg,.jpeg,.webp,.gif,.pdf,.doc,.docx"
            @change="handleAttachmentFileChange"
          />

          <div class="rail-card__attachment-toolbar">
            <el-button type="primary" plain :loading="attachmentUploading" @click="triggerAttachmentUpload">
              上传并插入
            </el-button>
            <el-button plain :loading="attachmentsLoading" @click="loadAttachments">刷新</el-button>
            <el-button plain @click="openAttachmentCenter">附件中心</el-button>
          </div>

          <div class="rail-card__attachment-summary">
            <span>{{ referencedAttachmentIds.length }} 个已在当前笔记中引用</span>
            <span>{{ attachmentCount }} 个可用附件</span>
          </div>

          <div v-if="attachmentsLoading" class="empty-state">正在整理附件...</div>

          <div v-else-if="attachments.length" class="rail-card__attachments">
            <article
              v-for="attachment in attachments.slice(0, 8)"
              :key="attachment.id"
              class="rail-card__attachment"
              :class="{ 'rail-card__attachment--active': referencedAttachmentIds.includes(attachment.id) }"
            >
              <div
                class="rail-card__attachment-icon"
                :class="`rail-card__attachment-icon--${attachment.fileType.toLowerCase()}`"
              >
                {{ attachmentIcon(attachment.fileType) }}
              </div>

              <div class="rail-card__attachment-main">
                <strong>{{ attachment.originalName }}</strong>
                <span>
                  {{ attachmentTypeLabel(attachment.fileType) }} / {{ formatFileSize(attachment.fileSize) }} /
                  {{ attachment.used ? `${attachment.referenceCount} 处引用` : '未使用' }}
                </span>
              </div>

              <div class="rail-card__attachment-buttons">
                <el-button type="primary" plain size="small" @click="insertAttachmentReference(attachment)">插入</el-button>
                <el-button plain size="small" @click="copyAttachmentReference(attachment)">复制</el-button>
              </div>
            </article>
          </div>

          <div v-else class="empty-state">还没有附件。上传图片、PDF 或 Word 后，可一键插入到笔记。</div>

          <p class="rail-card__note">
            插入后会生成 Markdown 引用；保存笔记时系统会自动同步附件是否被使用，未使用附件可在附件中心统一清理。
          </p>
        </CollapsiblePanel>

        <CollapsiblePanel
          class="rail-card"
          kicker="Collaborators"
          title="协作者"
          body-class="rail-card__body"
          :initially-open="false"
        >
          <div v-if="ownerMember" class="rail-card__owner">
            <span class="rail-card__label">拥有者</span>
            <div class="rail-card__member rail-card__member--owner">
              <div class="rail-card__member-main">
                <el-avatar :size="38" :src="resolveAvatarSrc(ownerMember.avatar)">
                  {{ initials(displayUserName(ownerMember)) }}
                </el-avatar>
                <div class="rail-card__member-meta">
                  <strong>{{ displayUserName(ownerMember) }}</strong>
                  <span>{{ ownerSummary }}</span>
                </div>
              </div>

              <span class="pill">OWNER</span>
            </div>
          </div>

          <div class="rail-card__collaborators">
            <div v-if="!collaborators.length" class="empty-state">当前还没有额外协作者。</div>

            <div v-for="member in collaborators" :key="member.userId" class="rail-card__member">
              <div class="rail-card__member-main">
                <el-avatar :size="38" :src="resolveAvatarSrc(member.avatar)">
                  {{ initials(displayUserName(member)) }}
                </el-avatar>
                <div class="rail-card__member-meta">
                  <strong>{{ displayUserName(member) }}</strong>
                  <span>{{ collaboratorMeta(member) }}</span>
                </div>
              </div>

              <div class="rail-card__member-actions">
                <el-select
                  :model-value="member.permission"
                  size="small"
                  :disabled="!canManageCollaborators"
                  @change="handlePermissionChange(member, $event)"
                >
                  <el-option
                    v-for="permission in collaboratorPermissionOptions"
                    :key="permission"
                    :label="permission"
                    :value="permission"
                  />
                </el-select>
                <el-button plain size="small" :disabled="!canManageCollaborators" @click="removeCollaboratorAction(member.userId)">
                  移除
                </el-button>
              </div>
            </div>
          </div>

          <div class="rail-card__adder">
            <el-select
              v-model="collaboratorForm.userId"
              filterable
              remote
              clearable
              reserve-keyword
              placeholder="按昵称、用户名或邮箱搜索用户"
              :remote-method="searchCollaboratorUsers"
              :loading="collaboratorSearchLoading"
              :disabled="!canManageCollaborators"
              @visible-change="handleCollaboratorPickerVisibleChange"
            >
              <el-option
                v-for="user in availableCollaboratorCandidates"
                :key="user.id"
                :label="collaboratorOptionLabel(user)"
                :value="user.id"
              >
                <div class="rail-card__option">
                  <strong>{{ collaboratorOptionLabel(user) }}</strong>
                  <span>{{ collaboratorOptionMeta(user) }}</span>
                </div>
              </el-option>
            </el-select>

            <el-select v-model="collaboratorForm.permission" :disabled="!canManageCollaborators">
              <el-option
                v-for="permission in collaboratorPermissionOptions"
                :key="permission"
                :label="permission"
                :value="permission"
              />
            </el-select>
            <el-button type="primary" plain :disabled="!canManageCollaborators" @click="addCollaboratorAction">
              添加协作者
            </el-button>
          </div>

          <p class="rail-card__note">{{ collaboratorHelpText }}</p>
        </CollapsiblePanel>

        <CollapsiblePanel
          class="rail-card"
          kicker="Comments"
          :title="`评论协作 ${commentCount}`"
          body-class="rail-card__body"
          :initially-open="false"
        >
          <div v-if="!noteForm.id" class="empty-state">
            保存成笔记后即可留下评论，评论会同步提醒拥有者和协作者。
          </div>

          <template v-else>
            <div class="rail-card__comments">
              <div v-if="commentsLoading" class="empty-state">正在加载评论...</div>

              <article v-for="comment in comments" :key="comment.id" class="rail-card__comment">
                <div class="rail-card__comment-head">
                  <div class="rail-card__comment-author">
                    <el-avatar :size="34" :src="resolveAvatarSrc(comment.avatar)">
                      {{ initials(commentAuthorName(comment)) }}
                    </el-avatar>
                    <div>
                      <strong>{{ commentAuthorName(comment) }}</strong>
                      <span>{{ formatDateTime(comment.createTime) }}</span>
                    </div>
                  </div>

                  <el-button v-if="comment.canDelete" plain size="small" @click="deleteCommentAction(comment)">
                    删除
                  </el-button>
                </div>

                <p>{{ comment.content }}</p>
              </article>

              <div v-if="!comments.length && !commentsLoading" class="empty-state">
                还没有评论。可以用它记录反馈、待确认问题或协作结论。
              </div>
            </div>

            <div class="rail-card__comment-box">
              <el-input
                v-model="commentForm.content"
                type="textarea"
                :rows="4"
                maxlength="1000"
                show-word-limit
                placeholder="写下问题、补充说明或协作反馈..."
              />
              <el-button type="primary" :loading="commentSubmitting" @click="submitComment">发送评论</el-button>
            </div>
          </template>
        </CollapsiblePanel>

        <CollapsiblePanel
          class="rail-card"
          kicker="Versions"
          title="版本历史"
          body-class="rail-card__body"
          :initially-open="false"
        >
          <div v-if="versions.length" class="rail-card__versions">
            <article v-for="version in versions" :key="version.id" class="rail-card__version">
              <div>
                <strong>版本 {{ version.version }}</strong>
                <small>{{ formatDateTime(version.createTime) }}</small>
              </div>
              <p>{{ version.contentSnapshot.slice(0, 72) || '这个版本还没有可预览内容。' }}</p>
              <div class="rail-card__version-actions">
                <el-button type="primary" plain size="small" @click="openVersionDiff(version)">
                  查看差异
                </el-button>
                <el-button plain size="small" @click="restoreVersionAction(version.version)">恢复此版本</el-button>
              </div>
            </article>
          </div>

          <div v-else class="empty-state">保存后会自动生成版本快照。</div>
        </CollapsiblePanel>

        <CollapsiblePanel
          class="rail-card note-editor__relation-card"
          kicker="Graph"
          title="知识关联"
          body-class="rail-card__body note-editor__relation-card-body"
          :initially-open="false"
        >
          <div class="note-editor__relation-search">
            <NoteSearchSelect
              v-model="relationForm.targetNoteId"
              :notes="workspaceStore.notes"
              :exclude-note-id="noteForm.id"
              placeholder="搜索目标笔记"
            />
          </div>

          <el-form label-position="top" class="rail-card__form">
            <el-form-item label="关联目标">
              <el-select v-model="relationForm.targetNoteId" placeholder="选择目标笔记">
                <el-option
                  v-for="item in noteOptions"
                  :key="item.value"
                  :label="item.label"
                  :value="item.value"
                />
              </el-select>
            </el-form-item>

            <el-form-item label="关系类型">
              <el-select v-model="relationForm.relationType">
                <el-option label="引用" value="引用" />
                <el-option label="扩展" value="扩展" />
                <el-option label="相关" value="相关" />
              </el-select>
            </el-form-item>
          </el-form>

          <div class="rail-card__actions">
            <el-button type="primary" @click="addRelationAction">添加关联</el-button>
            <el-button plain @click="jumpToGraph">查看图谱</el-button>
          </div>
        </CollapsiblePanel>
      </aside>
    </div>

    <CollaboratorStrip :collaborators="collaboratorRoster" />

    <el-dialog
      v-model="versionDiffDialogVisible"
      class="version-diff-dialog"
      width="min(1120px, 94vw)"
      destroy-on-close
    >
      <template #header>
        <div class="version-diff__header">
          <div>
            <span class="section-kicker">Version Diff</span>
            <h3>
              {{ selectedDiffVersion ? `版本 ${selectedDiffVersion.version} 差异对比` : '版本差异对比' }}
            </h3>
          </div>

          <el-segmented
            v-model="versionDiffMode"
            :options="[
              { label: '与当前内容', value: 'current' },
              { label: '与上一版本', value: 'previous', disabled: !selectedDiffPreviousVersion }
            ]"
          />
        </div>
      </template>

      <section class="version-diff">
        <div class="version-diff__summary">
          <article class="version-diff__stat version-diff__stat--add">
            <span>新增行</span>
            <strong>+{{ versionDiffSummary.added }}</strong>
          </article>
          <article class="version-diff__stat version-diff__stat--remove">
            <span>删除行</span>
            <strong>-{{ versionDiffSummary.removed }}</strong>
          </article>
          <article class="version-diff__stat">
            <span>未变更</span>
            <strong>{{ versionDiffSummary.unchanged }}</strong>
          </article>
        </div>

        <div class="version-diff__compare">
          <div>
            <span>旧内容</span>
            <strong>{{ versionDiffOldLabel }}</strong>
          </div>
          <div>
            <span>新内容</span>
            <strong>{{ versionDiffNewLabel }}</strong>
          </div>
        </div>

        <div v-if="versionDiffRows.length" class="version-diff__body">
          <div
            v-for="line in versionDiffRows"
            :key="line.id"
            class="version-diff__line"
            :class="`version-diff__line--${line.type}`"
          >
            <span class="version-diff__mark">
              {{ line.type === 'add' ? '+' : line.type === 'remove' ? '-' : ' ' }}
            </span>
            <span class="version-diff__line-no">{{ line.oldLineNumber ?? '' }}</span>
            <span class="version-diff__line-no">{{ line.newLineNumber ?? '' }}</span>
            <code>{{ formatDiffLineText(line.text) }}</code>
          </div>
        </div>

        <div v-else class="version-diff__empty">
          <strong>没有可对比的内容</strong>
          <span>这个版本和对比目标都为空，或当前没有选中版本。</span>
        </div>
      </section>
    </el-dialog>
  </div>
</template>

<style scoped>
.note-editor__mobile-actions {
  display: none;
}

.note-editor__daily-banner {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 12px 14px;
  border: 1px solid rgba(54, 92, 75, 0.18);
  border-radius: 18px;
  background:
    radial-gradient(circle at top left, rgba(54, 92, 75, 0.14), transparent 34%),
    linear-gradient(180deg, rgba(246, 252, 249, 0.96), rgba(239, 248, 243, 0.9));
  box-shadow: 0 14px 30px rgba(54, 92, 75, 0.08);
}

.note-editor__daily-banner-copy {
  display: grid;
  gap: 5px;
}

.note-editor__daily-banner-copy strong {
  font-size: 1.08rem;
  color: #365c4b;
}

.note-editor__daily-banner-copy p {
  margin: 0;
  color: var(--text-soft);
  line-height: 1.5;
}

.note-editor__daily-banner-actions {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  justify-content: flex-end;
  gap: 8px;
}

.note-editor__daily-banner-pill {
  display: inline-flex;
  align-items: center;
  min-height: 30px;
  padding: 0 11px;
  border-radius: 999px;
  background: rgba(54, 92, 75, 0.14);
  color: #365c4b;
  font-size: 0.84rem;
  border: 1px solid rgba(54, 92, 75, 0.18);
}

.note-editor__daily-banner-pill--soft {
  background: rgba(255, 255, 255, 0.74);
}

.note-editor__restore-banner {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 12px 14px;
  border: 1px solid rgba(197, 157, 88, 0.28);
  border-radius: 18px;
  background:
    radial-gradient(circle at top right, rgba(197, 157, 88, 0.18), transparent 34%),
    linear-gradient(180deg, rgba(255, 251, 242, 0.96), rgba(255, 246, 228, 0.88));
  box-shadow: 0 14px 30px rgba(141, 106, 34, 0.08);
}

.note-editor__restore-banner-copy {
  display: grid;
  gap: 5px;
}

.note-editor__restore-banner-copy strong {
  font-size: 1.08rem;
  color: #7a5720;
}

.note-editor__restore-banner-copy p {
  margin: 0;
  color: var(--text-soft);
  line-height: 1.5;
}

.note-editor__restore-banner-actions {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  justify-content: flex-end;
  gap: 8px;
}

.note-editor__restore-banner-pill {
  display: inline-flex;
  align-items: center;
  min-height: 30px;
  padding: 0 11px;
  border-radius: 999px;
  background: rgba(197, 157, 88, 0.18);
  color: #8d6a22;
  font-size: 0.84rem;
  border: 1px solid rgba(197, 157, 88, 0.24);
}

.note-editor__workspace {
  position: relative;
  display: grid;
  gap: 14px;
  padding: 16px;
  overflow: hidden;
  border: 1px solid rgba(184, 92, 56, 0.14);
  background:
    radial-gradient(circle at top left, rgba(197, 157, 88, 0.18), transparent 32%),
    radial-gradient(circle at top right, rgba(54, 92, 75, 0.14), transparent 26%),
    linear-gradient(180deg, rgba(255, 252, 247, 0.94), rgba(255, 247, 239, 0.9));
  box-shadow: 0 16px 36px rgba(141, 69, 41, 0.08);
}

.note-editor__workspace::before {
  content: '';
  position: absolute;
  inset: 0;
  background: linear-gradient(135deg, rgba(255, 255, 255, 0.22), transparent 40%);
  pointer-events: none;
}

.note-editor__workspace > * {
  position: relative;
  z-index: 1;
}

.note-editor__workspace-head {
  display: grid;
  gap: 12px;
  grid-template-columns: minmax(0, 1.35fr) minmax(280px, 0.95fr);
  align-items: start;
}

.note-editor__workspace-copy {
  display: grid;
  gap: 5px;
}

.note-editor__workspace-copy strong {
  font-size: 1.16rem;
  line-height: 1.35;
}

.note-editor__workspace-copy small {
  color: var(--text-soft);
  line-height: 1.5;
}

.note-editor__workspace-summary {
  display: grid;
  gap: 8px;
  grid-template-columns: repeat(4, minmax(0, 1fr));
}

.note-editor__workspace-stat {
  display: grid;
  gap: 4px;
  padding: 10px 12px;
  border: 1px solid rgba(184, 92, 56, 0.12);
  border-radius: 14px;
  background: rgba(255, 255, 255, 0.72);
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.42);
}

.note-editor__workspace-stat span,
.note-editor__workspace-stat small {
  color: var(--text-soft);
}

.note-editor__workspace-stat strong {
  font-size: 1.18rem;
  line-height: 1;
  color: var(--accent-strong);
}

.note-editor__workspace-strip {
  display: grid;
  gap: 10px;
  padding: 12px 14px;
  border-radius: 16px;
  background: rgba(255, 255, 255, 0.58);
  border: 1px solid rgba(184, 92, 56, 0.12);
}

.note-editor__workspace-badges {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.note-editor__workspace-badge {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 6px 9px;
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.82);
  border: 1px solid rgba(184, 92, 56, 0.12);
  color: var(--text-main);
  font-size: 0.84rem;
  line-height: 1;
}

.note-editor__workspace-badge--accent {
  background: rgba(184, 92, 56, 0.1);
  border-color: rgba(184, 92, 56, 0.2);
  color: var(--accent-strong);
}

.note-editor__workspace-badge--tag {
  background: rgba(54, 92, 75, 0.08);
  border-color: rgba(54, 92, 75, 0.16);
  color: #365c4b;
}

.note-editor__workspace-badge--pin {
  background: rgba(197, 157, 88, 0.18);
  border-color: rgba(197, 157, 88, 0.32);
  color: #8d6a22;
}

.note-editor__workspace-actions {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.note-editor__quick-open {
  min-width: min(460px, 100%);
  flex: 1 1 340px;
}

.note-editor__workspace-tabs {
  --el-fill-color-light: transparent;
}

.note-editor__workspace-tabs :deep(.el-tabs__header) {
  margin-bottom: 0;
}

.note-editor__workspace-tabs :deep(.el-tabs__nav-wrap) {
  padding-bottom: 6px;
}

.note-editor__workspace-tabs :deep(.el-tabs__nav) {
  border: 0;
  gap: 6px;
}

.note-editor__workspace-tabs :deep(.el-tabs__item) {
  height: auto;
  max-width: 260px;
  padding: 8px 10px;
  border: 1px solid rgba(184, 92, 56, 0.1);
  border-radius: 14px;
  background: rgba(255, 255, 255, 0.76);
  color: var(--text-main);
  transition: transform 0.18s ease, box-shadow 0.18s ease, border-color 0.18s ease, background-color 0.18s ease;
}

.note-editor__workspace-tabs :deep(.el-tabs__item:hover) {
  transform: translateY(-1px);
  border-color: rgba(141, 69, 41, 0.28);
  box-shadow: 0 10px 22px rgba(141, 69, 41, 0.08);
}

.note-editor__workspace-tabs :deep(.el-tabs__item.is-active) {
  border-color: rgba(141, 69, 41, 0.34);
  background: linear-gradient(180deg, rgba(255, 255, 255, 0.98), rgba(255, 249, 242, 0.96));
  box-shadow: 0 12px 24px rgba(141, 69, 41, 0.1);
}

.note-editor__workspace-tabs :deep(.el-tabs__nav-next),
.note-editor__workspace-tabs :deep(.el-tabs__nav-prev) {
  line-height: 34px;
}

.note-editor__tab-label {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  max-width: 100%;
}

.note-editor__tab-name {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  max-width: 140px;
}

.note-editor__tab-meta {
  padding: 2px 6px;
  border-radius: 999px;
  background: rgba(184, 92, 56, 0.08);
  color: var(--text-soft);
  font-size: 0.72rem;
  line-height: 1;
}

.note-editor__tab-meta--pin {
  background: rgba(197, 157, 88, 0.18);
  color: #8d6a22;
}

.note-editor__tab-dot {
  width: 8px;
  height: 8px;
  border-radius: 999px;
  background: var(--accent);
  flex: 0 0 auto;
  box-shadow: 0 0 0 4px rgba(184, 92, 56, 0.12);
}

.note-editor__layout {
  display: grid;
  gap: 16px;
  grid-template-columns: 280px minmax(0, 1fr) 320px;
  align-items: start;
}

.note-editor__aside,
.note-editor__rail {
  display: grid;
  align-content: start;
  gap: 16px;
}

.note-editor__main {
  padding: 16px;
}

.note-editor__meta {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 12px;
}

.note-editor__meta-main {
  display: grid;
  gap: 8px;
  width: 100%;
}

.note-editor__status {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  justify-content: flex-end;
}

.note-editor__toolbar {
  display: grid;
  gap: 10px;
  grid-template-columns: 230px minmax(0, 1fr);
  margin-bottom: 12px;
}

.note-editor__export-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  grid-column: 1 / -1;
}

.note-editor__region-warning {
  position: sticky;
  top: 12px;
  z-index: 9;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 14px;
  padding: 14px 16px;
  border: 1px solid rgba(184, 92, 56, 0.22);
  border-radius: 20px;
  background:
    radial-gradient(circle at top left, rgba(184, 92, 56, 0.14), transparent 36%),
    linear-gradient(135deg, rgba(255, 249, 239, 0.96), rgba(255, 244, 230, 0.86));
  box-shadow: 0 12px 28px rgba(141, 69, 41, 0.08);
}

.note-editor__region-warning--conflict {
  border-color: rgba(184, 76, 59, 0.34);
  background:
    radial-gradient(circle at top left, rgba(184, 76, 59, 0.18), transparent 36%),
    linear-gradient(135deg, rgba(255, 247, 239, 0.98), rgba(255, 236, 226, 0.92));
  box-shadow: 0 16px 34px rgba(184, 76, 59, 0.13);
}

.note-editor__region-warning > div {
  display: grid;
  gap: 6px;
}

.note-editor__region-warning strong {
  color: #8d4529;
}

.note-editor__region-warning p {
  max-width: 460px;
  margin: 0;
  color: var(--text-soft);
  line-height: 1.65;
}

.note-editor__region-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.note-editor__region-tags span {
  display: inline-flex;
  align-items: center;
  min-height: 26px;
  padding: 0 10px;
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.72);
  color: var(--text-soft);
  font-size: 0.78rem;
}

.note-editor__hint {
  display: flex;
  justify-content: space-between;
  gap: 16px;
  margin-top: 14px;
  color: var(--text-soft);
}

.rail-card {
  display: grid;
  gap: 10px;
  padding: 16px;
}

.rail-card__body {
  display: grid;
  gap: 10px;
}

.rail-card__actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.rail-card__note {
  margin: 0;
  word-break: break-all;
  color: var(--text-soft);
  line-height: 1.5;
}

.rail-card__note--warning {
  padding: 12px 14px;
  border: 1px solid rgba(184, 92, 56, 0.18);
  border-radius: 16px;
  background: rgba(184, 92, 56, 0.08);
  color: var(--accent-strong);
  word-break: break-word;
}

.rail-card__modes,
.rail-card__collaborators,
.rail-card__versions,
.rail-card__links,
.rail-card__attachments,
.rail-card__comments,
.rail-card__outline {
  display: grid;
  gap: 8px;
}

.rail-card__owner {
  display: grid;
  gap: 10px;
}

.rail-card__label {
  color: var(--text-soft);
  font-size: 0.82rem;
  letter-spacing: 0.08em;
  text-transform: uppercase;
}

.rail-card__mode,
.rail-card__attachment,
.rail-card__member,
.rail-card__version,
.rail-card__comment,
.rail-card__link-group {
  display: grid;
  gap: 6px;
  padding: 12px;
  border: 1px solid rgba(184, 92, 56, 0.14);
  border-radius: 14px;
  background: rgba(255, 255, 255, 0.54);
}

.rail-card__mode span,
.rail-card__attachment-main span,
.rail-card__version small,
.rail-card__version p,
.rail-card__comment-author span {
  color: var(--text-soft);
}

.rail-card__file-input {
  display: none;
}

.rail-card__attachment-toolbar {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}

.rail-card__attachment-summary {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px;
}

.rail-card__attachment-summary span {
  display: inline-flex;
  align-items: center;
  min-height: 34px;
  padding: 0 12px;
  border: 1px solid rgba(54, 92, 75, 0.12);
  border-radius: 999px;
  background: rgba(54, 92, 75, 0.07);
  color: #365c4b;
  font-size: 0.82rem;
}

.rail-card__attachment {
  grid-template-columns: 42px minmax(0, 1fr);
  align-items: center;
  background:
    radial-gradient(circle at top right, rgba(54, 92, 75, 0.08), transparent 42%),
    rgba(255, 255, 255, 0.58);
}

.rail-card__attachment--active {
  border-color: rgba(54, 92, 75, 0.3);
  background:
    radial-gradient(circle at top right, rgba(54, 92, 75, 0.14), transparent 44%),
    rgba(247, 253, 249, 0.86);
}

.rail-card__attachment-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 42px;
  height: 42px;
  border-radius: 16px;
  background: rgba(184, 92, 56, 0.1);
  color: var(--accent-strong);
  font-size: 0.84rem;
  font-weight: 800;
}

.rail-card__attachment-icon--image {
  background: rgba(54, 92, 75, 0.12);
  color: #365c4b;
}

.rail-card__attachment-icon--pdf {
  background: rgba(184, 76, 59, 0.12);
  color: #b84c3b;
}

.rail-card__attachment-icon--word {
  background: rgba(55, 95, 151, 0.12);
  color: #315a94;
}

.rail-card__attachment-main {
  display: grid;
  gap: 4px;
  min-width: 0;
}

.rail-card__attachment-main strong {
  overflow: hidden;
  color: var(--text-main);
  text-overflow: ellipsis;
  white-space: nowrap;
}

.rail-card__attachment-main span {
  font-size: 0.8rem;
  line-height: 1.5;
}

.rail-card__attachment-buttons {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  grid-column: 1 / -1;
}

.rail-card__member-main {
  display: flex;
  align-items: center;
  gap: 12px;
}

.rail-card__member-meta {
  display: grid;
  gap: 4px;
  min-width: 0;
}

.rail-card__member-meta span {
  color: var(--text-soft);
  font-size: 0.84rem;
  line-height: 1.5;
  word-break: break-word;
}

.rail-card__member--owner {
  gap: 14px;
}

.rail-card__outline-item,
.rail-card__link-item {
  display: grid;
  gap: 6px;
  width: 100%;
  padding: 14px 16px;
  border: 1px solid rgba(184, 92, 56, 0.12);
  border-radius: 16px;
  background: rgba(255, 255, 255, 0.7);
  text-align: left;
  cursor: pointer;
  transition: transform 0.18s ease, border-color 0.18s ease, box-shadow 0.18s ease;
}

.rail-card__outline-item:hover,
.rail-card__link-item:hover {
  transform: translateY(-1px);
  border-color: rgba(141, 69, 41, 0.28);
  box-shadow: 0 10px 20px rgba(141, 69, 41, 0.08);
}

.rail-card__outline-item {
  padding-left: calc(16px + (var(--outline-level, 1) - 1) * 14px);
}

.rail-card__outline-item strong,
.rail-card__link-item strong {
  color: var(--text-main);
}

.rail-card__outline-level,
.rail-card__link-item span,
.rail-card__link-head span {
  color: var(--text-soft);
  font-size: 0.82rem;
}

.rail-card__link-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.rail-card__link-list {
  display: grid;
  gap: 10px;
}

.rail-card__comment-head,
.rail-card__comment-author {
  display: flex;
  align-items: center;
  gap: 12px;
}

.rail-card__comment-head {
  justify-content: space-between;
}

.rail-card__comment-author > div {
  display: grid;
  gap: 3px;
  min-width: 0;
}

.rail-card__comment-author strong {
  color: var(--text-main);
}

.rail-card__comment-author span {
  font-size: 0.78rem;
}

.rail-card__comment p {
  margin: 0;
  color: var(--text-main);
  line-height: 1.75;
  white-space: pre-wrap;
  word-break: break-word;
}

.rail-card__comment-box {
  display: grid;
  gap: 10px;
  padding: 14px;
  border: 1px solid rgba(54, 92, 75, 0.12);
  border-radius: 20px;
  background:
    radial-gradient(circle at top right, rgba(54, 92, 75, 0.08), transparent 42%),
    rgba(255, 255, 255, 0.6);
}

.rail-card__comment-box .el-button {
  justify-self: end;
}

.rail-card__version p {
  margin: 0;
  line-height: 1.8;
}

.rail-card__version-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.rail-card__member-actions {
  display: grid;
  gap: 10px;
  grid-template-columns: 1fr auto;
}

.rail-card__adder {
  display: grid;
  gap: 10px;
  grid-template-columns: minmax(0, 1fr) 120px;
}

.rail-card__adder .el-button {
  grid-column: 1 / -1;
}

.rail-card__option {
  display: grid;
  gap: 4px;
}

.rail-card__option span {
  color: var(--text-soft);
  font-size: 0.82rem;
}

.note-editor__relation-card-body .rail-card__form :deep(.el-form-item:first-child) {
  display: none;
}

.version-diff-dialog :deep(.el-dialog) {
  border-radius: 28px;
  overflow: hidden;
}

.version-diff-dialog :deep(.el-dialog__header) {
  padding: 0;
  margin: 0;
}

.version-diff-dialog :deep(.el-dialog__body) {
  padding: 0;
}

.version-diff__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 18px;
  padding: 22px 24px;
  border-bottom: 1px solid rgba(54, 92, 75, 0.12);
  background:
    radial-gradient(circle at top left, rgba(54, 92, 75, 0.14), transparent 28%),
    linear-gradient(135deg, rgba(248, 253, 249, 0.98), rgba(255, 250, 241, 0.94));
}

.version-diff__header h3 {
  margin: 6px 0 0;
  color: #243026;
  font-size: 1.22rem;
}

.version-diff {
  display: grid;
  gap: 16px;
  padding: 22px 24px 24px;
  background: linear-gradient(180deg, #fffdfa, #f6faf3);
}

.version-diff__summary {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;
}

.version-diff__stat {
  display: grid;
  gap: 4px;
  padding: 14px 16px;
  border: 1px solid rgba(93, 113, 92, 0.12);
  border-radius: 18px;
  background: rgba(255, 255, 255, 0.72);
}

.version-diff__stat span {
  color: var(--text-soft);
  font-size: 0.82rem;
}

.version-diff__stat strong {
  color: #273329;
  font-size: 1.4rem;
}

.version-diff__stat--add strong {
  color: #2e7d4f;
}

.version-diff__stat--remove strong {
  color: #b84c3b;
}

.version-diff__compare {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
}

.version-diff__compare > div {
  display: grid;
  gap: 4px;
  padding: 12px 14px;
  border: 1px solid rgba(184, 92, 56, 0.1);
  border-radius: 16px;
  background: rgba(255, 255, 255, 0.7);
}

.version-diff__compare span {
  color: var(--text-soft);
  font-size: 0.8rem;
}

.version-diff__compare strong {
  color: var(--text-main);
}

.version-diff__body {
  max-height: min(62vh, 680px);
  overflow: auto;
  border: 1px solid rgba(54, 92, 75, 0.14);
  border-radius: 20px;
  background: #fcfbf6;
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.7);
}

.version-diff__line {
  display: grid;
  grid-template-columns: 34px 52px 52px minmax(0, 1fr);
  min-height: 34px;
  border-bottom: 1px solid rgba(93, 113, 92, 0.08);
  font-family: 'Cascadia Code', 'JetBrains Mono', Consolas, monospace;
  font-size: 0.84rem;
}

.version-diff__line:last-child {
  border-bottom: 0;
}

.version-diff__mark,
.version-diff__line-no {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  color: rgba(74, 88, 78, 0.56);
  background: rgba(255, 255, 255, 0.54);
}

.version-diff__line code {
  display: block;
  overflow-x: auto;
  padding: 8px 12px;
  color: #344039;
  white-space: pre;
}

.version-diff__line--add {
  background: rgba(57, 137, 88, 0.11);
}

.version-diff__line--add .version-diff__mark,
.version-diff__line--add .version-diff__line-no {
  color: #2e7d4f;
  background: rgba(57, 137, 88, 0.13);
}

.version-diff__line--add code {
  color: #1f6f43;
}

.version-diff__line--remove {
  background: rgba(207, 86, 70, 0.11);
}

.version-diff__line--remove .version-diff__mark,
.version-diff__line--remove .version-diff__line-no {
  color: #b84c3b;
  background: rgba(207, 86, 70, 0.13);
}

.version-diff__line--remove code {
  color: #9a3f32;
  text-decoration: line-through;
  text-decoration-thickness: 1px;
  text-decoration-color: rgba(154, 63, 50, 0.48);
}

.version-diff__empty {
  display: grid;
  gap: 8px;
  justify-items: center;
  padding: 42px 20px;
  border: 1px dashed rgba(54, 92, 75, 0.2);
  border-radius: 20px;
  color: var(--text-soft);
  background: rgba(255, 255, 255, 0.56);
}

.version-diff__empty strong {
  color: var(--text-main);
}

@media (max-width: 1480px) {
  .note-editor__layout {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 1080px) {
  .note-editor__daily-banner,
  .note-editor__restore-banner {
    flex-direction: column;
    align-items: flex-start;
  }

  .note-editor__daily-banner-actions,
  .note-editor__restore-banner-actions {
    width: 100%;
    justify-content: flex-start;
  }

  .note-editor__workspace-head {
    grid-template-columns: 1fr;
  }

  .note-editor__workspace-summary {
    grid-template-columns: 1fr;
  }

  .note-editor__meta,
  .note-editor__region-warning,
  .note-editor__hint {
    flex-direction: column;
    align-items: flex-start;
  }

  .note-editor__workspace-actions {
    width: 100%;
  }

  .note-editor__quick-open {
    min-width: 100%;
  }
}

@media (max-width: 900px) {
  .note-editor__main {
    order: 1;
  }

  .note-editor__aside {
    order: 2;
  }

  .note-editor__rail {
    order: 3;
  }

  .note-editor__workspace {
    padding: 18px;
  }

  .note-editor__toolbar,
  .rail-card__member-actions,
  .rail-card__adder {
    grid-template-columns: 1fr;
  }

  .rail-card__comment-head {
    align-items: flex-start;
    flex-direction: column;
  }

  .note-editor__tab-label {
    max-width: 220px;
  }

  .note-editor__tab-meta {
    display: none;
  }

  .version-diff__header,
  .version-diff__compare,
  .version-diff__summary {
    grid-template-columns: 1fr;
  }

  .version-diff__header {
    align-items: flex-start;
    flex-direction: column;
  }

  .version-diff {
    padding: 18px;
  }

  .version-diff__line {
    grid-template-columns: 28px 42px 42px minmax(0, 1fr);
    font-size: 0.78rem;
  }
}

@media (max-width: 640px) {
  .note-editor {
    gap: 14px;
  }

  .note-editor :deep(.page-hero) {
    display: none;
  }

  .note-editor__mobile-actions {
    position: sticky;
    top: 72px;
    z-index: 16;
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 10px;
    padding: 10px;
    border-radius: 18px;
  }

  .note-editor__mobile-actions-copy {
    display: grid;
    min-width: 0;
    gap: 2px;
  }

  .note-editor__mobile-actions-copy span {
    color: var(--accent-strong);
    font-size: 0.72rem;
    font-weight: 800;
  }

  .note-editor__mobile-actions-copy strong {
    overflow: hidden;
    max-width: 38vw;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  .note-editor__mobile-actions-buttons {
    display: flex;
    flex: 0 0 auto;
    gap: 6px;
  }

  .note-editor__daily-banner,
  .note-editor__restore-banner,
  .note-editor__workspace,
  .note-editor__main,
  .rail-card {
    padding: 16px;
  }

  .note-editor__daily-banner-actions,
  .note-editor__restore-banner-actions,
  .note-editor__workspace-actions,
  .note-editor__export-actions,
  .rail-card__actions,
  .rail-card__attachment-buttons,
  .rail-card__version-actions {
    align-items: stretch;
  }

  .note-editor__daily-banner-actions :deep(.el-button),
  .note-editor__restore-banner-actions :deep(.el-button),
  .note-editor__workspace-actions :deep(.el-button),
  .note-editor__export-actions :deep(.el-button),
  .rail-card__actions :deep(.el-button),
  .rail-card__attachment-buttons :deep(.el-button),
  .rail-card__version-actions :deep(.el-button) {
    flex: 1 1 128px;
    min-width: 0;
  }

  .note-editor__workspace-head,
  .note-editor__workspace-strip,
  .note-editor__layout,
  .note-editor__aside,
  .note-editor__rail {
    gap: 16px;
  }

  .note-editor__workspace-copy,
  .note-editor__workspace-strip,
  .note-editor__workspace-action--mobile-hidden,
  .note-editor__export-actions {
    display: none;
  }

  .note-editor__workspace-actions {
    display: grid;
    grid-template-columns: 1fr repeat(2, auto);
    gap: 8px;
  }

  .note-editor__quick-open {
    min-width: 0;
  }

  .note-editor__workspace-action {
    min-width: 0;
  }

  .note-editor__workspace-summary {
    gap: 10px;
  }

  .note-editor__workspace-stat {
    padding: 12px;
  }

  .note-editor__workspace-tabs :deep(.el-tabs__nav-wrap) {
    overflow-x: auto;
  }

  .note-editor__workspace-tabs :deep(.el-tabs__item) {
    max-width: 230px;
    padding: 10px 12px;
    border-radius: 16px;
  }

  .note-editor__tab-name {
    max-width: 150px;
  }

  .note-editor__meta {
    gap: 12px;
    margin-bottom: 14px;
  }

  .note-editor__status {
    justify-content: flex-start;
  }

  .note-editor__toolbar {
    gap: 12px;
    margin-bottom: 14px;
  }

  .note-editor__region-warning {
    top: 8px;
    padding: 12px;
    border-radius: 16px;
  }

  .note-editor__hint {
    gap: 8px;
    font-size: 0.84rem;
  }

  .rail-card__attachment-summary {
    grid-template-columns: 1fr;
  }

  .rail-card__member-actions,
  .rail-card__adder {
    gap: 8px;
  }

  .version-diff__body {
    max-height: 58dvh;
  }
}

@media (max-width: 420px) {
  .note-editor__daily-banner,
  .note-editor__restore-banner,
  .note-editor__workspace,
  .note-editor__main,
  .rail-card {
    padding: 14px;
  }

  .note-editor__mobile-actions {
    padding: 8px;
  }

  .note-editor__mobile-actions-copy strong {
    max-width: 30vw;
    font-size: 0.9rem;
  }

  .note-editor__mobile-actions-buttons {
    gap: 4px;
  }

  .note-editor__workspace-copy strong {
    font-size: 1rem;
  }

  .note-editor__workspace-badges {
    gap: 8px;
  }

  .note-editor__workspace-badge {
    max-width: 100%;
    line-height: 1.25;
    white-space: normal;
  }

  .note-editor__tab-label {
    max-width: 180px;
  }

  .note-editor__tab-name {
    max-width: 124px;
  }

  .version-diff {
    padding: 14px;
  }

  .version-diff__line {
    grid-template-columns: 24px 34px 34px minmax(0, 1fr);
    font-size: 0.72rem;
  }

  .version-diff__line code {
    padding: 8px;
  }
}
</style>
