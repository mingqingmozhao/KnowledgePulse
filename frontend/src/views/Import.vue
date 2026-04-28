<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { useRouter } from 'vue-router'
import PageHero from '@/components/PageHero.vue'
import { uploadAttachment } from '@/api/attachment'
import { importDocumentsAsNotes, importMarkdownFiles, type ImportableFile } from '@/api/import'
import { useWorkspaceStore } from '@/stores/workspace'
import type { AttachmentItem, ImportMode, ImportResponse } from '@/types'
import {
  MAX_ATTACHMENT_BYTES,
  attachmentIcon,
  attachmentTypeLabel,
  formatFileSize,
  isSupportedAttachmentFile
} from '@/utils/attachment'

type ImportCenterMode = ImportMode | 'ATTACHMENTS'

type ModeCard = {
  value: ImportCenterMode
  title: string
  kicker: string
  description: string
  buttonText: string
}

const router = useRouter()
const workspaceStore = useWorkspaceStore()

const directoryInputRef = ref<HTMLInputElement | null>(null)
const batchInputRef = ref<HTMLInputElement | null>(null)
const attachmentInputRef = ref<HTMLInputElement | null>(null)
const mode = ref<ImportCenterMode>('OBSIDIAN_VAULT')
const selectedFiles = ref<ImportableFile[]>([])
const rootFolderName = ref('Obsidian 导入')
const targetFolderId = ref<number | null>(null)
const importing = ref(false)
const result = ref<ImportResponse | null>(null)
const uploadedAttachments = ref<AttachmentItem[]>([])
const attachmentWarnings = ref<string[]>([])
const extractDocuments = ref(false)
const keepOriginalDocuments = ref(true)
const showAdvancedSettings = ref(false)

const modeCards: ModeCard[] = [
  {
    value: 'OBSIDIAN_VAULT',
    kicker: 'Vault',
    title: 'Obsidian Vault 导入',
    description: '保留 vault 里的目录层级，自动读取 front matter 和正文里的 #标签。',
    buttonText: '选择 Vault 文件夹'
  },
  {
    value: 'MARKDOWN_FOLDER',
    kicker: 'Folder',
    title: 'Markdown 文件夹导入',
    description: '适合从本地资料夹、课程笔记或项目文档批量搬进知识库。',
    buttonText: '选择 Markdown 文件夹'
  },
  {
    value: 'BATCH_MARKDOWN',
    kicker: 'Batch',
    title: '批量 Markdown 文件',
    description: '不需要目录结构时，直接多选 .md 或 .markdown 文件快速导入。',
    buttonText: '选择多个文件'
  },
  {
    value: 'ATTACHMENTS',
    kicker: 'Assets',
    title: '附件批量导入',
    description: '图片、PDF、Word 统一上传到附件中心，之后可在笔记里插入引用。',
    buttonText: '选择附件文件'
  }
]

