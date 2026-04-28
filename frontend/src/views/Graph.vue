<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { useRoute, useRouter } from 'vue-router'
import type { EChartsOption } from 'echarts'
import PageHero from '@/components/PageHero.vue'
import EChartPanel from '@/components/EChartPanel.vue'
import NoteSearchSelect from '@/components/NoteSearchSelect.vue'
import { addRelation, getGlobalGraph, getGraphData } from '@/api/graph'
import { useNoteWorkspaceStore } from '@/stores/noteWorkspace'
import { useWorkspaceStore } from '@/stores/workspace'
import type { GraphData } from '@/types'
import { buildNoteEditRoute, buildWorkspaceTabRoute } from '@/utils/noteWorkspace'

type GraphColorConfig = {
  focusNode: string
  centerNode: string
  noteNode: string
  nodeLabel: string
  edge: string
  edgeLabel: string
}

type GraphColorKey = keyof GraphColorConfig
type PanelKey = 'scope' | 'relation' | 'color' | 'legend'

const GRAPH_COLOR_STORAGE_KEY = 'knowledgepulse.graph-colors'

const DEFAULT_GRAPH_COLORS: GraphColorConfig = {
  focusNode: '#8d4529',
  centerNode: '#365c4b',
  noteNode: '#b85c38',
  nodeLabel: '#1f2933',
  edge: '#c59d58',
  edgeLabel: '#8d4529'
}

const GRAPH_COLOR_PRESETS: Array<{
  id: string
  label: string
  description: string
  colors: GraphColorConfig
}> = [
  {
    id: 'warm',
    label: '暖铜',
    description: '适合长时间阅读，层次温和清晰。',
    colors: {
      focusNode: '#8d4529',
      centerNode: '#365c4b',
      noteNode: '#b85c38',
      nodeLabel: '#1f2933',
      edge: '#c59d58',
      edgeLabel: '#8d4529'
    }
  },
  {
    id: 'ocean',
    label: '海岸',
    description: '边和标签更清爽，适合分析型阅读。',
    colors: {
      focusNode: '#0f6d86',
      centerNode: '#2e8b77',
      noteNode: '#58a4b0',
      nodeLabel: '#14323f',
      edge: '#7dbbc3',
      edgeLabel: '#0f6d86'
    }
  },
  {
    id: 'berry',
    label: '莓果',
    description: '对比更强，适合演示和截图展示。',
    colors: {
      focusNode: '#9b1d57',
      centerNode: '#5b2a86',
      noteNode: '#d26488',
      nodeLabel: '#2d1b35',
      edge: '#d7a2b8',
      edgeLabel: '#9b1d57'
    }
  }
]

const graphColorFields: Array<{
  key: GraphColorKey
  label: string
  description: string
}> = [
  {
    key: 'focusNode',
    label: '聚焦节点',
    description: '当前选中的主节点颜色'
  },
  {
    key: 'centerNode',
    label: '中心节点',
    description: '单篇笔记聚焦时的中心颜色'
  },
  {
    key: 'noteNode',
    label: '普通节点',
    description: '其他关联笔记的默认颜色'
  },
  {
    key: 'edge',
    label: '关系连线',
    description: '图谱中的连接线颜色'
  },
  {
    key: 'edgeLabel',
    label: '关系标签',
    description: '边上的关系文字颜色'
  },
  {
    key: 'nodeLabel',
    label: '节点文字',
    description: '节点标题与提示文字颜色'
  }
]

const panelState = reactive<Record<PanelKey, boolean>>({
  scope: true,
  relation: false,
  color: false,
  legend: false
})

const route = useRoute()
const router = useRouter()
const workspaceStore = useWorkspaceStore()
const noteWorkspaceStore = useNoteWorkspaceStore()

const loading = ref(false)
const graphData = ref<GraphData>({
  nodes: [],
  links: []
})
const focusNoteId = ref<number | null>(null)

const relationForm = reactive({
  sourceNoteId: null as number | null,
  targetNoteId: null as number | null,
  relationType: '相关'
})

const graphColors = reactive<GraphColorConfig>({
  ...DEFAULT_GRAPH_COLORS
})

const activeWorkspaceTab = computed(() => noteWorkspaceStore.activeTab)
const workspaceOpenCount = computed(() => noteWorkspaceStore.openCount)
const workspaceDirtyCount = computed(() => noteWorkspaceStore.dirtyCount)
const focusedNote = computed(
  () => workspaceStore.notes.find((note) => note.id === focusNoteId.value) ?? null
)

