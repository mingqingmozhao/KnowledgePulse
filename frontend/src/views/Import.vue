<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { useRouter } from 'vue-router'
import PageHero from '@/components/PageHero.vue'
import { importMarkdownFiles, type ImportableFile } from '@/api/import'
import { useWorkspaceStore } from '@/stores/workspace'
import type { ImportMode, ImportResponse } from '@/types'

type ModeCard = {
  value: ImportMode
  title: string
  kicker: string
  description: string
  buttonText: string
}

const router = useRouter()
const workspaceStore = useWorkspaceStore()

const directoryInputRef = ref<HTMLInputElement | null>(null)
const batchInputRef = ref<HTMLInputElement | null>(null)
const mode = ref<ImportMode>('OBSIDIAN_VAULT')
const selectedFiles = ref<ImportableFile[]>([])
const rootFolderName = ref('Obsidian 导入')
const targetFolderId = ref<number | null>(null)
const importing = ref(false)
const result = ref<ImportResponse | null>(null)

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
  }
]

const activeModeCard = computed(() => modeCards.find((item) => item.value === mode.value) ?? modeCards[0])
const markdownFiles = computed(() => selectedFiles.value.filter((file) => isMarkdownPath(relativePathOf(file))))
const skippedClientCount = computed(() => selectedFiles.value.length - markdownFiles.value.length)
const totalSize = computed(() => markdownFiles.value.reduce((sum, file) => sum + file.size, 0))
const commonTopDirectory = computed(() => detectCommonTopDirectory(markdownFiles.value))
const directoryCount = computed(() => {
  const directories = new Set<string>()

  markdownFiles.value.forEach((file) => {
    const segments = logicalSegments(relativePathOf(file))
    segments.pop()

    if (segments.length) {
      directories.add(segments.join('/'))
    }
  })

  return directories.size
})
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
const canImport = computed(() => markdownFiles.value.length > 0 && rootFolderName.value.trim().length > 0 && !importing.value)
const pageDescription = computed(() => {
  if (!markdownFiles.value.length) {
    return '把 Markdown 文件夹、Obsidian vault 或多篇 Markdown 一次性导入，目录、标题和标签会自动对齐到系统里。'
  }

  return `已准备 ${markdownFiles.value.length} 个 Markdown 文件，导入后会放入「${rootFolderName.value || '新的导入文件夹'}」。`
})

onMounted(() => {
  if (!workspaceStore.folders.length) {
    void workspaceStore.loadFolders()
  }
})

function selectMode(nextMode: ImportMode) {
  mode.value = nextMode

  if (!selectedFiles.value.length) {
    rootFolderName.value = fallbackRootName()
  }
}

function triggerPicker(nextMode: ImportMode) {
  selectMode(nextMode)

  if (nextMode === 'BATCH_MARKDOWN') {
    batchInputRef.value?.click()
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

  mode.value = 'BATCH_MARKDOWN'
  applyFiles(files)
}

function applyFiles(files: ImportableFile[]) {
  selectedFiles.value = files
  result.value = null
  rootFolderName.value = inferRootName(files)

  if (!files.length) {
    return
  }

  const validCount = files.filter((file) => isMarkdownPath(relativePathOf(file))).length
  if (validCount === 0) {
    ElMessage.warning('没有找到 .md 或 .markdown 文件')
    return
  }

  if (validCount !== files.length) {
    ElMessage.info(`已自动忽略 ${files.length - validCount} 个非 Markdown 文件`)
  }
}

async function submitImport() {
  if (!canImport.value) {
    ElMessage.warning('请先选择 Markdown 文件，并填写导入后的根文件夹名称')
    return
  }

  importing.value = true

  try {
    result.value = await importMarkdownFiles({
      files: markdownFiles.value,
      mode: mode.value,
      rootFolderName: rootFolderName.value,
      targetFolderId: targetFolderId.value
    })
    await workspaceStore.loadExplorer()
    ElMessage.success(`导入完成：新增 ${result.value.importedNotes} 篇笔记`)
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '导入失败，请稍后重试')
  } finally {
    importing.value = false
  }
}