const activeModeCard = computed(() => modeCards.find((item) => item.value === mode.value) ?? modeCards[0])
const markdownFiles = computed(() => selectedFiles.value.filter((file) => isMarkdownPath(relativePathOf(file))))
const supportedAttachmentFiles = computed(() =>
  selectedFiles.value.filter((file) => !isMarkdownPath(relativePathOf(file)) && isSupportedAttachmentFile(file))
)
const oversizedAttachmentFiles = computed(() =>
  supportedAttachmentFiles.value.filter((file) => file.size > MAX_ATTACHMENT_BYTES)
)
const attachmentFiles = computed(() =>
  supportedAttachmentFiles.value.filter((file) => file.size <= MAX_ATTACHMENT_BYTES)
)
const documentFiles = computed(() =>
  attachmentFiles.value.filter((file) => ['PDF', 'WORD'].includes(attachmentFileType(file)))
)
const extractedDocumentFiles = computed(() => (extractDocuments.value ? documentFiles.value : []))
const directAttachmentFiles = computed(() =>
  extractDocuments.value
    ? attachmentFiles.value.filter((file) => attachmentFileType(file) === 'IMAGE')
    : attachmentFiles.value
)
const unsupportedClientCount = computed(
  () => selectedFiles.value.length - markdownFiles.value.length - supportedAttachmentFiles.value.length
)
const skippedClientCount = computed(
  () => unsupportedClientCount.value + oversizedAttachmentFiles.value.length
)
const totalSize = computed(() =>
  [...markdownFiles.value, ...attachmentFiles.value].reduce((sum, file) => sum + file.size, 0)
)
const commonTopDirectory = computed(() => detectCommonTopDirectory(markdownFiles.value))
const previewGroups = computed(() => {
  const groups = new Map<string, number>()

  markdownFiles.value.forEach((file) => {
    const segments = logicalSegments(relativePathOf(file))
    segments.pop()
    const groupName = segments[0] || '导入根目录'
    groups.set(groupName, (groups.get(groupName) ?? 0) + 1)
  })

  return [...groups.entries()]
    .map(([name, count]) => ({ name, count }))
    .sort((left, right) => right.count - left.count || left.name.localeCompare(right.name))
    .slice(0, 8)
})
const previewFiles = computed(() => markdownFiles.value.slice(0, 9))
const previewAttachments = computed(() => attachmentFiles.value.slice(0, 9))
const canImport = computed(() => {
  if (importing.value) {
    return false
  }

  if (markdownFiles.value.length > 0 && rootFolderName.value.trim().length === 0) {
    return false
  }

  if (extractedDocumentFiles.value.length > 0 && rootFolderName.value.trim().length === 0) {
    return false
  }

  return markdownFiles.value.length > 0 || attachmentFiles.value.length > 0
})
const hasImportResult = computed(
  () => Boolean(result.value) || uploadedAttachments.value.length > 0 || attachmentWarnings.value.length > 0
)
const resultWarnings = computed(() => [
  ...(result.value?.warnings ?? []),
  ...attachmentWarnings.value
])
const importButtonText = computed(() => {
  const parts = [
    markdownFiles.value.length + extractedDocumentFiles.value.length
      ? `${markdownFiles.value.length + extractedDocumentFiles.value.length} 篇笔记`
      : '',
    directAttachmentFiles.value.length || (extractedDocumentFiles.value.length && keepOriginalDocuments.value)
      ? `${directAttachmentFiles.value.length + (keepOriginalDocuments.value ? extractedDocumentFiles.value.length : 0)} 个附件`
      : ''
  ].filter(Boolean)

  return parts.length ? `导入 ${parts.join(' + ')}` : '开始导入'
})
const resultSummaryText = computed(() => {
  const parts = [
    result.value ? `已导入 ${result.value.importedNotes} 篇笔记` : '',
    result.value ? `创建 ${result.value.createdFolders} 个文件夹` : '',
    uploadedAttachments.value.length ? `上传 ${uploadedAttachments.value.length} 个附件` : '',
    result.value?.skippedFiles ? `${result.value.skippedFiles} 个 Markdown 文件被忽略` : ''
  ].filter(Boolean)

  return parts.length ? `${parts.join('，')}。` : '导入任务已结束。'
})
const pageDescription = computed(() => {
  if (!markdownFiles.value.length && !attachmentFiles.value.length) {
    return '把 Markdown、Obsidian vault、图片、PDF 和 Word 从一个入口导入：笔记归档到文件夹，资料进入附件中心。'
  }

  if (markdownFiles.value.length && attachmentFiles.value.length) {
    if (extractedDocumentFiles.value.length) {
      return `已准备 ${markdownFiles.value.length} 篇 Markdown、${extractedDocumentFiles.value.length} 个待抽取文档和 ${directAttachmentFiles.value.length} 个普通附件。`
    }
    return `已准备 ${markdownFiles.value.length} 篇 Markdown 和 ${attachmentFiles.value.length} 个附件，导入后会分别进入笔记库和附件中心。`
  }

  if (markdownFiles.value.length) {
    return `已准备 ${markdownFiles.value.length} 个 Markdown 文件，导入后会放入「${rootFolderName.value || '新的导入文件夹'}」。`
  }

  if (extractedDocumentFiles.value.length) {
    return `已准备 ${extractedDocumentFiles.value.length} 个 PDF/Word 文档，导入后会抽取为可编辑笔记。`
  }

  return `已准备 ${attachmentFiles.value.length} 个附件，上传后可在附件中心统一管理并插入笔记。`
})

onMounted(() => {
  if (!workspaceStore.folders.length) {
    void workspaceStore.loadFolders()
  }
})

watch(extractDocuments, (enabled) => {
  if (enabled && documentFiles.value.length && (!rootFolderName.value || rootFolderName.value === '附件批量导入')) {
    rootFolderName.value = '文档转笔记'
  }
})