function togglePanel(panel: PanelKey) {
  panelState[panel] = !panelState[panel]
}

function getPanelToggleSymbol(panel: PanelKey) {
  return panelState[panel] ? '-' : '+'
}

function getPanelToggleTitle(panel: PanelKey) {
  return panelState[panel] ? '收起面板' : '展开面板'
}

function toChartNodeKey(noteId: number) {
  return `note-${noteId}`
}

function isHexColor(value: unknown): value is string {
  return typeof value === 'string' && /^#[0-9a-fA-F]{6}$/.test(value)
}

function parseGraphColorConfig(value: unknown): GraphColorConfig | null {
  if (!value || typeof value !== 'object') {
    return null
  }

  const candidate = value as Partial<Record<GraphColorKey, unknown>>
  const nextConfig = { ...DEFAULT_GRAPH_COLORS }

  for (const key of Object.keys(DEFAULT_GRAPH_COLORS) as GraphColorKey[]) {
    const colorValue = candidate[key]

    if (!isHexColor(colorValue)) {
      return null
    }

    nextConfig[key] = colorValue
  }

  return nextConfig
}

function applyGraphColors(nextColors: GraphColorConfig) {
  for (const key of Object.keys(nextColors) as GraphColorKey[]) {
    graphColors[key] = nextColors[key]
  }
}

function loadGraphColors() {
  const storedValue = localStorage.getItem(GRAPH_COLOR_STORAGE_KEY)

  if (!storedValue) {
    return
  }

  try {
    const parsedValue = JSON.parse(storedValue) as unknown
    const nextColors = parseGraphColorConfig(parsedValue)

    if (nextColors) {
      applyGraphColors(nextColors)
    }
  } catch {
    localStorage.removeItem(GRAPH_COLOR_STORAGE_KEY)
  }
}

function resetGraphColors() {
  applyGraphColors(DEFAULT_GRAPH_COLORS)
  ElMessage.success('图谱配色已恢复默认')
}

function applyColorPreset(colors: GraphColorConfig) {
  applyGraphColors(colors)
  ElMessage.success('图谱配色已切换')
}

function updateGraphColor(key: GraphColorKey, event: Event) {
  const target = event.target as HTMLInputElement | null
  const nextValue = target?.value ?? ''

  if (!isHexColor(nextValue)) {
    return
  }

  graphColors[key] = nextValue
}

function isPresetActive(colors: GraphColorConfig) {
  return (Object.keys(colors) as GraphColorKey[]).every((key) => colors[key] === graphColors[key])
}

function parseGraphNoteId(value: unknown): number | null {
  if (typeof value === 'number' && Number.isFinite(value)) {
    return value
  }

  if (typeof value === 'string') {
    const normalizedValue = value.startsWith('note-') ? value.slice(5) : value
    const parsedValue = Number(normalizedValue)
    return Number.isFinite(parsedValue) ? parsedValue : null
  }

  return null
}

function syncFocusFromRoute() {
  const raw = typeof route.query.noteId === 'string' ? Number(route.query.noteId) : NaN
  focusNoteId.value = Number.isFinite(raw) ? raw : null
  relationForm.sourceNoteId = focusNoteId.value
}

async function loadGraph() {
  loading.value = true

  try {
    graphData.value = focusNoteId.value ? await getGraphData(focusNoteId.value) : await getGlobalGraph()
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '加载知识图谱失败')
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  loadGraphColors()

  if (!workspaceStore.notes.length) {
    void workspaceStore.loadExplorer()
  }

  syncFocusFromRoute()
  void loadGraph()
})

watch(
  graphColors,
  () => {
    localStorage.setItem(GRAPH_COLOR_STORAGE_KEY, JSON.stringify(graphColors))
  },
  {
    deep: true
  }
)

watch(
  () => route.fullPath,
  () => {
    syncFocusFromRoute()
    void loadGraph()
  }
)

function applyFocusFilter() {
  if (focusNoteId.value) {
    void router.push({
      path: '/graph',
      query: {
        noteId: focusNoteId.value
      }
    })
    return
  }

  void router.push('/graph')
}

async function submitRelation() {
  if (!relationForm.sourceNoteId || !relationForm.targetNoteId) {
    ElMessage.warning('请选择起点笔记和目标笔记')
    return
  }

  try {
    await addRelation({
      sourceNoteId: relationForm.sourceNoteId,
      targetNoteId: relationForm.targetNoteId,
      relationType: relationForm.relationType
    })

    relationForm.targetNoteId = null
    ElMessage.success('关联关系已添加')
    await loadGraph()
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '添加关联关系失败')
  }
}

