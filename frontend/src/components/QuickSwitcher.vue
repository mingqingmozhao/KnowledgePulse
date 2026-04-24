<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { useRouter } from 'vue-router'
import { useNoteWorkspaceStore } from '@/stores/noteWorkspace'
import { useWorkspaceStore } from '@/stores/workspace'
import { formatDateOnly, flattenFolders, relativeTime } from '@/utils/format'
import {
  buildDraftNoteRoute,
  buildNoteEditRoute,
  buildWorkspaceTabRoute
} from '@/utils/noteWorkspace'

type QuickSwitcherTone = 'action' | 'workspace' | 'note' | 'folder' | 'daily' | 'template'

type QuickSwitcherItem = {
  id: string
  group: string
  title: string
  subtitle: string
  meta?: string
  badge?: string
  tone: QuickSwitcherTone
  run: () => Promise<unknown> | void
}

type FolderSearchItem = {
  id: number
  name: string
  path: string
}

const props = defineProps<{
  visible: boolean
}>()

const emit = defineEmits<{
  (event: 'update:visible', value: boolean): void
}>()

const router = useRouter()
const workspaceStore = useWorkspaceStore()
const noteWorkspaceStore = useNoteWorkspaceStore()

const inputRef = ref<HTMLInputElement | null>(null)
const query = ref('')
const loading = ref(false)
const activeIndex = ref(0)

const normalizedQuery = computed(() => query.value.trim().toLowerCase())
const queryTerms = computed(() => normalizedQuery.value.split(/\s+/).filter(Boolean))
const tagQuery = computed(() => {
  const raw = query.value.trim()
  return raw.startsWith('#') ? raw.slice(1).trim() : ''
})
const parsedDateQuery = computed(() => normalizeDateQuery(query.value))

const folderSearchIndex = computed<FolderSearchItem[]>(() => {
  const flatFolders = flattenFolders(workspaceStore.folders)
  const pathById = new Map<number, string>()

  flatFolders.forEach((folder) => {
    if (!folder.parentId) {
      pathById.set(folder.id, folder.name)
      return
    }

    const parentPath = pathById.get(folder.parentId)
    pathById.set(folder.id, parentPath ? `${parentPath} / ${folder.name}` : folder.name)
  })

  return flatFolders.map((folder) => ({
    id: folder.id,
    name: folder.name,
    path: pathById.get(folder.id) || folder.name
  }))
})