function selectMode(nextMode: ImportCenterMode) {
  mode.value = nextMode

  if (!selectedFiles.value.length) {
    rootFolderName.value = fallbackRootName()
  }
}

function triggerPicker(nextMode: ImportCenterMode) {
  selectMode(nextMode)

  if (nextMode === 'BATCH_MARKDOWN') {
    batchInputRef.value?.click()
    return
  }

  if (nextMode === 'ATTACHMENTS') {
    attachmentInputRef.value?.click()
    return
  }

  directoryInputRef.value?.click()
}

function handleDirectoryChange(event: Event) {
  applySelectedFiles(event)
}

function handleBatchChange(event: Event) {
  applySelectedFiles(event)
}

function handleAttachmentChange(event: Event) {
  applySelectedFiles(event)
}

function applySelectedFiles(event: Event) {
  const input = event.target as HTMLInputElement
  const files = Array.from(input.files ?? []) as ImportableFile[]

  applyFiles(files)
  input.value = ''
}

function handleDrop(event: DragEvent) {
  const files = Array.from(event.dataTransfer?.files ?? []) as ImportableFile[]

  if (!files.length) {
    return
  }

  mode.value = files.some((file) => isMarkdownPath(relativePathOf(file))) ? 'BATCH_MARKDOWN' : 'ATTACHMENTS'
  applyFiles(files)
}

function applyFiles(files: ImportableFile[]) {
  selectedFiles.value = files
  result.value = null
  uploadedAttachments.value = []
  attachmentWarnings.value = []
  rootFolderName.value = inferRootName(files)

  if (!files.length) {
    return
  }

  const validCount = markdownFiles.value.length + attachmentFiles.value.length
  if (validCount === 0) {
    ElMessage.warning('没有找到可导入的 Markdown、图片、PDF 或 Word 文件')
    return
  }

  if (oversizedAttachmentFiles.value.length) {
    ElMessage.warning(`已忽略 ${oversizedAttachmentFiles.value.length} 个超过 25MB 的附件`)
  }

  if (unsupportedClientCount.value > 0) {
    ElMessage.info(`已自动忽略 ${unsupportedClientCount.value} 个暂不支持的文件`)
  }
}

async function submitImport() {
  if (!canImport.value) {
    ElMessage.warning('请先选择可导入的 Markdown 或附件文件')
    return
  }

  importing.value = true
  result.value = null
  uploadedAttachments.value = []
  attachmentWarnings.value = []

  try {
    let combinedResult: ImportResponse | null = null

    if (markdownFiles.value.length) {
      const markdownResult = await importMarkdownFiles({
        files: markdownFiles.value,
        mode: toMarkdownMode(mode.value),
        rootFolderName: rootFolderName.value,
        targetFolderId: targetFolderId.value
      })
      combinedResult = mergeImportResponses(combinedResult, markdownResult)
      await workspaceStore.loadExplorer()
    }

    if (extractedDocumentFiles.value.length) {
      const documentResult = await importDocumentsAsNotes({
        files: extractedDocumentFiles.value,
        rootFolderName: rootFolderName.value || '文档转笔记',
        targetFolderId: targetFolderId.value,
        keepAttachments: keepOriginalDocuments.value
      })
      combinedResult = mergeImportResponses(combinedResult, documentResult)
      uploadedAttachments.value.push(...(documentResult.attachments ?? []))
      await workspaceStore.loadExplorer()
    }

    result.value = combinedResult

    if (directAttachmentFiles.value.length) {
      uploadedAttachments.value.push(...await uploadAttachmentFiles(directAttachmentFiles.value))
    }

    const successParts = [
      result.value?.importedNotes ? `新增 ${result.value.importedNotes} 篇笔记` : '',
      uploadedAttachments.value.length ? `上传 ${uploadedAttachments.value.length} 个附件` : ''
    ].filter(Boolean)

    if (attachmentWarnings.value.length) {
      ElMessage.warning(`导入完成，但 ${attachmentWarnings.value.length} 个附件上传失败`)
    } else {
      ElMessage.success(`导入完成：${successParts.join('，') || '没有新内容'}`)
    }
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '导入失败，请稍后重试')
  } finally {
    importing.value = false
  }
}

function clearSelection() {
  selectedFiles.value = []
  result.value = null
  uploadedAttachments.value = []
  attachmentWarnings.value = []
  rootFolderName.value = fallbackRootName()
}

