<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useRouter } from 'vue-router'
import CollapsiblePanel from '@/components/CollapsiblePanel.vue'
import FolderTreePanel from '@/components/FolderTreePanel.vue'
import TagCloudPanel from '@/components/TagCloudPanel.vue'
import type { Note } from '@/types'
import { useWorkspaceStore } from '@/stores/workspace'
import { findFolderName, formatDateTime, relativeTime } from '@/utils/format'
import { buildDraftNoteRoute } from '@/utils/noteWorkspace'

const router = useRouter()
const workspaceStore = useWorkspaceStore()

const activePane = ref<'notes' | 'favorites' | 'trash'>('notes')
const favoriteSubmittingId = ref<number | null>(null)
const folderTreeInitiallyOpen = false

onMounted(() => {
  if (!workspaceStore.notes.length || !workspaceStore.folders.length) {
    void workspaceStore.loadExplorer()
  }
})

const currentFolderName = computed(() =>
  workspaceStore.activeFolderId
    ? findFolderName(workspaceStore.folders, workspaceStore.activeFolderId) || '当前文件夹'
    : '全部笔记'
)

const notes = computed(() => workspaceStore.visibleNotes)
const favoriteNotes = computed(() => workspaceStore.favoriteNotes)
const trashNotes = computed(() => workspaceStore.visibleTrashNotes)
const activePaneSummary = computed(() => {
  if (activePane.value === 'favorites') {
    return favoriteNotes.value.length ? `已收藏 ${favoriteNotes.value.length} 篇常用笔记。` : '收藏区为空，可以先从笔记卡片里收藏常用内容。'
  }

  if (activePane.value === 'trash') {
    return trashNotes.value.length ? `回收站有 ${trashNotes.value.length} 篇可恢复笔记。` : '回收站为空，不需要处理。'
  }

  return notes.value.length ? `当前范围内有 ${notes.value.length} 篇笔记。` : '当前范围还没有笔记，可以先新建草稿。'
})
const activePaneTitle = computed(() => {
  if (activePane.value === 'favorites') {
    return '我的收藏'
  }

  if (activePane.value === 'trash') {
    return '回收站'
  }

  return '笔记列表'
})

function buildExcerpt(content: string) {
  const normalized = content.replace(/[#>*`~-]/g, ' ').replace(/\n+/g, ' ').trim()
  return normalized.slice(0, 120) || '这篇笔记还没有摘要，打开后继续补充内容。'
}

async function refreshExplorer() {
  try {
    await workspaceStore.loadExplorer()
    ElMessage.success('内容已刷新')
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '刷新失败，请稍后重试')
  }
}

async function handleCreateFolder(payload: { name: string; parentId: number | null }) {
  try {
    await workspaceStore.createFolder(payload)
    ElMessage.success('文件夹已创建')
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '创建文件夹失败')
  }
}

async function handleRenameFolder(payload: { id: number; name: string; parentId: number | null }) {
  try {
    await workspaceStore.renameFolder(payload.id, {
      name: payload.name,
      parentId: payload.parentId
    })
    ElMessage.success('文件夹已更新')
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '更新文件夹失败')
  }
}

async function handleDeleteFolder(folderId: number) {
  try {
    await workspaceStore.removeFolder(folderId)
    ElMessage.success('文件夹已删除')
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '删除文件夹失败')
  }
}

function createNote() {
  void router.push(buildDraftNoteRoute())
}

function openImportCenter() {
  void router.push('/import')
}

function openNote(noteId: number) {
  void router.push(`/note/${noteId}/edit`)
}

function getNoteActionLabel(note: Note) {
  return note.currentUserPermission === 'READ' ? '查看' : '编辑'
}

async function toggleFavorite(note: Note) {
  const nextFavorited = !note.favorited
  favoriteSubmittingId.value = note.id

  try {
    await workspaceStore.toggleFavorite(note.id, nextFavorited)
    ElMessage.success(nextFavorited ? '已加入收藏' : '已取消收藏')
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '更新收藏状态失败')
  } finally {
    favoriteSubmittingId.value = null
  }
}

async function deleteNote(noteId: number) {
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

    await workspaceStore.removeNote(noteId)
    activePane.value = 'trash'
    ElMessage.success('笔记已移入回收站')
  } catch (error) {
    if (error instanceof Error) {
      ElMessage.error(error.message)
    }
  }
}

async function restoreNote(noteId: number) {
  try {
    await workspaceStore.restoreNoteFromTrash(noteId)
    activePane.value = 'notes'
    ElMessage.success('笔记已恢复')
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '恢复笔记失败')
  }
}