const actionItems = computed<QuickSwitcherItem[]>(() => {
  const items: QuickSwitcherItem[] = [
    {
      id: 'create-draft',
      group: '快捷操作',
      title: '新建草稿',
      subtitle: '立即打开一个新的工作区草稿标签',
      badge: 'Action',
      tone: 'action',
      run: () => router.push(buildDraftNoteRoute())
    },
    {
      id: 'open-today-daily',
      group: '快捷操作',
      title: '打开今日日记',
      subtitle: '如果今天还没有开始记录，会自动创建一篇新的每日日记',
      meta: formatDateOnly(new Date().toISOString().slice(0, 10)),
      badge: 'Daily',
      tone: 'daily',
      run: () => openDailyNote()
    },
    {
      id: 'go-folder',
      group: '快捷操作',
      title: '打开文件与笔记',
      subtitle: '切到文件夹、收藏和回收站的统一管理页',
      badge: 'Page',
      tone: 'action',
      run: () => router.push('/folder')
    },
    {
      id: 'go-templates',
      group: '快捷操作',
      title: '打开模板中心',
      subtitle: '管理常用模板，或从模板快速创建草稿标签',
      badge: 'Page',
      tone: 'template',
      run: () => router.push('/templates')
    },
    {
      id: 'go-attachments',
      group: '快捷操作',
      title: '打开附件中心',
      subtitle: '管理图片、PDF、Word 和未使用附件',
      badge: 'Page',
      tone: 'action',
      run: () => router.push('/attachments')
    },
    {
      id: 'go-import',
      group: '快捷操作',
      title: '打开导入中心',
      subtitle: '导入 Markdown 文件夹、Obsidian vault 或批量 Markdown',
      badge: 'Import',
      tone: 'action',
      run: () => router.push('/import')
    },
    {
      id: 'go-graph',
      group: '快捷操作',
      title: '打开知识图谱',
      subtitle: '查看笔记关系、连线和图谱结构',
      badge: 'Page',
      tone: 'action',
      run: () => router.push('/graph')
    },
    {
      id: 'go-dashboard',
      group: '快捷操作',
      title: '返回仪表盘',
      subtitle: '回到概览、每日日记和灵感面板',
      badge: 'Page',
      tone: 'action',
      run: () => router.push('/dashboard')
    },
    {
      id: 'go-profile',
      group: '快捷操作',
      title: '打开个人中心',
      subtitle: '管理头像、资料和账号设置',
      badge: 'Page',
      tone: 'action',
      run: () => router.push('/profile')
    }
  ]

  if (noteWorkspaceStore.activeTab) {
    items.unshift({
      id: 'continue-workspace',
      group: '快捷操作',
      title: '继续当前工作区',
      subtitle: noteWorkspaceStore.activeTab.title,
      meta: noteWorkspaceStore.activeTab.dirty ? '有未保存改动' : '内容已同步',
      badge: 'Workspace',
      tone: 'workspace',
      run: () => router.push(buildWorkspaceTabRoute(noteWorkspaceStore.activeTab!))
    })
  }

  const trimmedQuery = query.value.trim()

  if (trimmedQuery) {
    items.unshift({
      id: `search-${trimmedQuery}`,
      group: '快捷操作',
      title: `全文搜索 “${trimmedQuery}”`,
      subtitle: '跳到全文搜索页，查看更完整的匹配结果',
      badge: 'Search',
      tone: 'action',
      run: () =>
        router.push({
          path: '/search',
          query: {
            keyword: trimmedQuery
          }
        })
    })
  }

  if (tagQuery.value) {
    items.unshift({
      id: `tag-${tagQuery.value}`,
      group: '快捷操作',
      title: `搜索标签 #${tagQuery.value}`,
      subtitle: '只看命中这个标签的相关笔记',
      badge: 'Tag',
      tone: 'action',
      run: () =>
        router.push({
          path: '/search',
          query: {
            tag: tagQuery.value
          }
        })
    })
  }

  if (parsedDateQuery.value) {
    items.unshift({
      id: `daily-${parsedDateQuery.value}`,
      group: '快捷操作',
      title: `打开 ${parsedDateQuery.value} 的每日日记`,
      subtitle: '如果这一天还没有记录，会自动创建一篇新的每日日记',
      badge: 'Daily',
      tone: 'daily',
      run: () => openDailyNote(parsedDateQuery.value!)
    })
  }

  return items.filter((item) => matchesQuery(item.title, item.subtitle, item.meta || '')).slice(0, 7)
})

const workspaceItems = computed<QuickSwitcherItem[]>(() =>
  [...noteWorkspaceStore.tabs]
    .sort((left, right) => {
      if (left.key === noteWorkspaceStore.activeKey) {
        return -1
      }

      if (right.key === noteWorkspaceStore.activeKey) {
        return 1
      }

      if (left.pinned !== right.pinned) {
        return left.pinned ? -1 : 1
      }

      return right.updatedAt - left.updatedAt
    })
    .filter((tab) =>
      matchesQuery(
        tab.title,
        tab.routeKind === 'draft' ? '草稿 工作区' : '笔记 工作区',
        tab.dirty ? '未保存' : '已同步',
        tab.pinned ? '固定' : ''
      )
    )
    .slice(0, 6)
    .map((tab) => ({
      id: `workspace-${tab.key}`,
      group: '工作区',
      title: tab.title,
      subtitle: tab.routeKind === 'draft' ? '草稿标签' : '已打开的笔记标签',
      meta: tab.dirty ? '未保存改动' : '内容已同步',
      badge: tab.pinned ? 'Pinned' : tab.routeKind === 'draft' ? 'Draft' : 'Open',
      tone: 'workspace' as const,
      run: () => router.push(buildWorkspaceTabRoute(tab))
    }))
)