function handleChartClick(payload: unknown) {
  const params = payload as {
    dataType?: string
    data?: {
      id?: number | string
      noteId?: number | string
    }
  }

  const noteId = parseGraphNoteId(params.data?.noteId ?? params.data?.id)

  if (params.dataType === 'node' && noteId !== null) {
    void router.push(buildNoteEditRoute(noteId))
  }
}

const chartNodes = computed(() =>
  graphData.value.nodes.map((node) => ({
    ...node,
    id: toChartNodeKey(node.id),
    noteId: node.id,
    symbolSize: focusNoteId.value === node.id ? 72 : 54,
    itemStyle: {
      color:
        focusNoteId.value === node.id
          ? graphColors.focusNode
          : node.type === 'CENTER'
            ? graphColors.centerNode
            : graphColors.noteNode
    }
  }))
)

const chartLinks = computed(() =>
  graphData.value.links.flatMap((link) => {
    const sourceId = parseGraphNoteId(link.source)
    const targetId = parseGraphNoteId(link.target)

    if (sourceId === null || targetId === null) {
      return []
    }

    return [
      {
        ...link,
        source: toChartNodeKey(sourceId),
        target: toChartNodeKey(targetId)
      }
    ]
  })
)

const chartOption = computed(
  () =>
    ({
      backgroundColor: 'transparent',
      tooltip: {
        formatter: (params: any) => {
          if (params.dataType === 'edge') {
            return params.data?.relationType || '关联'
          }

          return params.data?.name || ''
        }
      },
      series: [
        {
          type: 'graph',
          layout: 'force',
          roam: true,
          draggable: true,
          label: {
            show: true,
            color: graphColors.nodeLabel
          },
          force: {
            repulsion: 520,
            edgeLength: [100, 180]
          },
          lineStyle: {
            width: 2,
            color: graphColors.edge,
            curveness: 0.18
          },
          edgeLabel: {
            show: true,
            color: graphColors.edgeLabel,
            formatter: (params: { data?: { relationType?: string } }) => params.data?.relationType || ''
          },
          emphasis: {
            focus: 'adjacency'
          },
          data: chartNodes.value,
          links: chartLinks.value
        }
      ],
      graphic: graphData.value.nodes.length
        ? undefined
        : {
            type: 'text',
            left: 'center',
            top: 'middle',
            style: {
              text: '当前还没有可展示的图谱关系',
              fill: graphColors.nodeLabel,
              fontSize: 18
            }
          }
    }) as EChartsOption
)