function openImportedFolder() {
  if (!result.value?.rootFolderId) {
    return
  }

  workspaceStore.selectFolder(result.value.rootFolderId)
  void router.push('/folder')
}

function openFirstImportedNote() {
  const firstNote = result.value?.notes[0]
  if (!firstNote) {
    return
  }

  void router.push(`/note/${firstNote.id}/edit`)
}

function openAttachmentCenter() {
  void router.push('/attachments')
}

function relativePathOf(file: ImportableFile) {
  return file.webkitRelativePath || file.name
}

function isMarkdownPath(path: string) {
  const normalized = path.toLowerCase()
  return normalized.endsWith('.md') || normalized.endsWith('.markdown')
}

function toMarkdownMode(value: ImportCenterMode): ImportMode {
  return value === 'ATTACHMENTS' ? 'BATCH_MARKDOWN' : value
}

function mergeImportResponses(current: ImportResponse | null, next: ImportResponse): ImportResponse {
  if (!current) {
    return {
      ...next,
      tags: [...next.tags],
      warnings: [...next.warnings],
      notes: [...next.notes],
      attachments: [...(next.attachments ?? [])]
    }
  }

  return {
    ...current,
    rootFolderId: current.rootFolderId || next.rootFolderId,
    rootFolderName: current.rootFolderName || next.rootFolderName,
    totalFiles: current.totalFiles + next.totalFiles,
    importedNotes: current.importedNotes + next.importedNotes,
    createdFolders: current.createdFolders + next.createdFolders,
    skippedFiles: current.skippedFiles + next.skippedFiles,
    tags: [...new Set([...current.tags, ...next.tags])],
    warnings: [...current.warnings, ...next.warnings],
    notes: [...current.notes, ...next.notes],
    attachments: [...(current.attachments ?? []), ...(next.attachments ?? [])]
  }
}

async function uploadAttachmentFiles(files: ImportableFile[]) {
  const uploaded: AttachmentItem[] = []

  for (const file of files) {
    try {
      uploaded.push(await uploadAttachment(file))
    } catch (error) {
      const message = error instanceof Error ? error.message : '上传失败'
      attachmentWarnings.value.push(`${file.name}：${message}`)
    }
  }

  return uploaded
}

function attachmentFileType(file: File) {
  const extension = file.name.split('.').pop()?.toLowerCase() || ''

  if (file.type.startsWith('image/') || ['png', 'jpg', 'jpeg', 'webp', 'gif'].includes(extension)) {
    return 'IMAGE'
  }

  if (file.type === 'application/pdf' || extension === 'pdf') {
    return 'PDF'
  }

  return 'WORD'
}

function inferRootName(files: ImportableFile[]) {
  const markdownCandidates = files.filter((file) => isMarkdownPath(relativePathOf(file)))
  const commonTop = detectCommonTopDirectory(markdownCandidates)

  if (commonTop) {
    return commonTop
  }

  return fallbackRootName()
}

function fallbackRootName() {
  if (mode.value === 'ATTACHMENTS') {
    return '附件批量导入'
  }

  if (mode.value === 'OBSIDIAN_VAULT') {
    return 'Obsidian 导入'
  }

  if (mode.value === 'BATCH_MARKDOWN') {
    return '批量 Markdown 导入'
  }

  return 'Markdown 文件夹导入'
}

function detectCommonTopDirectory(files: ImportableFile[]) {
  const topSegments = new Set<string>()

  for (const file of files) {
    const segments = relativePathOf(file).replace(/\\/g, '/').split('/').filter(Boolean)

    if (segments.length < 2) {
      return ''
    }

    topSegments.add(segments[0])
  }

  return topSegments.size === 1 ? [...topSegments][0] : ''
}

function logicalSegments(path: string) {
  const segments = path.replace(/\\/g, '/').split('/').filter(Boolean)

  if (commonTopDirectory.value && segments[0] === commonTopDirectory.value) {
    return segments.slice(1)
  }

  return segments
}

function formatBytes(bytes: number) {
  if (bytes < 1024) {
    return `${bytes} B`
  }

  if (bytes < 1024 * 1024) {
    return `${(bytes / 1024).toFixed(1)} KB`
  }

  return `${(bytes / 1024 / 1024).toFixed(1)} MB`
}
</script>