const noteItems = computed<QuickSwitcherItem[]>(() =>
  workspaceStore.notes
    .filter((note) =>
      matchesQuery(
        note.title,
        note.folderName || '',
        note.tags.join(' '),
        note.dailyNoteDate || '',
        note.dailyNote ? '每日日记' : ''
      )
    )
    .slice(0, 8)
    .map((note) => ({
      id: `note-${note.id}`,
      group: queryTerms.value.length ? '匹配笔记' : '最近笔记',
      title: note.title,
      subtitle: note.folderName || (note.dailyNoteDate ? '每日日记' : '未归档文件夹'),
      meta: note.dailyNoteDate ? formatDateOnly(note.dailyNoteDate) : relativeTime(note.updateTime),
      badge: note.dailyNoteDate ? 'Daily' : note.favorited ? 'Star' : 'Note',
      tone: note.dailyNoteDate ? 'daily' : 'note',
      run: () => openNote(note.id, note.title)
    }))
)

const folderItems = computed<QuickSwitcherItem[]>(() =>
  folderSearchIndex.value
    .filter((folder) => matchesQuery(folder.name, folder.path))
    .slice(0, queryTerms.value.length ? 8 : 5)
    .map((folder) => ({
      id: `folder-${folder.id}`,
      group: queryTerms.value.length ? '匹配文件夹' : '常用目录',
      title: folder.name,
      subtitle: folder.path,
      badge: 'Folder',
      tone: 'folder' as const,
      run: () => openFolder(folder.id)
    }))
)

const templateItems = computed<QuickSwitcherItem[]>(() =>
  workspaceStore.templates
    .filter((template) =>
      matchesQuery(
        template.name,
        template.description || '',
        template.category || '',
        template.tags.join(' '),
        template.system ? '系统模板' : '我的模板'
      )
    )
    .slice(0, queryTerms.value.length ? 8 : 5)
    .map((template) => ({
      id: `template-${template.id}`,
      group: queryTerms.value.length ? '匹配模板' : '常用模板',
      title: template.name,
      subtitle: template.description || `${template.category || '通用'} 模板`,
      meta: template.tags.slice(0, 3).map((tag) => `#${tag}`).join(' '),
      badge: template.system ? 'System' : 'Template',
      tone: 'template' as const,
      run: () => startFromTemplate(template.id)
    }))
)

const groupedItems = computed(() => {
  const groups = [
    {
      label: '快捷操作',
      items: actionItems.value
    },
    {
      label: '工作区',
      items: workspaceItems.value
    },
    {
      label: queryTerms.value.length ? '匹配笔记' : '最近笔记',
      items: noteItems.value
    },
    {
      label: queryTerms.value.length ? '匹配文件夹' : '常用目录',
      items: folderItems.value
    },
    {
      label: queryTerms.value.length ? '匹配模板' : '常用模板',
      items: templateItems.value
    }
  ]

  return groups.filter((group) => group.items.length > 0)
})

const flatItems = computed(() => groupedItems.value.flatMap((group) => group.items))

watch(
  () => props.visible,
  async (visible) => {
    if (typeof document !== 'undefined') {
      document.body.style.overflow = visible ? 'hidden' : ''
    }

    if (!visible) {
      query.value = ''
      activeIndex.value = 0
      return
    }

    const needsExplorer = !workspaceStore.notes.length || !workspaceStore.folders.length
    const needsTemplates = !workspaceStore.templates.length

    if (needsExplorer || needsTemplates) {
      loading.value = true

      try {
        await Promise.all([
          needsExplorer ? workspaceStore.loadExplorer() : Promise.resolve(),
          needsTemplates ? workspaceStore.loadTemplates() : Promise.resolve()
        ])
      } catch (error) {
        ElMessage.error(error instanceof Error ? error.message : '加载快速切换数据失败')
      } finally {
        loading.value = false
      }
    }

    await nextTick()
    inputRef.value?.focus()
    inputRef.value?.select()
  }
)

watch(flatItems, (items) => {
  if (!items.length) {
    activeIndex.value = 0
    return
  }

  activeIndex.value = Math.min(activeIndex.value, items.length - 1)
})

watch(query, () => {
  activeIndex.value = 0
})

onBeforeUnmount(() => {
  if (typeof document !== 'undefined') {
    document.body.style.overflow = ''
  }
})