function openFocusedNote() {
  if (!focusNoteId.value) {
    ElMessage.warning('请先选择一篇笔记作为图谱焦点')
    return
  }

  void router.push(buildNoteEditRoute(focusNoteId.value))
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
  <div class="graph-view page-shell">
    <PageHero
      kicker="Graph"
      title="全局知识图谱"
      description="从图谱上查看笔记之间的引用、扩展与相关关系，点击节点后可直接加入工作区继续编辑。"
    >
      <template #actions>
        <el-button plain @click="loadGraph">刷新图谱</el-button>
        <el-button v-if="focusNoteId" type="primary" plain @click="openFocusedNote">打开聚焦笔记</el-button>
      </template>
    </PageHero>

    <div class="graph-view__layout">
      <aside class="graph-view__aside">
        <section class="panel graph-panel graph-panel--workspace">
          <span class="section-kicker">Workspace</span>
          <strong>{{ activeWorkspaceTab?.title || '当前还没有打开中的编辑标签' }}</strong>
          <p class="graph-panel__meta">
            {{
              workspaceOpenCount
                ? `已打开 ${workspaceOpenCount} 个工作区标签${workspaceDirtyCount ? `，其中 ${workspaceDirtyCount} 个未保存` : ''}。`
                : '点击图谱节点或聚焦按钮后，都会直接进入工作区标签。'
            }}
          </p>
          <el-button plain @click="continueWorkspace">
            {{ activeWorkspaceTab ? '返回当前编辑' : '去工作区看看' }}
          </el-button>
        </section>

        <section class="panel graph-panel">
          <div class="graph-panel__title-row">
            <div>
              <span class="section-kicker">Scope</span>
              <h3 class="section-title">聚焦视图</h3>
              <small class="graph-panel__meta">先锁定一篇笔记，可以更清楚地看到它和周边内容的连接。</small>
            </div>

            <button
              type="button"
              class="graph-panel__toggle"
              :aria-expanded="panelState.scope"
              :title="getPanelToggleTitle('scope')"
              @click="togglePanel('scope')"
            >
              {{ getPanelToggleSymbol('scope') }}
            </button>
          </div>

          <transition name="graph-panel-collapse">
            <div v-show="panelState.scope" class="graph-panel__body">
              <NoteSearchSelect
                v-model="focusNoteId"
                :notes="workspaceStore.notes"
                placeholder="搜索一篇笔记后聚焦图谱"
              />
              <div class="graph-panel__actions graph-panel__actions--spread">
                <span class="graph-panel__caption">
                  {{ focusedNote ? `当前聚焦：${focusedNote.title}` : '未选择聚焦笔记，将展示全局关系。' }}
                </span>
                <el-button type="primary" plain @click="applyFocusFilter">应用筛选</el-button>
              </div>
            </div>
          </transition>
        </section>

        <section class="panel graph-panel">
          <div class="graph-panel__title-row">
            <div>
              <span class="section-kicker">Relation</span>
              <h3 class="section-title">添加关联关系</h3>
              <small class="graph-panel__meta">从这里补充两篇笔记之间的语义连接，图谱会立即反映变化。</small>
            </div>

            <button
              type="button"
              class="graph-panel__toggle"
              :aria-expanded="panelState.relation"
              :title="getPanelToggleTitle('relation')"
              @click="togglePanel('relation')"
            >
              {{ getPanelToggleSymbol('relation') }}
            </button>
          </div>

          <transition name="graph-panel-collapse">
            <div v-show="panelState.relation" class="graph-panel__body">
              <el-form label-position="top" class="graph-panel__form">
                <el-form-item label="起点笔记">
                  <NoteSearchSelect
                    v-model="relationForm.sourceNoteId"
                    :notes="workspaceStore.notes"
                    :disabled-ids="relationForm.targetNoteId ? [relationForm.targetNoteId] : []"
                    placeholder="搜索起点笔记"
                  />
                </el-form-item>

                <el-form-item label="目标笔记">
                  <NoteSearchSelect
                    v-model="relationForm.targetNoteId"
                    :notes="workspaceStore.notes"
                    :disabled-ids="relationForm.sourceNoteId ? [relationForm.sourceNoteId] : []"
                    placeholder="搜索目标笔记"
                  />
                </el-form-item>

                <el-form-item label="关系类型">
                  <el-select v-model="relationForm.relationType">
                    <el-option label="引用" value="引用" />
                    <el-option label="扩展" value="扩展" />
                    <el-option label="相关" value="相关" />
                  </el-select>
                </el-form-item>
              </el-form>

              <el-button type="primary" @click="submitRelation">添加关联</el-button>
            </div>
          </transition>
        </section>

        <section class="panel graph-panel">
          <div class="graph-panel__title-row">
            <div>
              <span class="section-kicker">Color</span>
              <h3 class="section-title">图谱配色</h3>
              <small class="graph-panel__meta">配色会保存在当前浏览器中，方便你按自己的阅读习惯定制。</small>
            </div>

            <button
              type="button"
              class="graph-panel__toggle"
              :aria-expanded="panelState.color"
              :title="getPanelToggleTitle('color')"
              @click="togglePanel('color')"
            >
              {{ getPanelToggleSymbol('color') }}
            </button>
          </div>

          <transition name="graph-panel-collapse">
            <div v-show="panelState.color" class="graph-panel__body">
              <div class="graph-palette__presets">
                <button
                  v-for="preset in GRAPH_COLOR_PRESETS"
                  :key="preset.id"
                  type="button"
                  class="graph-palette__preset"
                  :class="{ 'is-active': isPresetActive(preset.colors) }"
                  @click="applyColorPreset(preset.colors)"
                >
                  <div class="graph-palette__swatches">
                    <span :style="{ background: preset.colors.focusNode }" />
                    <span :style="{ background: preset.colors.centerNode }" />
                    <span :style="{ background: preset.colors.noteNode }" />
                    <span :style="{ background: preset.colors.edge }" />
                  </div>
                  <strong>{{ preset.label }}</strong>
                  <span>{{ preset.description }}</span>
                </button>
              </div>

              <div class="graph-palette__grid">
                <label
                  v-for="field in graphColorFields"
                  :key="field.key"
                  class="graph-palette__field"
                >
                  <div class="graph-palette__field-copy">
                    <strong>{{ field.label }}</strong>
                    <span>{{ field.description }}</span>
                  </div>

                  <div class="graph-palette__field-input">
                    <code>{{ graphColors[field.key] }}</code>
                    <input
                      type="color"
                      :value="graphColors[field.key]"
                      @input="updateGraphColor(field.key, $event)"
                    />
                  </div>
                </label>
              </div>

              <div class="graph-panel__actions">
                <el-button plain @click="resetGraphColors">恢复默认</el-button>
              </div>
            </div>
          </transition>
        </section>

        <section class="panel graph-panel">
          <div class="graph-panel__title-row">
            <div>
              <span class="section-kicker">Legend</span>
              <h3 class="section-title">使用说明</h3>
            </div>

            <button
              type="button"
              class="graph-panel__toggle"
              :aria-expanded="panelState.legend"
              :title="getPanelToggleTitle('legend')"
              @click="togglePanel('legend')"
            >
              {{ getPanelToggleSymbol('legend') }}
            </button>
          </div>

          <transition name="graph-panel-collapse">
            <div v-show="panelState.legend" class="graph-panel__body">
              <ul class="graph-panel__legend">
                <li>中心或聚焦节点用于强调你当前正在观察的主笔记。</li>
                <li>普通节点、连线和关系文字都可以分别自定义颜色。</li>
                <li>点击图谱节点后，会直接把对应笔记加入工作区继续编辑。</li>
              </ul>
            </div>
          </transition>
        </section>
      </aside>

      <section class="panel graph-view__canvas">
        <div class="graph-view__canvas-head">
          <div>
            <span class="section-kicker">Canvas</span>
            <strong>{{ focusedNote ? focusedNote.title : '全局关系视图' }}</strong>
            <small>{{ focusedNote ? '当前画布已围绕这篇笔记进行聚焦展示。' : '当前展示的是全部笔记之间的整体连接。' }}</small>
          </div>
          <span class="graph-view__canvas-tip">点击节点即可加入工作区</span>
        </div>

        <EChartPanel
          :option="chartOption"
          height="620px"
          :loading="loading"
          @chart-click="handleChartClick"
        />
      </section>
    </div>
  </div>
</template>

<style scoped>
.graph-view__layout {
  display: grid;
  gap: 16px;
  grid-template-columns: 320px minmax(0, 1fr);
}

.graph-view__aside {
  display: grid;
  align-content: start;
  gap: 16px;
}

.graph-panel {
  display: grid;
  gap: 10px;
  padding: 16px;
}

.graph-panel--workspace {
  background:
    linear-gradient(180deg, rgba(255, 252, 248, 0.9), rgba(255, 246, 238, 0.78)),
    rgba(255, 255, 255, 0.56);
}

.graph-panel__title-row {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 8px;
}

.graph-panel__meta {
  display: block;
  margin-top: 4px;
  color: var(--text-soft);
  font-size: 0.82rem;
  line-height: 1.35;
}

.graph-panel__toggle {
  width: 30px;
  height: 30px;
  border: 1px solid rgba(184, 92, 56, 0.2);
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.72);
  color: #8d4529;
  font-size: 1.08rem;
  line-height: 1;
  cursor: pointer;
  transition: border-color 0.2s ease, background-color 0.2s ease, transform 0.2s ease;
}