<template>
  <div class="import-center page-shell">
    <PageHero
      kicker="Import Center"
      title="导入中心"
      :description="pageDescription"
    >
      <template #actions>
        <el-button type="primary" :loading="importing" :disabled="!canImport" @click="submitImport">
          {{ importButtonText }}
        </el-button>
        <el-button plain @click="clearSelection">清空选择</el-button>
      </template>
    </PageHero>

    <input
      ref="directoryInputRef"
      class="import-center__input"
      type="file"
      accept=".md,.markdown,text/markdown,image/png,image/jpeg,image/webp,image/gif,application/pdf,.doc,.docx,application/msword,application/vnd.openxmlformats-officedocument.wordprocessingml.document"
      webkitdirectory
      directory
      multiple
      @change="handleDirectoryChange"
    />
    <input
      ref="batchInputRef"
      class="import-center__input"
      type="file"
      accept=".md,.markdown,text/markdown"
      multiple
      @change="handleBatchChange"
    />
    <input
      ref="attachmentInputRef"
      class="import-center__input"
      type="file"
      accept="image/png,image/jpeg,image/webp,image/gif,application/pdf,.doc,.docx,application/msword,application/vnd.openxmlformats-officedocument.wordprocessingml.document"
      multiple
      @change="handleAttachmentChange"
    />

    <section class="import-center__modes">
      <button
        v-for="item in modeCards"
        :key="item.value"
        type="button"
        class="import-mode-card"
        :class="{ 'import-mode-card--active': mode === item.value }"
        @click="selectMode(item.value)"
      >
        <span>{{ item.kicker }}</span>
        <strong>{{ item.title }}</strong>
        <p>{{ item.description }}</p>
        <em>{{ item.buttonText }}</em>
      </button>
    </section>

    <section class="import-center__workspace">
      <article
        class="import-dropzone panel"
        @dragover.prevent
        @drop.prevent="handleDrop"
      >
        <div class="import-dropzone__copy">
          <span class="section-kicker">{{ activeModeCard.kicker }} Import</span>
          <h2>{{ activeModeCard.title }}</h2>
          <p>
            {{ activeModeCard.description }}
            <template v-if="mode === 'ATTACHMENTS'">
              也可以直接把图片、PDF、Word 拖到这里。
            </template>
            <template v-else-if="mode === 'BATCH_MARKDOWN'">
              也可以把多个 Markdown 文件和相关附件一起拖到这里。
            </template>
            <template v-else>
              目录导入可以同时识别 Markdown 和附件，浏览器会保留相对路径。
            </template>
          </p>
        </div>

        <div class="import-dropzone__actions">
          <el-button type="primary" size="large" @click="triggerPicker(mode)">
            {{ activeModeCard.buttonText }}
          </el-button>
          <el-button plain size="large" @click="triggerPicker('BATCH_MARKDOWN')">只选 Markdown</el-button>
          <el-button plain size="large" @click="triggerPicker('ATTACHMENTS')">只选附件</el-button>
        </div>

        <div class="import-dropzone__stats">
          <article>
            <span>Markdown</span>
            <strong>{{ markdownFiles.length }}</strong>
          </article>
          <article>
            <span>附件</span>
            <strong>{{ attachmentFiles.length }}</strong>
          </article>
          <article>
            <span>总大小</span>
            <strong>{{ formatBytes(totalSize) }}</strong>
          </article>
          <article class="import-dropzone__stat--muted">
            <span>已忽略</span>
            <strong>{{ skippedClientCount }}</strong>
          </article>
        </div>
      </article>

      <aside class="import-settings panel">
        <div class="import-settings__head">
          <div>
            <span class="section-kicker">Settings</span>
            <h3>导入设置</h3>
          </div>
          <el-button text size="small" @click="showAdvancedSettings = !showAdvancedSettings">
            {{ showAdvancedSettings ? '收起' : '更多设置' }}
          </el-button>
        </div>

        <div
          v-if="documentFiles.length || mode === 'ATTACHMENTS'"
          class="import-settings__field import-settings__switch"
        >
          <div>
            <span>PDF / Word</span>
            <strong>{{ extractDocuments ? '转成笔记' : '仅附件' }}</strong>
          </div>
          <el-switch
            v-model="extractDocuments"
            :disabled="!documentFiles.length"
            active-text="转笔记"
            inactive-text="附件"
          />
        </div>

        <div v-if="extractDocuments" class="import-settings__field import-settings__switch">
          <div>
            <span>原始文档</span>
            <strong>{{ keepOriginalDocuments ? '保留附件' : '不保留' }}</strong>
          </div>
          <el-switch
            v-model="keepOriginalDocuments"
            active-text="保留"
            inactive-text="不保留"
          />
        </div>

        <template v-if="showAdvancedSettings && (mode !== 'ATTACHMENTS' || markdownFiles.length || extractedDocumentFiles.length)">
          <label class="import-settings__field">
            <span>导入后的根文件夹</span>
            <el-input v-model="rootFolderName" maxlength="80" placeholder="例如：我的 Obsidian Vault" />
          </label>

          <label class="import-settings__field">
            <span>导入到哪个位置</span>
            <el-select v-model="targetFolderId" clearable placeholder="作为根目录导入">
              <el-option label="作为根目录导入" :value="null" />
              <el-option
                v-for="folder in workspaceStore.folderOptions"
                :key="folder.value"
                :label="folder.label"
                :value="folder.value"
              />
            </el-select>
          </label>

          <div class="import-settings__hint">
            <strong>笔记导入会自动处理</strong>
            <span>一级导入文件夹、子目录、H1 标题、front matter 标签和正文里的 #标签。</span>
          </div>
        </template>

        <div v-if="showAdvancedSettings" class="import-settings__hint">
          <strong>附件会进入附件中心</strong>
          <span>
            图片始终作为附件；PDF/Word 可选择仅保存附件，或抽取文字生成可搜索、可编辑的笔记。
          </span>
        </div>

        <el-button type="primary" :loading="importing" :disabled="!canImport" @click="submitImport">
          {{ importButtonText }}
        </el-button>
      </aside>
    </section>

    <section v-if="markdownFiles.length || attachmentFiles.length" class="import-preview panel">
      <div class="import-preview__head">
        <div>
          <span class="section-kicker">Preview</span>
          <h2>导入预览</h2>
        </div>
        <small>只展示部分文件，Markdown 会按相对路径导入，附件会上传到附件中心。</small>
      </div>

      <div v-if="markdownFiles.length" class="import-preview__groups">
        <article v-for="group in previewGroups" :key="group.name">
          <strong>{{ group.name }}</strong>
          <span>{{ group.count }} 篇</span>
        </article>
      </div>

      <div v-if="markdownFiles.length" class="import-preview__files">
        <span v-for="file in previewFiles" :key="relativePathOf(file)">
          {{ relativePathOf(file) }}
        </span>
      </div>

      <div v-if="attachmentFiles.length" class="import-preview__attachments">
        <article v-for="file in previewAttachments" :key="relativePathOf(file)">
          <span>{{ attachmentIcon(attachmentFileType(file)) }}</span>
          <div>
            <strong>{{ file.name }}</strong>
            <small>
              {{ attachmentTypeLabel(attachmentFileType(file)) }} / {{ formatFileSize(file.size) }}
              <template v-if="extractDocuments && ['PDF', 'WORD'].includes(attachmentFileType(file))">
                / 将抽取为笔记
              </template>
            </small>
          </div>
        </article>
      </div>
    </section>

    <section v-if="hasImportResult" class="import-result panel">
      <div class="import-result__summary">
        <span class="section-kicker">Import Finished</span>
        <h2>导入完成</h2>
        <p>{{ resultSummaryText }}</p>
      </div>

      <div class="import-result__actions">
        <el-button v-if="result?.rootFolderId" type="primary" @click="openImportedFolder">查看导入文件夹</el-button>
        <el-button v-if="result?.notes.length" plain @click="openFirstImportedNote">打开第一篇笔记</el-button>
        <el-button v-if="uploadedAttachments.length" plain @click="openAttachmentCenter">查看附件中心</el-button>
      </div>

      <div v-if="result?.tags.length" class="import-result__tags">
        <span v-for="tag in result.tags.slice(0, 18)" :key="tag">#{{ tag }}</span>
      </div>

      <div v-if="uploadedAttachments.length" class="import-result__attachments">
        <span v-for="attachment in uploadedAttachments.slice(0, 18)" :key="attachment.id">
          {{ attachmentTypeLabel(attachment.fileType) }} / {{ attachment.originalName }}
        </span>
      </div>

      <div v-if="resultWarnings.length" class="import-result__warnings">
        <strong>需要留意</strong>
        <span v-for="warning in resultWarnings" :key="warning">{{ warning }}</span>
      </div>
    </section>
  </div>