function normalizeDateQuery(value: string) {
  const normalized = value.trim().replace(/\//g, '-')

  if (!/^\d{4}-\d{1,2}-\d{1,2}$/.test(normalized)) {
    return null
  }

  const [year, month, day] = normalized.split('-').map(Number)
  const candidate = new Date(year, month - 1, day)

  if (
    candidate.getFullYear() !== year ||
    candidate.getMonth() !== month - 1 ||
    candidate.getDate() !== day
  ) {
    return null
  }

  return [
    String(year).padStart(4, '0'),
    String(month).padStart(2, '0'),
    String(day).padStart(2, '0')
  ].join('-')
}

function matchesQuery(...parts: string[]) {
  if (!queryTerms.value.length) {
    return true
  }

  const haystack = parts.join(' ').toLowerCase()
  return queryTerms.value.every((term) => haystack.includes(term))
}

function close() {
  emit('update:visible', false)
}

async function openNote(noteId: number, title: string) {
  noteWorkspaceStore.openNoteTab(noteId, title)
  await router.push(buildNoteEditRoute(noteId))
}

async function openFolder(folderId: number) {
  workspaceStore.selectFolder(folderId)
  await router.push('/folder')
}

async function openDailyNote(date?: string) {
  const note = await workspaceStore.openDailyNote(date)
  noteWorkspaceStore.openNoteTab(note.id, note.title)
  await router.push(buildNoteEditRoute(note.id))
}

async function startFromTemplate(templateId: number) {
  await router.push(buildDraftNoteRoute(undefined, templateId))
}

async function executeItem(item: QuickSwitcherItem) {
  close()

  try {
    await item.run()
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '执行快捷操作失败')
  }
}

async function executeActiveItem() {
  const targetItem = flatItems.value[activeIndex.value]

  if (!targetItem) {
    return
  }

  await executeItem(targetItem)
}

function selectPrevious() {
  if (!flatItems.value.length) {
    return
  }

  activeIndex.value =
    activeIndex.value <= 0 ? flatItems.value.length - 1 : activeIndex.value - 1
}

function selectNext() {
  if (!flatItems.value.length) {
    return
  }

  activeIndex.value =
    activeIndex.value >= flatItems.value.length - 1 ? 0 : activeIndex.value + 1
}

function handleKeydown(event: KeyboardEvent) {
  if (!props.visible) {
    return
  }

  if (event.key === 'Escape') {
    event.preventDefault()
    close()
    return
  }

  if (event.key === 'ArrowUp') {
    event.preventDefault()
    selectPrevious()
    return
  }

  if (event.key === 'ArrowDown') {
    event.preventDefault()
    selectNext()
    return
  }

  if (event.key === 'Enter') {
    event.preventDefault()
    void executeActiveItem()
  }
}

function isActiveItem(item: QuickSwitcherItem) {
  return flatItems.value[activeIndex.value]?.id === item.id
}

function activateItem(itemId: string) {
  const nextIndex = flatItems.value.findIndex((candidate) => candidate.id === itemId)

  if (nextIndex >= 0) {
    activeIndex.value = nextIndex
  }
}
</script>