.graph-panel__toggle:hover {
  border-color: rgba(141, 69, 41, 0.42);
  background: rgba(255, 255, 255, 0.96);
  transform: translateY(-1px);
}

.graph-panel__body {
  display: grid;
  gap: 10px;
}

.graph-panel__form {
  margin-top: 4px;
}

.graph-panel__actions {
  display: flex;
  justify-content: flex-end;
}

.graph-panel__actions--spread {
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.graph-panel__caption {
  color: var(--text-soft);
  font-size: 0.84rem;
  line-height: 1.6;
}

.graph-palette__presets {
  display: grid;
  gap: 10px;
}

.graph-palette__preset {
  display: grid;
  gap: 6px;
  padding: 10px;
  border: 1px solid rgba(184, 92, 56, 0.16);
  border-radius: 16px;
  background: rgba(255, 255, 255, 0.56);
  text-align: left;
  transition: border-color 0.2s ease, transform 0.2s ease, box-shadow 0.2s ease;
}

.graph-palette__preset:hover,
.graph-palette__preset.is-active {
  border-color: rgba(141, 69, 41, 0.45);
  box-shadow: 0 10px 24px rgba(184, 92, 56, 0.12);
  transform: translateY(-1px);
}

.graph-palette__preset strong {
  color: var(--text-main);
}

.graph-palette__preset span {
  color: var(--text-soft);
  font-size: 0.82rem;
  line-height: 1.35;
}

.graph-palette__swatches {
  display: flex;
  gap: 8px;
}

.graph-palette__swatches span {
  width: 22px;
  height: 22px;
  border-radius: 999px;
  border: 1px solid rgba(31, 41, 51, 0.12);
}

.graph-palette__grid {
  display: grid;
  gap: 10px;
}

.graph-palette__field {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 8px;
  padding: 10px 12px;
  border-radius: 16px;
  background: rgba(255, 255, 255, 0.46);
  border: 1px solid rgba(184, 92, 56, 0.1);
}

.graph-palette__field-copy {
  display: grid;
  gap: 4px;
}

.graph-palette__field-copy strong {
  color: var(--text-main);
}

.graph-palette__field-copy span {
  color: var(--text-soft);
  font-size: 0.82rem;
  line-height: 1.35;
}

.graph-palette__field-input {
  display: flex;
  align-items: center;
  gap: 10px;
}

.graph-palette__field-input code {
  color: var(--text-soft);
  font-size: 0.8rem;
  background: rgba(255, 255, 255, 0.8);
  padding: 4px 8px;
  border-radius: 999px;
}

.graph-palette__field-input input[type='color'] {
  width: 42px;
  height: 42px;
  border: 0;
  padding: 0;
  border-radius: 12px;
  background: transparent;
  cursor: pointer;
}

.graph-panel__legend {
  margin: 0;
  padding-left: 18px;
  color: var(--text-soft);
  line-height: 1.55;
}

.graph-view__canvas {
  display: grid;
  gap: 12px;
  padding: 14px;
}

.graph-view__canvas-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
}

