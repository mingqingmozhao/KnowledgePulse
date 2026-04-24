<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useRouter } from 'vue-router'
import PageHero from '@/components/PageHero.vue'
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
const compactFolderView = typeof window !== 'undefined' && window.matchMedia('(max-width: 640px)').matches
const folderTreeInitiallyOpen = !compactFolderView

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
    <PageHero
      kicker="Explorer"
      title="文件树与笔记库"
      description="在这里统一管理目录、收藏与回收站。现在从这里新建笔记，会直接进入草稿工作区标签，不会先生成空白占位笔记。"
    >
      <template #actions>
        <el-button type="primary" @click="createNote">新建笔记</el-button>
        <el-button plain @click="openImportCenter">导入 Markdown</el-button>
        <el-button plain @click="refreshExplorer">刷新内容</el-button>
      </template>
    </PageHero>

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
        :title="currentFolderName"
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
.folder-view__layout {
  display: grid;
  gap: 24px;
  grid-template-columns: 320px minmax(0, 1fr);
}

.folder-view__aside {
  display: grid;
  align-content: start;
  gap: 24px;
}

.folder-view__content {
  padding: 24px;
}

.folder-view__content-body {
  display: grid;
}

.folder-view__tabs :deep(.el-tabs__header) {
  margin-bottom: 18px;
}

.note-grid {
  display: grid;
  gap: 18px;
  grid-template-columns: repeat(2, minmax(0, 1fr));
}

.note-card {
  display: grid;
  gap: 14px;
  padding: 20px;
  border: 1px solid rgba(184, 92, 56, 0.14);
  border-radius: 20px;
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
  gap: 16px;
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
  line-height: 1.8;
}

.note-card__tags {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}

.note-card__tags button {
  padding: 8px 12px;
  border: 1px solid rgba(184, 92, 56, 0.16);
  border-radius: 999px;
  background: rgba(184, 92, 56, 0.06);
  color: var(--accent-strong);
  cursor: pointer;
}

.note-card__actions {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
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
  .folder-view__layout,
  .folder-view__aside {
    gap: 16px;
  }

  .folder-view__content {
    order: 1;
  }

  .folder-view__aside {
    order: 2;
  }

  .folder-view__content {
    padding: 16px;
  }

  .note-grid {
    gap: 14px;
  }

  .note-card {
    gap: 12px;
    padding: 16px;
    border-radius: 18px;
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
    padding: 14px;
  }

  .note-card {
    padding: 14px;
  }

  .note-card__actions :deep(.el-button) {
    flex-basis: 100%;
  }
}
</style>