<template>
  <teleport to="body">
    <transition name="quick-switcher-fade">
      <div
        v-if="visible"
        class="quick-switcher"
        @click="close"
        @keydown.capture="handleKeydown"
      >
        <div class="quick-switcher__panel" @click.stop>
          <div class="quick-switcher__search">
            <div class="quick-switcher__search-copy">
              <span class="section-kicker">Quick Switcher</span>
              <strong>快速切换笔记、文件夹、页面或命令</strong>
            </div>

            <div class="quick-switcher__search-input-wrap">
              <input
                ref="inputRef"
                v-model="query"
                class="quick-switcher__search-input"
                type="search"
                placeholder="输入标题、标签、文件夹名，或直接输入 2026-04-22 这样的日期"
              />
              <button type="button" class="quick-switcher__close" @click="close">Esc</button>
            </div>
          </div>

          <div class="quick-switcher__content">
            <div v-if="loading" class="quick-switcher__empty">
              <strong>正在准备工作区内容</strong>
              <span>会把最近笔记、文件夹和工作区标签一起整理进这里。</span>
            </div>

            <template v-else-if="groupedItems.length">
              <section
                v-for="group in groupedItems"
                :key="group.label"
                class="quick-switcher__group"
              >
                <div class="quick-switcher__group-head">
                  <span>{{ group.label }}</span>
                  <small>{{ group.items.length }}</small>
                </div>

                <div class="quick-switcher__group-list">
                  <button
                    v-for="item in group.items"
                    :key="item.id"
                    type="button"
                    class="quick-switcher__item"
                    :class="[
                      `quick-switcher__item--${item.tone}`,
                      {
                        'quick-switcher__item--active': isActiveItem(item)
                      }
                    ]"
                    @mouseenter="activateItem(item.id)"
                    @click="executeItem(item)"
                  >
                    <div class="quick-switcher__item-main">
                      <div class="quick-switcher__item-copy">
                        <strong>{{ item.title }}</strong>
                        <span>{{ item.subtitle }}</span>
                      </div>

                      <div class="quick-switcher__item-side">
                        <small v-if="item.meta">{{ item.meta }}</small>
                        <span v-if="item.badge" class="quick-switcher__badge">{{ item.badge }}</span>
                      </div>
                    </div>
                  </button>
                </div>
              </section>
            </template>

            <div v-else class="quick-switcher__empty">
              <strong>没有找到匹配项</strong>
              <span>可以试试标题、标签、文件夹名，或者直接输入一个日期来打开对应的每日日记。</span>
            </div>
          </div>

          <footer class="quick-switcher__footer">
            <span>↑ ↓ 切换</span>
            <span>Enter 打开</span>
            <span>Esc 关闭</span>
            <span>支持 `#标签` 和 `YYYY-MM-DD`</span>
          </footer>
        </div>
      </div>
    </transition>
  </teleport>
</template>

<style scoped>
.quick-switcher {
  position: fixed;
  inset: 0;
  z-index: 80;
  display: grid;
  align-items: start;
  justify-items: center;
  padding: 8vh 20px 24px;
  background: rgba(18, 23, 26, 0.42);
  backdrop-filter: blur(16px);
}

.quick-switcher__panel {
  width: min(860px, 100%);
  max-height: 78vh;
  display: grid;
  grid-template-rows: auto minmax(0, 1fr) auto;
  overflow: hidden;
  border: 1px solid rgba(184, 92, 56, 0.16);
  border-radius: 28px;
  background:
    radial-gradient(circle at top left, rgba(197, 157, 88, 0.14), transparent 34%),
    linear-gradient(180deg, rgba(255, 251, 245, 0.96), rgba(248, 241, 232, 0.94));
  box-shadow:
    0 30px 80px rgba(18, 23, 26, 0.24),
    inset 0 1px 0 rgba(255, 255, 255, 0.6);
}

.quick-switcher__search,
.quick-switcher__footer {
  padding: 22px 24px;
}

.quick-switcher__search {
  display: grid;
  gap: 16px;
  border-bottom: 1px solid rgba(184, 92, 56, 0.12);
}

.quick-switcher__search-copy {
  display: grid;
  gap: 6px;
}

.quick-switcher__search-copy strong {
  font-family: var(--header-font);
  font-size: 1.32rem;
}

.quick-switcher__search-input-wrap {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: 12px;
  align-items: center;
}

.quick-switcher__search-input {
  width: 100%;
  min-height: 54px;
  padding: 0 18px;
  border: 1px solid rgba(184, 92, 56, 0.16);
  border-radius: 18px;
  background: rgba(255, 255, 255, 0.84);
  outline: none;
  font-size: 1rem;
  transition: border-color 0.2s ease, box-shadow 0.2s ease;
}

.quick-switcher__search-input:focus {
  border-color: rgba(184, 92, 56, 0.34);
  box-shadow: 0 0 0 4px rgba(184, 92, 56, 0.08);
}

.quick-switcher__close {
  min-height: 42px;
  padding: 0 16px;
  border: 1px solid rgba(184, 92, 56, 0.16);
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.8);
  color: var(--text-soft);
  cursor: pointer;
}

.quick-switcher__content {
  display: grid;
  gap: 18px;
  padding: 20px 24px 16px;
  overflow-y: auto;
}

.quick-switcher__group {
  display: grid;
  gap: 10px;
}