function clearSelection() {
  selectedFiles.value = []
  result.value = null
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

function relativePathOf(file: ImportableFile) {
  return file.webkitRelativePath || file.name
}

function isMarkdownPath(path: string) {
  const normalized = path.toLowerCase()
  return normalized.endsWith('.md') || normalized.endsWith('.markdown')
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
          开始导入
        </el-button>
        <el-button plain @click="clearSelection">清空选择</el-button>
      </template>
    </PageHero>

    <input
      ref="directoryInputRef"
      class="import-center__input"
      type="file"
      accept=".md,.markdown,text/markdown"
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
            {{
              mode === 'BATCH_MARKDOWN'
                ? '也可以把多个 Markdown 文件拖到这里。'
                : '目录导入建议使用下方按钮选择文件夹，浏览器会保留相对路径。'
            }}
          </p>
        </div>

        <div class="import-dropzone__actions">
          <el-button type="primary" size="large" @click="triggerPicker(mode)">
            {{ activeModeCard.buttonText }}
          </el-button>
          <el-button plain size="large" @click="triggerPicker('BATCH_MARKDOWN')">只选文件</el-button>
        </div>

        <div class="import-dropzone__stats">
          <article>
            <span>Markdown</span>
            <strong>{{ markdownFiles.length }}</strong>
          </article>
          <article>
            <span>目录层级</span>
            <strong>{{ directoryCount }}</strong>
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
        <span class="section-kicker">Import Settings</span>
        <h3>导入设置</h3>

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
          <strong>会自动处理</strong>
          <span>一级导入文件夹、子目录、H1 标题、front matter 标签和正文里的 #标签。</span>
        </div>

        <el-button type="primary" :loading="importing" :disabled="!canImport" @click="submitImport">
          导入 {{ markdownFiles.length || '' }} 篇笔记
        </el-button>
      </aside>
    </section>

    <section v-if="markdownFiles.length" class="import-preview panel">
      <div class="import-preview__head">
        <div>
          <span class="section-kicker">Preview</span>
          <h2>导入预览</h2>
        </div>
        <small>只展示前 {{ previewFiles.length }} 个文件，完整目录会在后端按相对路径导入。</small>
      </div>

      <div class="import-preview__groups">
        <article v-for="group in previewGroups" :key="group.name">
          <strong>{{ group.name }}</strong>
          <span>{{ group.count }} 篇</span>
        </article>
      </div>

      <div class="import-preview__files">
        <span v-for="file in previewFiles" :key="relativePathOf(file)">
          {{ relativePathOf(file) }}
        </span>
      </div>
    </section>

    <section v-if="result" class="import-result panel">
      <div class="import-result__summary">
        <span class="section-kicker">Import Finished</span>
        <h2>导入完成</h2>
        <p>
          已导入 {{ result.importedNotes }} 篇笔记，创建 {{ result.createdFolders }} 个文件夹，
          {{ result.skippedFiles }} 个文件被忽略。
        </p>
      </div>

      <div class="import-result__actions">
        <el-button type="primary" @click="openImportedFolder">查看导入文件夹</el-button>
        <el-button plain :disabled="!result.notes.length" @click="openFirstImportedNote">打开第一篇笔记</el-button>
      </div>

      <div v-if="result.tags.length" class="import-result__tags">
        <span v-for="tag in result.tags.slice(0, 18)" :key="tag">#{{ tag }}</span>
      </div>

      <div v-if="result.warnings.length" class="import-result__warnings">
        <strong>需要留意</strong>
        <span v-for="warning in result.warnings" :key="warning">{{ warning }}</span>
      </div>
    </section>
  </div>
</template>

<style scoped>
.import-center {
  display: grid;
  gap: 24px;
}

.import-center__input {
  display: none;
}

.import-center__modes {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 16px;
}

.import-mode-card {
  display: grid;
  gap: 10px;
  padding: 20px;
  border: 1px solid rgba(184, 92, 56, 0.12);
  border-radius: 26px;
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
  transform: translateY(-2px);
  border-color: rgba(184, 92, 56, 0.28);
  background: rgba(255, 255, 255, 0.86);
  box-shadow: 0 18px 40px rgba(141, 69, 41, 0.08);
}

.import-mode-card span,
.import-mode-card em {
  color: var(--accent-strong);
  font-size: 0.82rem;
  font-style: normal;
  letter-spacing: 0.06em;
  text-transform: uppercase;
}

.import-mode-card strong {
  font-family: var(--header-font);
  font-size: 1.28rem;
}

.import-mode-card p {
  margin: 0;
  color: var(--text-soft);
  line-height: 1.7;
}

.import-center__workspace {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 340px;
  gap: 20px;
}

.import-dropzone,
.import-settings,
.import-preview,
.import-result {
  padding: 24px;
}

.import-dropzone {
  display: grid;
  gap: 22px;
  min-height: 360px;
  border-style: dashed;
  background:
    radial-gradient(circle at top left, rgba(54, 92, 75, 0.12), transparent 34%),
    linear-gradient(135deg, rgba(255, 255, 255, 0.8), rgba(255, 249, 239, 0.66));
}

.import-dropzone__copy {
  display: grid;
  align-content: center;
  gap: 12px;
}

.import-dropzone__copy h2,
.import-preview h2,
.import-result h2 {
  margin: 0;
  font-family: var(--header-font);
  font-size: clamp(1.8rem, 3vw, 2.5rem);
}

.import-dropzone__copy p,
.import-result p {
  max-width: 680px;
  margin: 0;
  color: var(--text-soft);
  line-height: 1.8;
}

.import-dropzone__actions {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
}

.import-dropzone__stats {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 12px;
}

.import-dropzone__stats article {
  display: grid;
  gap: 6px;
  padding: 16px;
  border-radius: 20px;
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
  font-size: 1.4rem;
}

.import-dropzone__stat--muted strong {
  color: var(--accent-strong);
}

.import-settings {
  display: grid;
  align-content: start;
  gap: 18px;
}

.import-settings h3 {
  margin: 0;
  font-family: var(--header-font);
  font-size: 1.4rem;
}

.import-settings__field {
  display: grid;
  gap: 8px;
}

.import-settings__hint {
  display: grid;
  gap: 6px;
  padding: 16px;
  border-radius: 18px;
  background: rgba(184, 92, 56, 0.08);
  color: var(--text-soft);
  line-height: 1.65;
}

.import-settings__hint strong {
  color: var(--accent-strong);
}

.import-preview,
.import-result {
  display: grid;
  gap: 18px;
}

.import-preview__head,
.import-result {
  align-items: start;
}

.import-preview__head,
.import-result__actions {
  display: flex;
  justify-content: space-between;
  gap: 16px;
  flex-wrap: wrap;
}

.import-preview__groups {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(160px, 1fr));
  gap: 12px;
}

.import-preview__groups article {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  padding: 14px 16px;
  border-radius: 18px;
  background: rgba(54, 92, 75, 0.08);
}

.import-preview__files,
.import-result__tags,
.import-result__warnings {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}

.import-preview__files span,
.import-result__tags span,
.import-result__warnings span {
  display: inline-flex;
  align-items: center;
  min-height: 34px;
  padding: 0 12px;
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.76);
  border: 1px solid rgba(184, 92, 56, 0.12);
  font-size: 0.84rem;
}

.import-result {
  grid-template-columns: minmax(0, 1fr) auto;
  background:
    radial-gradient(circle at top right, rgba(54, 92, 75, 0.12), transparent 34%),
    rgba(255, 255, 255, 0.74);
}

.import-result__tags,
.import-result__warnings {
  grid-column: 1 / -1;
}

.import-result__tags span {
  color: #365c4b;
  background: rgba(54, 92, 75, 0.08);
}

.import-result__warnings {
  display: grid;
  gap: 10px;
  padding: 16px;
  border-radius: 18px;
  background: rgba(184, 92, 56, 0.08);
}

.import-result__warnings strong {
  color: var(--accent-strong);
}

@media (max-width: 1100px) {
  .import-center__modes,
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
    display: flex;
    gap: 10px;
    overflow-x: auto;
    scrollbar-width: none;
  }

  .import-center__modes::-webkit-scrollbar {
    display: none;
  }

  .import-mode-card {
    flex: 0 0 180px;
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
    padding: 14px;
    border-radius: 16px;
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