</template>

<style scoped>
.import-center {
  display: grid;
  gap: 16px;
}

.import-center__input {
  display: none;
}

.import-center__modes {
  display: flex;
  gap: 8px;
  overflow-x: auto;
  padding: 2px;
  scrollbar-width: none;
}

.import-center__modes::-webkit-scrollbar {
  display: none;
}

.import-mode-card {
  display: flex;
  flex: 1 0 170px;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  min-height: 52px;
  padding: 10px 12px;
  border: 1px solid rgba(184, 92, 56, 0.12);
  border-radius: 16px;
  background:
    radial-gradient(circle at top right, rgba(197, 157, 88, 0.12), transparent 34%),
    rgba(255, 255, 255, 0.62);
  color: inherit;
  text-align: left;
  cursor: pointer;
  transition:
    transform 0.2s ease,
    border-color 0.2s ease,
    box-shadow 0.2s ease,
    background-color 0.2s ease;
}

.import-mode-card:hover,
.import-mode-card--active {
  transform: translateY(-1px);
  border-color: rgba(184, 92, 56, 0.28);
  background: rgba(255, 255, 255, 0.86);
  box-shadow: 0 10px 24px rgba(141, 69, 41, 0.07);
}

.import-mode-card span,
.import-mode-card em {
  color: var(--accent-strong);
  font-size: 0.72rem;
  font-style: normal;
  letter-spacing: 0.06em;
  text-transform: uppercase;
}

