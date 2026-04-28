<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { ElMessageBox, type TreeInstance } from 'element-plus'
import CollapsiblePanel from '@/components/CollapsiblePanel.vue'
import type { FolderNode } from '@/types'
import { findFolderById } from '@/utils/format'

const props = withDefaults(
  defineProps<{
    folders: FolderNode[]
    currentFolderId: number | null
    loading?: boolean
    title?: string
    initiallyOpen?: boolean
  }>(),
  {
    loading: false,
    title: '文件夹树',
    initiallyOpen: true
  }
)

const emit = defineEmits<{
  (event: 'select', folderId: number | null): void
  (event: 'create', payload: { name: string; parentId: number | null }): void
  (event: 'rename', payload: { id: number; name: string; parentId: number | null }): void
  (event: 'delete', folderId: number): void
}>()

const treeRef = ref<TreeInstance | null>(null)
const expandedKeys = ref<number[]>([])
const searchKeyword = ref('')

const selectedFolder = computed(() =>
  props.currentFolderId ? findFolderById(props.folders, props.currentFolderId) : null
)

const normalizedSearchKeyword = computed(() => searchKeyword.value.trim().toLowerCase())
const displayFolders = computed(() => filterFolders(props.folders, normalizedSearchKeyword.value))
const allFolderIds = computed(() => collectFolderIds(displayFolders.value))
const totalFolderCount = computed(() => collectFolderIds(props.folders).length)
const visibleFolderCount = computed(() => allFolderIds.value.length)
const canCollapseTree = computed(() => !normalizedSearchKeyword.value && expandedKeys.value.length > 0)

watch(
  () => [props.folders, props.currentFolderId, normalizedSearchKeyword.value] as const,
  () => {
    syncExpandedKeys()
  },
  {
    immediate: true,
    deep: true
  }
)

watch(treeRef, (instance) => {
  if (instance) {
    applyExpandedKeys()
  }
})

function collectFolderIds(folders: FolderNode[]): number[] {
  return folders.flatMap((folder) => [folder.id, ...collectFolderIds(folder.children ?? [])])
}

function filterFolders(folders: FolderNode[], keyword: string): FolderNode[] {
  if (!keyword) {
    return folders
  }

  return folders.flatMap((folder) => {
    const children = filterFolders(folder.children ?? [], keyword)
    const matched = folder.name.toLowerCase().includes(keyword)

    if (!matched && !children.length) {
      return []
    }

    return [
      {
        ...folder,
        children
      }
    ]
  })
}

function findFolderPath(folders: FolderNode[], targetId: number, path: number[] = []): number[] | null {
  for (const folder of folders) {
    const nextPath = [...path, folder.id]

    if (folder.id === targetId) {
      return nextPath
    }

    const nestedPath = findFolderPath(folder.children ?? [], targetId, nextPath)

    if (nestedPath) {
      return nestedPath
    }
  }

  return null
}

function applyExpandedKeys() {
  const store = treeRef.value?.store

  if (!store) {
    return
  }

  const targetKeys = new Set(expandedKeys.value.map((key) => String(key)))

  Object.values(store.nodesMap).forEach((node) => {
    if (targetKeys.has(String(node.key))) {
      node.expand(null, false)
      return
    }

    node.collapse()
  })
}

function syncExpandedKeys() {
  if (normalizedSearchKeyword.value) {
    expandedKeys.value = [...allFolderIds.value]
    applyExpandedKeys()
    return
  }

  const validIds = new Set(allFolderIds.value)
  const nextExpandedKeys = expandedKeys.value.filter((id) => validIds.has(id))
  const currentPath = props.currentFolderId ? findFolderPath(props.folders, props.currentFolderId) ?? [] : []

  currentPath.forEach((id) => {
    if (!nextExpandedKeys.includes(id)) {
      nextExpandedKeys.push(id)
    }
  })

  expandedKeys.value = nextExpandedKeys
  applyExpandedKeys()
}

function expandAll() {
  expandedKeys.value = [...allFolderIds.value]
  applyExpandedKeys()
}

function collapseTree() {
  expandedKeys.value = props.currentFolderId ? findFolderPath(props.folders, props.currentFolderId) ?? [] : []
  applyExpandedKeys()
}

async function handleCreate(parentId: number | null) {
  try {
    const { value } = await ElMessageBox.prompt('请输入文件夹名称', parentId ? '新建子文件夹' : '新建文件夹', {
      inputPattern: /\S+/,
      inputErrorMessage: '名称不能为空',
      confirmButtonText: '创建',
      cancelButtonText: '取消'
    })

    emit('create', {
      name: value.trim(),
      parentId
    })
  } catch {
    return
  }
}

async function handleRename() {
  if (!selectedFolder.value) {
    return
  }

  try {
    const { value } = await ElMessageBox.prompt('修改当前文件夹名称', '重命名文件夹', {
      inputValue: selectedFolder.value.name,
      inputPattern: /\S+/,
      inputErrorMessage: '名称不能为空',
      confirmButtonText: '保存',
      cancelButtonText: '取消'
    })

    emit('rename', {
      id: selectedFolder.value.id,
      name: value.trim(),
      parentId: selectedFolder.value.parentId
    })
  } catch {
    return
  }
}