async function permanentlyDeleteNote(noteId: number) {
  try {
    await ElMessageBox.confirm(
      '永久删除后将无法找回，关联的版本、分享和协作记录也会一并移除。是否继续？',
      '永久删除',
      {
        type: 'warning',
        confirmButtonText: '永久删除',
        cancelButtonText: '取消'
      }
    )

    await workspaceStore.permanentlyDeleteNote(noteId)
    ElMessage.success('笔记已永久删除')
  } catch (error) {
    if (error instanceof Error) {
      ElMessage.error(error.message)
    }
  }
}

function searchByTag(tag: string) {
  void router.push({
    path: '/search',
    query: {
      tag
    }
  })
}
</script>

<template>
  <div class="folder-view page-shell">
    <section class="folder-focus panel">
      <div class="folder-focus__copy">
        <span class="section-kicker">Notebook</span>
        <h2>{{ currentFolderName }}</h2>
        <p>{{ activePaneSummary }}</p>
      </div>

      <div class="folder-focus__actions">
        <el-button type="primary" @click="createNote">新建笔记</el-button>
        <el-button plain @click="openImportCenter">导入资料</el-button>
        <el-button plain @click="refreshExplorer">刷新</el-button>
      </div>

      <div class="folder-focus__stats" aria-label="笔记状态">
        <button type="button" :class="{ 'is-active': activePane === 'notes' }" @click="activePane = 'notes'">
          <strong>{{ notes.length }}</strong>
          <span>笔记</span>
        </button>
        <button type="button" :class="{ 'is-active': activePane === 'favorites' }" @click="activePane = 'favorites'">
          <strong>{{ favoriteNotes.length }}</strong>
          <span>收藏</span>
        </button>
        <button type="button" :class="{ 'is-active': activePane === 'trash' }" @click="activePane = 'trash'">
          <strong>{{ trashNotes.length }}</strong>
          <span>回收站</span>
        </button>
      </div>
    </section>

    <div class="folder-view__layout">
      <aside class="folder-view__aside">
        <FolderTreePanel
          :folders="workspaceStore.folders"
          :current-folder-id="workspaceStore.activeFolderId"
          :loading="workspaceStore.explorerLoading"
          :initially-open="folderTreeInitiallyOpen"
          @select="workspaceStore.selectFolder"
          @create="handleCreateFolder"
          @rename="handleRenameFolder"
          @delete="handleDeleteFolder"
        />

        <TagCloudPanel :tags="workspaceStore.tagBuckets" :initially-open="false" @select="searchByTag" />
      </aside>

      <CollapsiblePanel
        class="folder-view__content"
        kicker="Notebook"
        :title="activePaneTitle"
        :meta="`正常笔记 ${notes.length} 篇 / 收藏 ${favoriteNotes.length} 篇 / 回收站 ${trashNotes.length} 篇`"
        body-class="folder-view__content-body"
        :initially-open="true"
      >
        <el-tabs v-model="activePane" class="folder-view__tabs">
          <el-tab-pane label="笔记列表" name="notes">
            <div v-if="notes.length" class="note-grid">
              <article v-for="note in notes" :key="note.id" class="note-card">
                <div class="note-card__top">
                  <div>
                    <strong>{{ note.title }}</strong>
                    <span>{{ note.folderName || '未归档文件夹' }}</span>
                  </div>
                  <small>{{ relativeTime(note.updateTime) }}</small>
                </div>

                <p>{{ buildExcerpt(note.content) }}</p>

                <div class="note-card__tags">
                  <button v-for="tag in note.tags" :key="tag" @click="searchByTag(tag)">#{{ tag }}</button>
                </div>

                <div class="note-card__footer">
                  <small>最后更新：{{ formatDateTime(note.updateTime) }}</small>
                  <div class="note-card__actions">
                    <el-button plain size="small" @click="openNote(note.id)">{{ getNoteActionLabel(note) }}</el-button>
                    <el-button
                      plain
                      size="small"
                      :loading="favoriteSubmittingId === note.id"
                      @click="toggleFavorite(note)"
                    >
                      {{ note.favorited ? '取消收藏' : '收藏' }}
                    </el-button>
                    <el-button plain size="small" @click="deleteNote(note.id)">移入回收站</el-button>
                  </div>
                </div>
              </article>
            </div>

            <div v-else class="empty-state">
              <strong>当前目录下还没有笔记</strong>
              <span>点击“新建笔记”会先打开一个草稿标签，你可以写完再决定是否保存到数据库。</span>
            </div>
          </el-tab-pane>

          <el-tab-pane label="我的收藏" name="favorites">
            <div v-if="favoriteNotes.length" class="note-grid">
              <article v-for="note in favoriteNotes" :key="note.id" class="note-card note-card--favorite">
                <div class="note-card__top">
                  <div>
                    <strong>{{ note.title }}</strong>
                    <span>{{ note.folderName || '未归档文件夹' }}</span>
                  </div>
                  <small>{{ relativeTime(note.favoriteTime || note.updateTime) }}</small>
                </div>

                <p>{{ buildExcerpt(note.content) }}</p>

                <div class="note-card__tags">
                  <button v-for="tag in note.tags" :key="tag" @click="searchByTag(tag)">#{{ tag }}</button>
                </div>

                <div class="note-card__footer">
                  <small>收藏时间：{{ formatDateTime(note.favoriteTime || note.updateTime) }}</small>
                  <div class="note-card__actions">
                    <el-button plain size="small" @click="openNote(note.id)">{{ getNoteActionLabel(note) }}</el-button>
                    <el-button
                      plain
                      size="small"
                      :loading="favoriteSubmittingId === note.id"
                      @click="toggleFavorite(note)"
                    >
                      取消收藏
                    </el-button>
                  </div>
                </div>
              </article>
            </div>

            <div v-else class="empty-state">
              <strong>还没有收藏的笔记</strong>
              <span>在笔记列表或编辑页点“收藏”后，这里会集中展示你常用的内容。</span>
            </div>
          </el-tab-pane>

          <el-tab-pane label="回收站" name="trash">
            <div v-if="trashNotes.length" class="note-grid">
              <article v-for="note in trashNotes" :key="note.id" class="note-card note-card--trash">
                <div class="note-card__top">
                  <div>
                    <strong>{{ note.title }}</strong>
                    <span>{{ note.folderName || '未归档文件夹' }}</span>
                  </div>
                  <small>{{ relativeTime(note.deletedTime || note.updateTime) }}</small>
                </div>

                <p>{{ buildExcerpt(note.content) }}</p>

                <div class="note-card__meta">
                  <span class="pill pill--warning">已删除</span>
                  <small>删除时间：{{ formatDateTime(note.deletedTime) }}</small>
                </div>

                <div class="note-card__footer">
                  <small>永久删除会清空版本、分享和协作记录</small>
                  <div class="note-card__actions">
                    <el-button plain size="small" @click="restoreNote(note.id)">恢复</el-button>
                    <el-button plain size="small" @click="permanentlyDeleteNote(note.id)">永久删除</el-button>
                  </div>
                </div>
              </article>
            </div>

            <div v-else class="empty-state">
              <strong>回收站目前是空的</strong>
              <span>删除的笔记会先在这里暂存，方便你随时恢复。</span>
            </div>
          </el-tab-pane>
        </el-tabs>
      </CollapsiblePanel>
    </div>
  </div>
