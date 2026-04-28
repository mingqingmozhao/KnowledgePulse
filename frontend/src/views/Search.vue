<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import PageHero from '@/components/PageHero.vue'
import CollapsiblePanel from '@/components/CollapsiblePanel.vue'
import TagCloudPanel from '@/components/TagCloudPanel.vue'
import { useNoteWorkspaceStore } from '@/stores/noteWorkspace'
import { useWorkspaceStore } from '@/stores/workspace'
import { formatDateTime } from '@/utils/format'
import { buildNoteEditRoute, buildWorkspaceTabRoute } from '@/utils/noteWorkspace'

const route = useRoute()
const router = useRouter()
const workspaceStore = useWorkspaceStore()
const noteWorkspaceStore = useNoteWorkspaceStore()

const searchInput = ref('')

const queryLabel = computed(() => {
  if (typeof route.query.tag === 'string') {
    return `标签：${route.query.tag}`
  }

  if (typeof route.query.keyword === 'string') {
    return `关键词：${route.query.keyword}`
  }

  return '输入关键词开始搜索'
})

const activeWorkspaceTab = computed(() => noteWorkspaceStore.activeTab)
const workspaceSummary = computed(() => {
  const openCount = noteWorkspaceStore.openCount
  const dirtyCount = noteWorkspaceStore.dirtyCount

  if (!openCount) {
    return '搜索结果打开后会自动进入工作区标签，方便同时查看多篇笔记。'
  }

  return `当前工作区里有 ${openCount} 个标签${dirtyCount ? `，其中 ${dirtyCount} 个未保存` : ''}。`
})

async function runSearchFromRoute() {
  const tag = typeof route.query.tag === 'string' ? route.query.tag : ''
  const keyword = typeof route.query.keyword === 'string' ? route.query.keyword : ''

  searchInput.value = tag || keyword

  if (tag) {
    await workspaceStore.runTagSearch(tag)
    return
  }

  if (keyword) {
    await workspaceStore.runSearch(keyword)
    return
  }

  workspaceStore.clearSearch()
}

onMounted(() => {
  if (!workspaceStore.notes.length) {
    void workspaceStore.loadExplorer()
  }

  void runSearchFromRoute()
})

watch(
  () => route.fullPath,
  () => {
    void runSearchFromRoute()
  }
)

function submitSearch() {
  const keyword = searchInput.value.trim()

  if (!keyword) {
    workspaceStore.clearSearch()
    void router.push('/search')
    return
  }

  void router.push({
    path: '/search',
    query: {
      keyword
    }
  })
}

function searchByTag(tag: string) {
  void router.push({
    path: '/search',
    query: {
      tag
    }
  })
}

function getResultTags(tagsText: string) {
  return tagsText
    .split(',')
    .map((tag) => tag.trim())
    .filter(Boolean)
}

function openNote(noteId: number) {
  void router.push(buildNoteEditRoute(noteId))
}

function continueWorkspace() {
  if (!activeWorkspaceTab.value) {
    void router.push('/folder')
    return
  }

  void router.push(buildWorkspaceTabRoute(activeWorkspaceTab.value))
}
</script>

<template>
  <div class="search-view page-shell">
    <PageHero
      kicker="Search"
      title="全文搜索与主题检索"
      description="同时覆盖标题、正文和标签，让你更快从大量笔记中定位到真正想找的内容。"
    >
      <template #actions>
        <form class="search-view__toolbar" @submit.prevent="submitSearch">
          <input
            v-model="searchInput"
            type="search"
            placeholder="输入关键词，例如：Spring Security、图谱、协作权限"
          />
          <button type="submit">搜索</button>
        </form>
      </template>
    </PageHero>

    <div class="search-view__layout">
      <CollapsiblePanel
        class="search-view__result"
        kicker="Query"
        :title="queryLabel"
        :meta="`共找到 ${workspaceStore.searchResults.length} 条结果`"
        body-class="search-view__result-body"
        :initially-open="true"
      >
        <div v-if="workspaceStore.searchResults.length" class="search-view__list">
          <article v-for="item in workspaceStore.searchResults" :key="item.id" class="search-view__item">
            <div class="search-view__item-head">
              <div class="search-view__item-copy">
                <strong>{{ item.title }}</strong>
                <small>{{ formatDateTime(item.updateTime) }}</small>
              </div>
              <span class="search-view__item-status">可加入工作区</span>
            </div>

            <p>{{ item.snippet || '这篇笔记命中了当前搜索条件，可以直接加入工作区继续查看与编辑。' }}</p>

            <div class="search-view__item-tags">
              <button
                v-for="tag in getResultTags(item.tags)"
                :key="tag"
                type="button"
                class="search-view__tag"
                @click="searchByTag(tag)"
              >
                #{{ tag }}
              </button>
            </div>

            <div class="search-view__item-actions">
              <span>打开后会进入顶部工作区标签，便于来回比对多篇笔记。</span>
              <el-button type="primary" @click="openNote(item.id)">加入工作区</el-button>
            </div>
          </article>
        </div>

        <div v-else class="empty-state">
          <strong>暂时没有匹配结果</strong>
          <span>可以换一个更宽泛的关键词，或者直接从右侧标签云切换检索角度。</span>
        </div>
      </CollapsiblePanel>

      <aside class="search-view__aside">
        <section class="panel search-view__workspace-card">
          <span class="section-kicker">Workspace</span>
          <strong>{{ activeWorkspaceTab?.title || '当前还没有打开中的编辑标签' }}</strong>
          <p>{{ workspaceSummary }}</p>
          <el-button plain @click="continueWorkspace">
            {{ activeWorkspaceTab ? '返回当前编辑' : '去工作区看看' }}
          </el-button>
        </section>

        <TagCloudPanel :tags="workspaceStore.tagBuckets" :initially-open="false" @select="searchByTag" />
      </aside>
    </div>
  </div>