async function handleDelete() {
  if (!selectedFolder.value) {
    return
  }

  try {
    await ElMessageBox.confirm(
      `确定删除“${selectedFolder.value.name}”吗？这会影响该目录下笔记的归属。`,
      '删除文件夹',
      {
        type: 'warning',
        confirmButtonText: '删除',
        cancelButtonText: '取消'
      }
    )

    emit('delete', selectedFolder.value.id)
  } catch {
    return
  }
}

function handleNodeClick(data: FolderNode) {
  emit('select', data.id)
}

function handleNodeExpand(data: FolderNode) {
  if (expandedKeys.value.includes(data.id)) {
    return
  }

  expandedKeys.value = [...expandedKeys.value, data.id]
}

function handleNodeCollapse(data: FolderNode) {
  expandedKeys.value = expandedKeys.value.filter((id) => id !== data.id)
}
</script>

<template>
  <CollapsiblePanel
    class="folder-panel"
    kicker="Explorer"
    :title="title"
    :meta="totalFolderCount ? `共 ${totalFolderCount} 个文件夹` : '还没有创建文件夹'"
    :initially-open="initiallyOpen"
  >
    <template #header-actions>
      <el-button text @click="emit('select', null)">全部</el-button>
    </template>

    <div class="folder-panel__actions">
      <el-button type="primary" plain @click="handleCreate(null)">新建</el-button>
      <el-button plain :disabled="!selectedFolder" @click="handleCreate(selectedFolder?.id ?? null)">
        子级
      </el-button>
      <el-button plain :disabled="!selectedFolder" @click="handleRename">重命名</el-button>
      <el-button plain :disabled="!selectedFolder" @click="handleDelete">删除</el-button>
    </div>

    <div v-if="totalFolderCount" class="folder-panel__search">
      <el-input v-model="searchKeyword" clearable placeholder="输入名称快速查找文件夹" />
      <small>{{ visibleFolderCount }} / {{ totalFolderCount }}</small>
    </div>

    <div v-if="totalFolderCount" class="folder-panel__tree-tools">
      <span class="folder-panel__tree-tip">
        {{ normalizedSearchKeyword ? '已自动展开匹配结果及其路径' : '默认仅展开当前路径' }}
      </span>

      <div class="folder-panel__tree-tool-actions">
        <el-button text size="small" @click="expandAll">展开全部</el-button>
        <el-button text size="small" :disabled="!canCollapseTree" @click="collapseTree">收起层级</el-button>
      </div>
    </div>

    <el-skeleton :loading="loading" animated :rows="5">
      <template #default>
        <el-tree
          ref="treeRef"
          :data="displayFolders"
          node-key="id"
          :default-expanded-keys="expandedKeys"
          :expand-on-click-node="false"
          highlight-current
          :current-node-key="currentFolderId ?? undefined"
          :props="{ children: 'children', label: 'name' }"
          :empty-text="normalizedSearchKeyword ? '没有找到匹配的文件夹' : '暂无文件夹'"
          class="folder-panel__tree"
          @node-click="handleNodeClick"
          @node-expand="handleNodeExpand"
          @node-collapse="handleNodeCollapse"
        >
          <template #default="{ data }">
            <div class="folder-panel__node">
              <span>{{ data.name }}</span>
              <small>{{ data.notes?.length ?? 0 }}</small>
            </div>
          </template>
        </el-tree>
      </template>
    </el-skeleton>
  </CollapsiblePanel>
</template>

<style scoped>
.folder-panel {
  padding: 16px;
}

.folder-panel__actions {
  display: grid;
  gap: 8px;
  grid-template-columns: repeat(2, minmax(0, 1fr));
}

.folder-panel__search {
  display: grid;
  gap: 8px;
}

.folder-panel__search small {
  color: var(--text-soft);
}

.folder-panel__tree-tools {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.folder-panel__tree-tip {
  color: var(--text-soft);
  font-size: 0.84rem;
}

.folder-panel__tree-tool-actions {
  display: flex;
  align-items: center;
  gap: 6px;
}

.folder-panel__tree {
  padding: 4px 0;
  background: transparent;
  max-height: min(32vh, 300px);
  overflow: auto;
}

.folder-panel__node {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  width: 100%;
  font-size: 0.9rem;
}

.folder-panel__node small {
  color: var(--text-soft);
}

.folder-panel__tree :deep(.el-tree-node__content) {
  min-height: 32px;
  border-radius: 12px;
  transition: background-color 0.2s ease;
}

.folder-panel__tree :deep(.el-tree-node:focus > .el-tree-node__content),
.folder-panel__tree :deep(.el-tree-node__content:hover) {
  background: rgba(184, 92, 56, 0.08);
}

.folder-panel__tree :deep(.el-tree-node.is-current > .el-tree-node__content) {
  background: rgba(184, 92, 56, 0.12);
}

@media (max-width: 640px) {
  .folder-panel {
    padding: 14px;
  }

  .folder-panel__actions {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .folder-panel__tree-tools {
    align-items: flex-start;
    flex-direction: column;
  }

  .folder-panel__tree-tool-actions {
    width: 100%;
    justify-content: space-between;
  }

  .folder-panel__tree {
    max-height: min(28vh, 220px);
  }
}

@media (max-width: 420px) {
  .folder-panel {
    padding: 14px;
  }

  .folder-panel__actions {
    gap: 8px;
  }
}
</style>