.quick-switcher__group-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  color: var(--text-soft);
  font-size: 0.84rem;
}

.quick-switcher__group-head span {
  letter-spacing: 0.08em;
}

.quick-switcher__group-list {
  display: grid;
  gap: 10px;
}

.quick-switcher__item {
  width: 100%;
  padding: 16px 18px;
  border: 1px solid rgba(184, 92, 56, 0.12);
  border-radius: 20px;
  background: rgba(255, 255, 255, 0.72);
  color: inherit;
  text-align: left;
  cursor: pointer;
  transition:
    transform 0.2s ease,
    border-color 0.2s ease,
    background-color 0.2s ease,
    box-shadow 0.2s ease;
}

.quick-switcher__item:hover,
.quick-switcher__item--active {
  transform: translateY(-1px);
  border-color: rgba(184, 92, 56, 0.28);
  background: rgba(255, 255, 255, 0.94);
  box-shadow: 0 14px 28px rgba(141, 69, 41, 0.08);
}

.quick-switcher__item--action {
  border-left: 4px solid rgba(184, 92, 56, 0.38);
}

.quick-switcher__item--workspace {
  border-left: 4px solid rgba(54, 92, 75, 0.34);
}

.quick-switcher__item--note {
  border-left: 4px solid rgba(197, 157, 88, 0.42);
}

.quick-switcher__item--folder {
  border-left: 4px solid rgba(125, 143, 105, 0.42);
}

.quick-switcher__item--daily {
  border-left: 4px solid rgba(109, 82, 160, 0.34);
}

.quick-switcher__item--template {
  border-left: 4px solid rgba(68, 137, 111, 0.4);
}

.quick-switcher__item-main {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
}

.quick-switcher__item-copy {
  display: grid;
  gap: 6px;
}

.quick-switcher__item-copy strong {
  font-size: 1rem;
}

.quick-switcher__item-copy span,
.quick-switcher__item-side small,
.quick-switcher__empty span,
.quick-switcher__footer {
  color: var(--text-soft);
}

.quick-switcher__item-side {
  display: grid;
  justify-items: end;
  gap: 8px;
}

.quick-switcher__badge {
  display: inline-flex;
  align-items: center;
  min-height: 28px;
  padding: 0 10px;
  border-radius: 999px;
  background: rgba(184, 92, 56, 0.1);
  color: #8d4529;
  font-size: 0.76rem;
  white-space: nowrap;
}

.quick-switcher__empty {
  display: grid;
  gap: 8px;
  padding: 30px 12px;
  text-align: center;
}

.quick-switcher__empty strong {
  font-size: 1.08rem;
}

.quick-switcher__footer {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 10px 16px;
  border-top: 1px solid rgba(184, 92, 56, 0.12);
  font-size: 0.84rem;
}

.quick-switcher-fade-enter-active,
.quick-switcher-fade-leave-active {
  transition: opacity 0.2s ease;
}

.quick-switcher-fade-enter-from,
.quick-switcher-fade-leave-to {
  opacity: 0;
}

@media (max-width: 900px) {
  .quick-switcher {
    padding: 5vh 14px 16px;
  }

  .quick-switcher__panel {
    max-height: 84vh;
    border-radius: 24px;
  }

  .quick-switcher__search-input-wrap,
  .quick-switcher__item-main {
    grid-template-columns: 1fr;
  }

  .quick-switcher__item-side {
    justify-items: start;
  }
}

@media (max-width: 520px) {
  .quick-switcher {
    padding: 12px 10px;
  }

  .quick-switcher__panel {
    max-height: calc(100dvh - 24px);
    border-radius: 22px;
  }

  .quick-switcher__search,
  .quick-switcher__footer {
    padding: 16px;
  }

  .quick-switcher__content {
    gap: 14px;
    padding: 14px 16px;
  }

  .quick-switcher__search-input-wrap {
    grid-template-columns: 1fr;
  }

  .quick-switcher__search-input {
    min-height: 48px;
  }

  .quick-switcher__close {
    width: 100%;
  }

  .quick-switcher__item {
    padding: 14px;
    border-radius: 18px;
  }

  .quick-switcher__footer {
    gap: 8px 12px;
    font-size: 0.78rem;
  }
}
</style>