</template>

<style scoped>
.search-view__toolbar {
  display: flex;
  align-items: center;
  gap: 10px;
  min-width: min(560px, 100%);
  padding: 8px;
  border: 1px solid var(--line);
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.62);
}

.search-view__toolbar input {
  width: 100%;
  padding: 0 12px;
  border: 0;
  outline: none;
  background: transparent;
}

.search-view__toolbar button {
  padding: 10px 18px;
  border: 0;
  border-radius: 999px;
  background: var(--accent);
  color: #fff;
  cursor: pointer;
}

.search-view__layout {
  display: grid;
  gap: 16px;
  grid-template-columns: minmax(0, 1fr) 280px;
}

.search-view__result {
  padding: 16px;
}

.search-view__result-body,
.search-view__list,
.search-view__aside {
  display: grid;
  gap: 12px;
}

.search-view__item {
  display: grid;
  gap: 10px;
  padding: 14px;
  border: 1px solid rgba(184, 92, 56, 0.14);
  border-radius: 18px;
  background:
    linear-gradient(180deg, rgba(255, 255, 255, 0.82), rgba(255, 250, 245, 0.72)),
    rgba(255, 255, 255, 0.56);
  box-shadow: 0 10px 24px rgba(141, 69, 41, 0.05);
}

.search-view__item-head,
.search-view__item-actions {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 10px;
}

.search-view__item-copy {
  display: grid;
  gap: 6px;
}

.search-view__item p,
.search-view__item small,
.search-view__item-actions span,
.search-view__workspace-card p {
  margin: 0;
  color: var(--text-soft);
  line-height: 1.55;
}

.search-view__item-status {
  padding: 6px 10px;
  border-radius: 999px;
  background: rgba(54, 92, 75, 0.1);
  color: #365c4b;
  font-size: 0.82rem;
  white-space: nowrap;
}

.search-view__item-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.search-view__tag {
  padding: 6px 9px;
  border: 0;
  border-radius: 999px;
  background: rgba(184, 92, 56, 0.08);
  color: var(--accent-strong);
  cursor: pointer;
}

.search-view__workspace-card {
  display: grid;
  gap: 8px;
  padding: 14px;
}

.search-view__workspace-card strong {
  font-size: 1.02rem;
}

@media (max-width: 1180px) {
  .search-view__layout {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 900px) {
  .search-view__toolbar {
    min-width: 100%;
  }

  .search-view__item-head,
  .search-view__item-actions {
    flex-direction: column;
    align-items: flex-start;
  }
}

@media (max-width: 640px) {
  .search-view__layout,
  .search-view__result-body,
  .search-view__list,
  .search-view__aside {
    gap: 14px;
  }

  .search-view__toolbar {
    position: sticky;
    top: 72px;
    z-index: 12;
    align-items: center;
    flex-direction: row;
    padding: 10px;
    border-radius: 18px;
  }

  .search-view__toolbar input {
    min-height: 42px;
  }

  .search-view__toolbar button {
    flex: 0 0 auto;
  }

  .search-view__result,
  .search-view__item,
  .search-view__workspace-card {
    padding: 16px;
    border-radius: 18px;
  }

  .search-view__item p,
  .search-view__workspace-card p {
    overflow: hidden;
    display: -webkit-box;
    -webkit-box-orient: vertical;
    -webkit-line-clamp: 2;
  }

  .search-view__item-actions :deep(.el-button) {
    width: 100%;
  }
}

@media (max-width: 420px) {
  .search-view__result,
  .search-view__item,
  .search-view__workspace-card {
    padding: 14px;
  }

  .search-view__toolbar {
    gap: 6px;
  }

  .search-view__toolbar button {
    padding-inline: 12px;
  }
}
</style>