</template>

<style scoped>
.folder-focus {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: 12px;
  align-items: center;
  padding: 16px;
  background:
    radial-gradient(circle at top right, rgba(54, 92, 75, 0.12), transparent 32%),
    linear-gradient(135deg, rgba(255, 252, 247, 0.96), rgba(248, 241, 232, 0.88));
}

.folder-focus__copy {
  min-width: 0;
}

.folder-focus__copy h2 {
  overflow: hidden;
  margin: 4px 0 0;
  font-family: var(--header-font);
  font-size: clamp(1.35rem, 2.4vw, 2rem);
  text-overflow: ellipsis;
  white-space: nowrap;
}

.folder-focus__copy p {
  margin: 6px 0 0;
  color: var(--text-soft);
  line-height: 1.5;
}

.folder-focus__actions {
  display: flex;
  flex-wrap: wrap;
  justify-content: flex-end;
  gap: 8px;
}

.folder-focus__stats {
  display: grid;
  grid-column: 1 / -1;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 8px;
}

.folder-focus__stats button {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  min-width: 0;
  padding: 10px 12px;
  border: 1px solid rgba(184, 92, 56, 0.12);
  border-radius: 14px;
  background: rgba(255, 255, 255, 0.56);
  color: var(--text-soft);
  cursor: pointer;
  transition: border-color 0.2s ease, background-color 0.2s ease, transform 0.2s ease;
}