.graph-view__canvas-head strong {
  display: block;
  margin-top: 6px;
}

.graph-view__canvas-head small {
  display: block;
  margin-top: 8px;
  color: var(--text-soft);
}

.graph-view__canvas-tip {
  padding: 8px 11px;
  border-radius: 999px;
  background: rgba(54, 92, 75, 0.1);
  color: #365c4b;
  font-size: 0.84rem;
}

.graph-panel-collapse-enter-active,
.graph-panel-collapse-leave-active {
  transition: opacity 0.2s ease, transform 0.2s ease;
}

.graph-panel-collapse-enter-from,
.graph-panel-collapse-leave-to {
  opacity: 0;
  transform: translateY(-6px);
}

@media (max-width: 1180px) {
  .graph-view__layout {
    grid-template-columns: 1fr;
  }

  .graph-palette__field,
  .graph-panel__actions--spread,
  .graph-view__canvas-head {
    flex-direction: column;
    align-items: flex-start;
  }
}

@media (max-width: 640px) {
  .graph-view__layout,
  .graph-view__aside {
    gap: 16px;
  }

  .graph-view__canvas {
    order: 1;
  }

  .graph-view__aside {
    order: 2;
  }

  .graph-panel,
  .graph-view__canvas {
    padding: 16px;
    border-radius: 20px;
  }

  .graph-panel__title-row,
  .graph-panel__actions {
    align-items: center;
  }

  .graph-panel__meta,
  .graph-view__canvas-head small,
  .graph-view__canvas-tip,
  .graph-panel--workspace .graph-panel__meta {
    display: none;
  }

  .graph-panel__actions,
  .graph-panel__actions--spread {
    flex-direction: column;
  }

  .graph-panel__actions :deep(.el-button),
  .graph-panel__actions--spread :deep(.el-button) {
    width: 100%;
  }

  .graph-palette__field {
    align-items: flex-start;
    flex-direction: column;
  }

  .graph-palette__field-input {
    width: 100%;
    justify-content: space-between;
  }

  .graph-view__canvas :deep(.chart-shell__canvas) {
    height: min(52dvh, 460px) !important;
    min-height: 280px;
  }
}

@media (max-width: 420px) {
  .graph-panel,
  .graph-view__canvas {
    padding: 14px;
  }

  .graph-view__canvas :deep(.chart-shell__canvas) {
    height: min(50dvh, 400px) !important;
    min-height: 260px;
  }
}
</style>