.import-mode-card strong {
  font-family: var(--header-font);
  font-size: 0.98rem;
  line-height: 1.2;
}

.import-mode-card p {
  display: none;
}

.import-mode-card em {
  display: none;
}

.import-center__workspace {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 300px;
  gap: 14px;
}

.import-dropzone,
.import-settings,
.import-preview,
.import-result {
  padding: 16px;
}

.import-dropzone {
  display: grid;
  gap: 14px;
  min-height: 220px;
  border-style: dashed;
  background:
    radial-gradient(circle at top left, rgba(54, 92, 75, 0.12), transparent 34%),
    linear-gradient(135deg, rgba(255, 255, 255, 0.8), rgba(255, 249, 239, 0.66));
}

.import-dropzone__copy {
  display: grid;
  align-content: center;
  gap: 8px;
}

.import-dropzone__copy h2,
.import-preview h2,
.import-result h2 {
  margin: 0;
  font-family: var(--header-font);
  font-size: clamp(1.35rem, 2.2vw, 1.9rem);
}

.import-dropzone__copy p,
.import-result p {
  max-width: 680px;
  margin: 0;
  color: var(--text-soft);
  font-size: 0.92rem;
  line-height: 1.6;
}

.import-dropzone__actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.import-dropzone__stats {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 8px;
}

.import-dropzone__stats article {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  padding: 10px 12px;
  border-radius: 14px;
  background: rgba(255, 255, 255, 0.7);
  border: 1px solid rgba(54, 92, 75, 0.1);
}

.import-dropzone__stats span,
.import-settings__field span,
.import-preview__head small,
.import-result__warnings span {
  color: var(--text-soft);
}

.import-dropzone__stats strong {
  color: var(--text-main);
  font-size: 1.05rem;
}

.import-dropzone__stat--muted strong {
  color: var(--accent-strong);
}

.import-settings {
  display: grid;
  align-content: start;
  gap: 12px;
}

.import-settings__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
}

.import-settings h3 {
  margin: 2px 0 0;
  font-family: var(--header-font);
  font-size: 1.08rem;
}

.import-settings__field {
  display: grid;
  gap: 8px;
}

.import-settings__switch {
  grid-template-columns: minmax(0, 1fr) auto;
  align-items: center;
  gap: 10px;
  padding: 10px 12px;
  border: 1px solid rgba(54, 92, 75, 0.1);
  border-radius: 14px;
  background: rgba(255, 255, 255, 0.62);
}

.import-settings__switch div {
  display: grid;
  gap: 4px;
}

.import-settings__switch strong {
  color: var(--text-main);
  font-size: 0.94rem;
}

.import-settings__hint {
  display: grid;
  gap: 4px;
  padding: 12px;
  border-radius: 14px;
  background: rgba(184, 92, 56, 0.08);
  color: var(--text-soft);
  font-size: 0.88rem;
  line-height: 1.55;
}