.folder-focus__stats button:hover,
.folder-focus__stats button.is-active {
  border-color: rgba(141, 69, 41, 0.32);
  background: rgba(184, 92, 56, 0.09);
  color: #8d4529;
  transform: translateY(-1px);
}

.folder-focus__stats strong {
  color: var(--text);
  font-size: 1.1rem;
}

.folder-view__layout {
  display: grid;
  gap: 16px;
  grid-template-columns: 300px minmax(0, 1fr);
}

.folder-view__aside {
  display: grid;
  align-content: start;
  gap: 16px;
}

.folder-view__content {
  padding: 16px;
}

.folder-view__content-body {
  display: grid;
}

.folder-view__tabs :deep(.el-tabs__header) {
  display: none;
}

.note-grid {
  display: grid;
  gap: 12px;
  grid-template-columns: repeat(2, minmax(0, 1fr));
}

.note-card {
  display: grid;
  gap: 10px;
  padding: 14px;
  border: 1px solid rgba(184, 92, 56, 0.14);
  border-radius: 16px;
  background: rgba(255, 255, 255, 0.54);
}

.note-card--trash {
  background:
    linear-gradient(180deg, rgba(255, 246, 240, 0.95), rgba(255, 255, 255, 0.72));
  border-color: rgba(184, 92, 56, 0.22);
}

.note-card--favorite {
  background:
    linear-gradient(180deg, rgba(255, 251, 240, 0.96), rgba(255, 255, 255, 0.76));
  border-color: rgba(197, 157, 88, 0.24);
}

.note-card__top,
.note-card__footer,
.note-card__meta {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 10px;
}

.note-card__top strong {
  display: block;
  font-size: 1.05rem;
}

.note-card__top span,
.note-card__top small,
.note-card__footer small,
.note-card__meta small {
  color: var(--text-soft);
}

.note-card p {
  margin: 0;
  color: var(--text-soft);
  line-height: 1.55;
}

.note-card__tags {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.note-card__tags button {
  padding: 6px 9px;
  border: 1px solid rgba(184, 92, 56, 0.16);
  border-radius: 999px;
  background: rgba(184, 92, 56, 0.06);
  color: var(--accent-strong);
  cursor: pointer;
}

.note-card__actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.pill--warning {
  background: rgba(184, 92, 56, 0.1);
  color: var(--accent-strong);
}

@media (max-width: 1180px) {
  .folder-view__layout {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 820px) {
  .folder-focus {
    grid-template-columns: 1fr;
  }

  .folder-focus__actions {
    justify-content: flex-start;
  }

  .note-grid {
    grid-template-columns: 1fr;
  }

  .note-card__top,
  .note-card__footer,
  .note-card__meta {
    flex-direction: column;
    align-items: flex-start;
  }
}

@media (max-width: 640px) {
  .folder-focus {
    gap: 10px;
    padding: 10px;
  }

  .folder-focus__copy p {
    display: none;
  }

  .folder-focus__actions {
    align-items: stretch;
  }

  .folder-focus__actions :deep(.el-button) {
    flex: 1 1 112px;
    min-width: 0;
  }

  .folder-focus__stats {
    display: flex;
    grid-template-columns: none;
    gap: 8px;
    overflow-x: auto;
    padding-bottom: 2px;
    scrollbar-width: none;
  }

  .folder-focus__stats::-webkit-scrollbar {
    display: none;
  }

  .folder-focus__stats button {
    flex: 0 0 112px;
    padding: 8px 10px;
  }

  .folder-view__layout,
  .folder-view__aside {
    gap: 12px;
  }

  .folder-view__content {
    order: 1;
  }

  .folder-view__aside {
    order: 2;
  }

  .folder-view__content {
    padding: 10px;
  }

  .note-grid {
    gap: 10px;
  }

  .note-card {
    gap: 10px;
    padding: 10px;
    border-radius: 16px;
  }

  .note-card p {
    display: none;
  }

  .note-card__footer > small {
    display: none;
  }

  .note-card__tags {
    overflow-x: auto;
    flex-wrap: nowrap;
    scrollbar-width: none;
  }

  .note-card__tags::-webkit-scrollbar {
    display: none;
  }

  .note-card__actions {
    width: 100%;
    align-items: stretch;
  }

  .note-card__actions :deep(.el-button) {
    flex: 1 1 120px;
    min-width: 0;
  }
}

@media (max-width: 420px) {
  .folder-view__content {
    padding: 10px;
  }

  .note-card {
    padding: 10px;
  }

  .note-card__actions :deep(.el-button) {
    flex: 1 1 96px;
  }
}
</style>