.import-settings__hint strong {
  color: var(--accent-strong);
}

.import-preview,
.import-result {
  display: grid;
  gap: 12px;
}

.import-preview__head,
.import-result {
  align-items: start;
}

.import-preview__head,
.import-result__actions {
  display: flex;
  justify-content: space-between;
  gap: 10px;
  flex-wrap: wrap;
}

.import-preview__groups {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(140px, 1fr));
  gap: 8px;
}

.import-preview__groups article {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  padding: 10px 12px;
  border-radius: 14px;
  background: rgba(54, 92, 75, 0.08);
}

.import-preview__files,
.import-preview__attachments,
.import-result__attachments,
.import-result__tags,
.import-result__warnings {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.import-preview__files span,
.import-result__attachments span,
.import-result__tags span,
.import-result__warnings span {
  display: inline-flex;
  align-items: center;
  min-height: 28px;
  padding: 0 10px;
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.76);
  border: 1px solid rgba(184, 92, 56, 0.12);
  font-size: 0.84rem;
}

.import-preview__attachments {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));
  gap: 8px;
}

.import-preview__attachments article {
  display: grid;
  grid-template-columns: 38px minmax(0, 1fr);
  gap: 10px;
  align-items: center;
  padding: 10px;
  border: 1px solid rgba(54, 92, 75, 0.1);
  border-radius: 14px;
  background:
    radial-gradient(circle at top left, rgba(54, 92, 75, 0.1), transparent 40%),
    rgba(255, 255, 255, 0.72);
}

.import-preview__attachments article > span {
  display: grid;
  place-items: center;
  width: 38px;
  height: 38px;
  border-radius: 12px;
  background: rgba(54, 92, 75, 0.1);
  color: #365c4b;
  font-weight: 800;
}

.import-preview__attachments strong,
.import-preview__attachments small {
  display: block;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.import-preview__attachments small {
  color: var(--text-soft);
}

.import-result {
  grid-template-columns: minmax(0, 1fr) auto;
  background:
    radial-gradient(circle at top right, rgba(54, 92, 75, 0.12), transparent 34%),
    rgba(255, 255, 255, 0.74);
}

.import-result__tags,
.import-result__attachments,
.import-result__warnings {
  grid-column: 1 / -1;
}

.import-result__tags span {
  color: #365c4b;
  background: rgba(54, 92, 75, 0.08);
}

.import-result__attachments span {
  color: #64442d;
  background: rgba(197, 157, 88, 0.12);
}

.import-result__warnings {
  display: grid;
  gap: 8px;
  padding: 12px;
  border-radius: 14px;
  background: rgba(184, 92, 56, 0.08);
}

.import-result__warnings strong {
  color: var(--accent-strong);
}

@media (max-width: 1100px) {
  .import-center__workspace {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 760px) {
  .import-dropzone__stats,
  .import-result {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 640px) {
  .import-center {
    gap: 16px;
  }

  .import-center__modes {
    gap: 8px;
  }

  .import-mode-card {
    flex: 0 0 154px;
  }

  .import-mode-card p,
  .import-dropzone__copy p {
    display: none;
  }

  .import-mode-card,
  .import-dropzone,
  .import-settings,
  .import-preview,
  .import-result {
    padding: 16px;
    border-radius: 20px;
  }

  .import-dropzone {
    min-height: 0;
  }

  .import-dropzone__stats {
    display: flex;
    gap: 10px;
    overflow-x: auto;
    scrollbar-width: none;
  }

  .import-dropzone__stats::-webkit-scrollbar {
    display: none;
  }

  .import-dropzone__stats article {
    flex: 0 0 118px;
  }

  .import-dropzone__actions,
  .import-result__actions {
    align-items: stretch;
  }

  .import-dropzone__actions :deep(.el-button),
  .import-settings :deep(.el-button),
  .import-result__actions :deep(.el-button) {
    flex: 1 1 140px;
    min-width: 0;
  }

  .import-dropzone__stats article {
    padding: 10px 12px;
    border-radius: 14px;
  }

  .import-preview__groups {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 420px) {
  .import-mode-card,
  .import-dropzone,
  .import-settings,
  .import-preview,
  .import-result {
    padding: 14px;
  }

  .import-dropzone__actions :deep(.el-button),
  .import-settings :deep(.el-button),
  .import-result__actions :deep(.el-button) {
    flex: 1 1 132px;
  }
}
</style>
