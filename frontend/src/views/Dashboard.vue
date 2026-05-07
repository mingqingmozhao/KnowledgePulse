<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { useRouter } from 'vue-router'
import type { EChartsOption } from 'echarts'
import EChartPanel from '@/components/EChartPanel.vue'
import { useNoteWorkspaceStore } from '@/stores/noteWorkspace'
import { useWorkspaceStore } from '@/stores/workspace'
import { formatDateOnly, relativeTime } from '@/utils/format'
import {
  buildDraftNoteRoute,
  buildNoteEditRoute,
  buildWorkspaceTabRoute
} from '@/utils/noteWorkspace'

type PanelKey = 'daily' | 'stats' | 'tags' | 'heatmap' | 'inspiration' | 'recent'
const DASHBOARD_WIDGET_KEYS = ['daily', 'workspace', 'recent', 'notes', 'folders', 'tags'] as const
type DashboardWidgetKey = (typeof DASHBOARD_WIDGET_KEYS)[number]
type DashboardWidget = {
  key: DashboardWidgetKey
  label: string
  value: string | number
  meta: string
  actionLabel: string
  tone?: 'accent' | 'moss' | 'gold'
  action: () => void | Promise<void>
}

type CalendarCell = {
  key: string
  dateKey: string
  dayNumber: number
  inCurrentMonth: boolean
  isToday: boolean
  isSelected: boolean
  hasNote: boolean
}

const router = useRouter()
const workspaceStore = useWorkspaceStore()
const noteWorkspaceStore = useNoteWorkspaceStore()

const todayKey = toDateKey(new Date())
const currentMonth = ref(startOfMonth(parseDateKey(todayKey)))
const selectedDate = ref(todayKey)
const dailyOpening = ref(false)
const DASHBOARD_WIDGET_ORDER_STORAGE_KEY = 'knowledgepulse.dashboard-widget-order'
const dashboardWidgetOrder = ref<DashboardWidgetKey[]>(readDashboardWidgetOrder())
const dashboardWidgetDragKey = ref<DashboardWidgetKey | null>(null)
let dashboardWidgetDrag: {
  key: DashboardWidgetKey
  pointerId: number
  startX: number
  startY: number
} | null = null
let dashboardWidgetDidDrag = false
let suppressDashboardWidgetClick = false

const panelState = reactive<Record<PanelKey, boolean>>({
  daily: false,
  stats: false,
  tags: false,
  heatmap: false,
  inspiration: false,
  recent: true
})

onMounted(() => {
  void bootstrapDashboard()
})

const activeWorkspaceTab = computed(() => noteWorkspaceStore.activeTab)
const workspaceOpenCount = computed(() => noteWorkspaceStore.openCount)
const workspaceDirtyCount = computed(() => noteWorkspaceStore.dirtyCount)

const monthKey = computed(() => `${currentMonth.value.getFullYear()}-${String(currentMonth.value.getMonth() + 1).padStart(2, '0')}`)
const monthLabel = computed(() =>
  new Intl.DateTimeFormat('zh-CN', {
    year: 'numeric',
    month: 'long'
  }).format(currentMonth.value)
)
const selectedDateLabel = computed(() => formatDateOnly(selectedDate.value))
const dailyNoteDates = computed(() => workspaceStore.dailyNoteCalendarCache[monthKey.value] ?? [])
const dailyNoteDateSet = computed(() => new Set(dailyNoteDates.value))
const monthRecordedCount = computed(() => dailyNoteDates.value.length)
const selectedDateHasNote = computed(() => dailyNoteDateSet.value.has(selectedDate.value))
const monthRecordedPreview = computed(() =>
  [...dailyNoteDates.value].sort((left, right) => right.localeCompare(left)).slice(0, 4)
)
const calendarCells = computed(() =>
  buildCalendarCells(currentMonth.value, dailyNoteDateSet.value, selectedDate.value, todayKey)
)

watch(
  () => monthKey.value,
  (month) => {
    void workspaceStore.loadDailyNoteCalendar(month).catch(() => [])
  },
  {
    immediate: true
  }
)

const selectedDateSummary = computed(() => {
  if (selectedDate.value === todayKey && selectedDateHasNote.value) {
    return '今天的每日笔记已经存在，可以直接继续写。'
  }

  if (selectedDate.value === todayKey) {
    return '今天还没有开始记录，现在就可以创建一篇新的每日日记。'
  }

  if (selectedDateHasNote.value) {
    return '这一天已经有一篇每日日记，适合补充复盘或继续整理碎片想法。'
  }

  return '这一天还没有记录，点一下就能为它创建一篇新的每日日记。'
})

const selectedDateBadge = computed(() => {
  if (selectedDate.value === todayKey) {
    return selectedDateHasNote.value ? '今日已记录' : '今日待开始'
  }

  return selectedDateHasNote.value ? '已存在记录' : '可创建新记录'
})

const stats = computed(() => [
  {
    label: '笔记总数',
    value: workspaceStore.dashboard?.totalNotes ?? workspaceStore.notes.length,
    note: '当前知识库中可直接继续整理的内容总量'
  },
  {
    label: '文件夹总数',
    value: workspaceStore.dashboard?.totalFolders ?? workspaceStore.folders.length,
    note: '帮助你维持层级、专题和项目的清晰边界'
  },
  {
    label: '标签数量',
    value: workspaceStore.dashboard?.totalTags ?? workspaceStore.tagBuckets.length,
    note: '高频主题会慢慢沉淀成你的长期知识主线'
  }
])

const tagDistribution = computed(() => {
  if (workspaceStore.dashboard?.tagDistribution) {
    return Object.entries(workspaceStore.dashboard.tagDistribution)
  }

  return workspaceStore.tagBuckets.map((tag) => [tag.label, tag.count] as [string, number])
})

const tagDistributionOption = computed(
  () =>
    ({
      tooltip: {
        trigger: 'item'
      },
      color: ['#b85c38', '#365c4b', '#c59d58', '#8d4529', '#7d8f69', '#9a7258'],
      series: [
        {
          type: 'pie',
          radius: ['36%', '72%'],
          roseType: 'area',
          itemStyle: {
            borderRadius: 12
          },
          label: {
            formatter: '{b}\n{d}%'
          },
          data: (tagDistribution.value.length ? tagDistribution.value : [['暂无标签', 1]]).map(
            ([name, value]) => ({
              name,
              value: Number(value)
            })
          )
        }
      ]
    }) as EChartsOption
)

const heatmapSource = computed(() =>
  Object.entries(workspaceStore.dashboard?.editHeatmap ?? {}).map(([date, count]) => [
    date,
    count
  ] as [string, number])
)

const heatmapOption = computed(
  () =>
    ({
      tooltip: {
        formatter: (params: { data?: [string, number] }) =>
          params.data ? `${params.data[0]}：${params.data[1]} 次编辑` : ''
      },
      visualMap: {
        min: 0,
        max: Math.max(...heatmapSource.value.map((item) => item[1]), 1),
        calculable: true,
        orient: 'horizontal',
        left: 'center',
        bottom: 0,
        inRange: {
          color: ['#f1e8db', '#d8b16d', '#b85c38', '#6f3b27']
        }
      },
      calendar: {
        top: 36,
        left: 32,
        right: 32,
        cellSize: ['auto', 18],
        range: heatmapSource.value.length
          ? [
              heatmapSource.value[0][0],
              heatmapSource.value[heatmapSource.value.length - 1][0]
            ]
          : todayKey,
        itemStyle: {
          borderWidth: 3,
          borderColor: '#f7f1e8'
        },
        yearLabel: {
          show: false
        },
        dayLabel: {
          firstDay: 1,
          nameMap: 'ZH'
        },
        monthLabel: {
          color: '#66584a'
        }
      },
      series: [
        {
          type: 'heatmap',
          coordinateSystem: 'calendar',
          data: heatmapSource.value
        }
      ],
      graphic: heatmapSource.value.length
        ? undefined
        : {
            type: 'text',
            left: 'center',
            top: 'middle',
            style: {
              text: '最近还没有编辑热力数据',
              fill: '#66584a',
              fontSize: 16
            }
          }
    }) as EChartsOption
)

const recentNotes = computed(() => workspaceStore.notes.slice(0, 4))
const inspirationRecommendations = computed(() => workspaceStore.inspiration?.recommendations ?? [])
const inspirationPrompts = computed(() => workspaceStore.inspiration?.inspirationPrompts ?? [])
const startSummary = computed(() => {
  if (workspaceDirtyCount.value > 0) {
    return `你有 ${workspaceDirtyCount.value} 个未保存标签。建议先回工作区确认内容，避免继续分散注意力。`
  }

  if (activeWorkspaceTab.value) {
    return `当前工作区停在「${activeWorkspaceTab.value.title}」。如果不确定从哪里开始，先继续它。`
  }

  if (recentNotes.value.length) {
    return '最近笔记里通常保留着最完整的上下文。先接着上一件事做，比重新找入口更省力。'
  }

  return '知识库还比较空。建议先新建一篇草稿，或者把已有 Markdown、PDF、Word 从导入中心放进来。'
})
const primaryStartLabel = computed(() => {
  if (workspaceDirtyCount.value > 0 || activeWorkspaceTab.value) {
    return '继续当前工作区'
  }

  if (recentNotes.value.length) {
    return '打开最近笔记'
  }

  return '新建第一篇草稿'
})
const dashboardWidgetMap = computed<Record<DashboardWidgetKey, DashboardWidget>>(() => {
  const [noteStat, folderStat, tagStat] = stats.value

  return {
    daily: {
      key: 'daily',
      label: '每日日记',
      value: selectedDateHasNote.value ? '续写' : '新建',
      meta: formatDateOnly(todayKey),
      actionLabel: selectedDateHasNote.value ? '打开今天' : '立即记录',
      tone: 'accent',
      action: openSelectedDailyNote
    },
    workspace: {
      key: 'workspace',
      label: '工作区',
      value: workspaceOpenCount.value,
      meta: workspaceDirtyCount.value ? `${workspaceDirtyCount.value} 个未保存` : '都已同步',
      actionLabel: '继续',
      tone: 'moss',
      action: continueWorkspace
    },
    recent: {
      key: 'recent',
      label: '最近笔记',
      value: recentNotes.value.length,
      meta: recentNotes.value[0]?.title || '暂无最近内容',
      actionLabel: '打开',
      action: () => {
        const [latestNote] = recentNotes.value

        if (latestNote) {
          openRecentNote(latestNote.id)
          return
        }

        createNote()
      }
    },
    notes: {
      key: 'notes',
      label: noteStat.label,
      value: noteStat.value,
      meta: noteStat.note,
      actionLabel: '查看指标',
      tone: 'gold',
      action: () => openPanelAndJump('stats', 'dashboard-stats')
    },
    folders: {
      key: 'folders',
      label: folderStat.label,
      value: folderStat.value,
      meta: folderStat.note,
      actionLabel: '查看指标',
      action: () => openPanelAndJump('stats', 'dashboard-stats')
    },
    tags: {
      key: 'tags',
      label: tagStat.label,
      value: tagStat.value,
      meta: tagStat.note,
      actionLabel: '查看标签',
      tone: 'moss',
      action: () => openPanelAndJump('tags', 'dashboard-inspiration')
    }
  }
})
const orderedDashboardWidgets = computed(() =>
  dashboardWidgetOrder.value.map((key) => dashboardWidgetMap.value[key]).filter(Boolean)
)

watch(
  dashboardWidgetOrder,
  (order) => {
    try {
      window.localStorage.setItem(DASHBOARD_WIDGET_ORDER_STORAGE_KEY, JSON.stringify(order))
    } catch {
      // 排序偏好失败时不影响首页核心操作。
    }
  },
  {
    deep: true
  }
)

onBeforeUnmount(() => {
  teardownDashboardWidgetDrag()
})

async function bootstrapDashboard() {
  try {
    if (!workspaceStore.notes.length || !workspaceStore.folders.length) {
      await workspaceStore.loadExplorer()
    }

    if (!workspaceStore.dashboard) {
      await workspaceStore.loadDashboard()
    }
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '加载仪表盘失败')
  }
}

function toDateKey(date: Date) {
  const year = date.getFullYear()
  const month = String(date.getMonth() + 1).padStart(2, '0')
  const day = String(date.getDate()).padStart(2, '0')
  return `${year}-${month}-${day}`
}

function parseDateKey(dateKey: string) {
  const [year, month, day] = dateKey.split('-').map(Number)
  return new Date(year, (month || 1) - 1, day || 1)
}

function startOfMonth(date: Date) {
  return new Date(date.getFullYear(), date.getMonth(), 1)
}

function buildCalendarCells(
  monthDate: Date,
  noteDates: Set<string>,
  selectedDateKey: string,
  todayDateKey: string
) {
  const firstDay = new Date(monthDate.getFullYear(), monthDate.getMonth(), 1)
  const startOffset = (firstDay.getDay() + 6) % 7
  const gridStart = new Date(firstDay)
  gridStart.setDate(firstDay.getDate() - startOffset)

  const cells: CalendarCell[] = []

  for (let index = 0; index < 42; index += 1) {
    const cellDate = new Date(gridStart)
    cellDate.setDate(gridStart.getDate() + index)
    const dateKey = toDateKey(cellDate)

    cells.push({
      key: `${monthDate.getFullYear()}-${monthDate.getMonth()}-${index}`,
      dateKey,
      dayNumber: cellDate.getDate(),
      inCurrentMonth: cellDate.getMonth() === monthDate.getMonth(),
      isToday: dateKey === todayDateKey,
      isSelected: dateKey === selectedDateKey,
      hasNote: noteDates.has(dateKey)
    })
  }

  return cells
}

function shiftMonth(offset: number) {
  const nextMonth = new Date(currentMonth.value.getFullYear(), currentMonth.value.getMonth() + offset, 1)
  currentMonth.value = nextMonth

  const selected = parseDateKey(selectedDate.value)
  const monthLastDay = new Date(nextMonth.getFullYear(), nextMonth.getMonth() + 1, 0).getDate()
  const nextDate = new Date(
    nextMonth.getFullYear(),
    nextMonth.getMonth(),
    Math.min(selected.getDate(), monthLastDay)
  )
  selectedDate.value = toDateKey(nextDate)
}

function selectCalendarDate(cell: CalendarCell) {
  selectedDate.value = cell.dateKey

  if (!cell.inCurrentMonth) {
    currentMonth.value = startOfMonth(parseDateKey(cell.dateKey))
  }
}

function jumpToToday() {
  selectedDate.value = todayKey
  currentMonth.value = startOfMonth(parseDateKey(todayKey))
}

async function openSelectedDailyNote() {
  dailyOpening.value = true

  try {
    const note = await workspaceStore.openDailyNote(selectedDate.value)
    noteWorkspaceStore.openNoteTab(note.id, note.title)
    await router.push(buildNoteEditRoute(note.id))
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '打开每日日记失败')
  } finally {
    dailyOpening.value = false
  }
}

function openRecentNote(noteId: number) {
  void router.push(buildNoteEditRoute(noteId))
}

function openInspirationNote(noteId: number) {
  void router.push(buildNoteEditRoute(noteId))
}

function createNote() {
  void router.push(buildDraftNoteRoute())
}

function continueWorkspace() {
  if (!activeWorkspaceTab.value) {
    void router.push('/folder')
    return
  }

  void router.push(buildWorkspaceTabRoute(activeWorkspaceTab.value))
}

function runPrimaryStartAction() {
  if (workspaceDirtyCount.value > 0 || activeWorkspaceTab.value) {
    continueWorkspace()
    return
  }

  const [latestNote] = recentNotes.value

  if (latestNote) {
    openRecentNote(latestNote.id)
    return
  }

  createNote()
}

function readDashboardWidgetOrder(): DashboardWidgetKey[] {
  try {
    const parsed = JSON.parse(window.localStorage.getItem(DASHBOARD_WIDGET_ORDER_STORAGE_KEY) || 'null') as unknown

    if (Array.isArray(parsed)) {
      return normalizeDashboardWidgetOrder(parsed)
    }
  } catch {
    return [...DASHBOARD_WIDGET_KEYS]
  }

  return [...DASHBOARD_WIDGET_KEYS]
}

function normalizeDashboardWidgetOrder(order: unknown[]): DashboardWidgetKey[] {
  const normalized = order.filter(isDashboardWidgetKey)

  DASHBOARD_WIDGET_KEYS.forEach((key) => {
    if (!normalized.includes(key)) {
      normalized.push(key)
    }
  })

  return normalized
}

function isDashboardWidgetKey(value: unknown): value is DashboardWidgetKey {
  return typeof value === 'string' && DASHBOARD_WIDGET_KEYS.includes(value as DashboardWidgetKey)
}

function resetDashboardWidgetOrder() {
  dashboardWidgetOrder.value = [...DASHBOARD_WIDGET_KEYS]
}

function startDashboardWidgetDrag(event: PointerEvent, key: DashboardWidgetKey) {
  if (event.button !== 0) {
    return
  }

  dashboardWidgetDrag = {
    key,
    pointerId: event.pointerId,
    startX: event.clientX,
    startY: event.clientY
  }
  dashboardWidgetDragKey.value = key
  dashboardWidgetDidDrag = false

  const target = event.currentTarget as HTMLElement | null
  target?.setPointerCapture?.(event.pointerId)
  document.addEventListener('pointermove', handleDashboardWidgetPointerMove)
  document.addEventListener('pointerup', stopDashboardWidgetDrag, { once: true })
  document.addEventListener('pointercancel', stopDashboardWidgetDrag, { once: true })
}

function handleDashboardWidgetPointerMove(event: PointerEvent) {
  if (!dashboardWidgetDrag || event.pointerId !== dashboardWidgetDrag.pointerId) {
    return
  }

  const deltaX = event.clientX - dashboardWidgetDrag.startX
  const deltaY = event.clientY - dashboardWidgetDrag.startY

  if (Math.abs(deltaX) + Math.abs(deltaY) <= 8 && !dashboardWidgetDidDrag) {
    return
  }

  dashboardWidgetDidDrag = true
  event.preventDefault()

  const target = document.elementFromPoint(event.clientX, event.clientY) as HTMLElement | null
  const overKey = target?.closest<HTMLElement>('[data-dashboard-widget-key]')?.dataset.dashboardWidgetKey

  if (isDashboardWidgetKey(overKey) && overKey !== dashboardWidgetDrag.key) {
    moveDashboardWidget(dashboardWidgetDrag.key, overKey)
  }
}

function moveDashboardWidget(sourceKey: DashboardWidgetKey, targetKey: DashboardWidgetKey) {
  const nextOrder = [...dashboardWidgetOrder.value]
  const sourceIndex = nextOrder.indexOf(sourceKey)
  const targetIndex = nextOrder.indexOf(targetKey)

  if (sourceIndex < 0 || targetIndex < 0 || sourceIndex === targetIndex) {
    return
  }

  nextOrder.splice(sourceIndex, 1)
  nextOrder.splice(targetIndex, 0, sourceKey)
  dashboardWidgetOrder.value = nextOrder
}

function stopDashboardWidgetDrag() {
  if (dashboardWidgetDidDrag) {
    suppressDashboardWidgetClick = true
    window.setTimeout(() => {
      suppressDashboardWidgetClick = false
    }, 0)
  }

  teardownDashboardWidgetDrag()
}

function teardownDashboardWidgetDrag() {
  dashboardWidgetDrag = null
  dashboardWidgetDragKey.value = null
  document.removeEventListener('pointermove', handleDashboardWidgetPointerMove)
  document.removeEventListener('pointerup', stopDashboardWidgetDrag)
  document.removeEventListener('pointercancel', stopDashboardWidgetDrag)
}

function runDashboardWidgetAction(widget: DashboardWidget) {
  if (suppressDashboardWidgetClick) {
    return
  }

  void widget.action()
}

function togglePanel(panel: PanelKey) {
  panelState[panel] = !panelState[panel]
}

function jumpToDashboardSection(sectionId: string) {
  document.getElementById(sectionId)?.scrollIntoView({
    behavior: 'smooth',
    block: 'start'
  })
}

function openPanelAndJump(panel: PanelKey, sectionId: string) {
  panelState[panel] = true
  window.requestAnimationFrame(() => {
    jumpToDashboardSection(sectionId)
  })
}

function getPanelToggleSymbol(panel: PanelKey) {
  return panelState[panel] ? '-' : '+'
}

function getPanelToggleTitle(panel: PanelKey) {
  return panelState[panel] ? '收起面板' : '展开面板'
}
</script>

<template>
  <div class="dashboard-view page-shell">
    <section class="dashboard-widget-board panel" aria-label="首页快捷组件">
      <div class="dashboard-widget-board__head">
        <div>
          <strong>首页快捷组件</strong>
          <span>点击进入，按住拖动可以交换位置。</span>
        </div>
        <button type="button" class="dashboard-widget-board__reset" @click="resetDashboardWidgetOrder">重置</button>
      </div>

      <div class="dashboard-widget-board__grid">
        <button
          v-for="widget in orderedDashboardWidgets"
          :key="widget.key"
          type="button"
          class="dashboard-widget-tile"
          :class="[
            widget.tone ? `is-${widget.tone}` : '',
            dashboardWidgetDragKey === widget.key ? 'is-dragging' : ''
          ]"
          :data-dashboard-widget-key="widget.key"
          @dragstart.prevent
          @pointerdown="startDashboardWidgetDrag($event, widget.key)"
          @click="runDashboardWidgetAction(widget)"
        >
          <span class="dashboard-widget-tile__drag">拖动</span>
          <span class="dashboard-widget-tile__label">{{ widget.label }}</span>
          <strong>{{ widget.value }}</strong>
          <small class="dashboard-widget-tile__meta">{{ widget.meta }}</small>
          <span class="dashboard-widget-tile__action">{{ widget.actionLabel }}</span>
        </button>
      </div>
    </section>

    <nav class="dashboard-mobile-jump panel" aria-label="仪表盘快捷跳转">
      <button type="button" @click="jumpToDashboardSection('dashboard-workspace')">工作区</button>
      <button type="button" @click="openPanelAndJump('recent', 'dashboard-recent')">最近</button>
      <button type="button" @click="openPanelAndJump('daily', 'dashboard-daily')">日记</button>
      <button type="button" @click="openPanelAndJump('stats', 'dashboard-stats')">指标</button>
      <button type="button" @click="openPanelAndJump('inspiration', 'dashboard-inspiration')">灵感</button>
    </nav>

    <section id="dashboard-workspace" class="dashboard-start panel">
      <div class="dashboard-start__copy">
        <span class="section-kicker">Start Here</span>
        <h2>先做一件明确的事</h2>
        <p>{{ startSummary }}</p>

        <div class="dashboard-start__actions">
          <el-button type="primary" @click="runPrimaryStartAction">{{ primaryStartLabel }}</el-button>
          <el-button plain :loading="dailyOpening" @click="openSelectedDailyNote">
            {{ selectedDateHasNote ? '打开今天的日记' : '写今天的日记' }}
          </el-button>
          <el-button plain @click="createNote">新建草稿</el-button>
        </div>
      </div>

      <div class="dashboard-start__cards" aria-label="当前状态">
        <article>
          <span>工作区</span>
          <strong>{{ workspaceOpenCount }}</strong>
          <small>{{ workspaceDirtyCount ? `${workspaceDirtyCount} 个未保存` : '都已同步' }}</small>
        </article>
        <article>
          <span>最近笔记</span>
          <strong>{{ recentNotes.length }}</strong>
          <small>{{ recentNotes[0]?.title || '暂无最近内容' }}</small>
        </article>
        <article>
          <span>今日日记</span>
          <strong>{{ selectedDateHasNote ? '已写' : '待写' }}</strong>
          <small>{{ formatDateOnly(todayKey) }}</small>
        </article>
      </div>
    </section>

    <section id="dashboard-daily" class="dashboard-daily panel">
      <div class="dashboard-panel__title-row">
        <div>
          <span class="section-kicker">Daily Notes</span>
          <h3 class="section-title">每日日记</h3>
          <small class="dashboard-panel__meta">把当天的碎片想法、推进记录和复盘线索，沉淀成一篇可持续续写的日记。</small>
        </div>

        <button
          type="button"
          class="dashboard-panel__toggle"
          :aria-expanded="panelState.daily"
          :title="getPanelToggleTitle('daily')"
          @click="togglePanel('daily')"
        >
          {{ getPanelToggleSymbol('daily') }}
        </button>
      </div>

      <transition name="dashboard-panel-collapse">
        <div v-if="panelState.daily" class="dashboard-panel__body dashboard-daily__body">
          <section class="dashboard-daily__focus">
            <div class="dashboard-daily__focus-card">
              <span class="dashboard-daily__eyebrow">{{ selectedDateBadge }}</span>
              <strong>{{ selectedDateLabel }}</strong>
              <p>{{ selectedDateSummary }}</p>

              <div class="dashboard-daily__metrics">
                <article class="dashboard-daily__metric">
                  <span>本月记录</span>
                  <strong>{{ monthRecordedCount }}</strong>
                  <small>已经写过的天数</small>
                </article>
                <article class="dashboard-daily__metric">
                  <span>当前状态</span>
                  <strong>{{ selectedDateHasNote ? '可续写' : '待创建' }}</strong>
                  <small>{{ selectedDate === todayKey ? '今天' : '所选日期' }}</small>
                </article>
              </div>

              <div class="dashboard-daily__actions">
                <el-button type="primary" :loading="dailyOpening" @click="openSelectedDailyNote">
                  {{ selectedDateHasNote ? '打开所选日记' : '为所选日期创建日记' }}
                </el-button>
                <el-button plain @click="jumpToToday">回到今天</el-button>
              </div>
            </div>

            <div class="dashboard-daily__highlights">
              <div class="dashboard-daily__highlight">
                <span class="section-kicker">Month Pulse</span>
                <strong>{{ monthLabel }}</strong>
                <p>日历上的圆点代表这一天已经有每日日记，点击任意日期都可以快速进入写作。</p>
              </div>

              <div class="dashboard-daily__chips">
                <span v-if="!monthRecordedPreview.length" class="dashboard-daily__chip dashboard-daily__chip--muted">
                  本月还没有记录
                </span>
                <span
                  v-for="date in monthRecordedPreview"
                  :key="date"
                  class="dashboard-daily__chip"
                >
                  {{ formatDateOnly(date) }}
                </span>
              </div>
            </div>
          </section>

          <section class="dashboard-daily__calendar panel">
            <div class="dashboard-daily__calendar-head">
              <div>
                <span class="section-kicker">Calendar</span>
                <strong>{{ monthLabel }}</strong>
              </div>

              <div class="dashboard-daily__calendar-nav">
                <button type="button" class="dashboard-calendar__nav-btn" @click="shiftMonth(-1)">上月</button>
                <button type="button" class="dashboard-calendar__nav-btn" @click="shiftMonth(1)">下月</button>
              </div>
            </div>

            <div class="dashboard-calendar">
              <span
                v-for="weekday in ['一', '二', '三', '四', '五', '六', '日']"
                :key="weekday"
                class="dashboard-calendar__weekday"
              >
                {{ weekday }}
              </span>

              <button
                v-for="cell in calendarCells"
                :key="cell.key"
                type="button"
                class="dashboard-calendar__cell"
                :class="{
                  'is-current-month': cell.inCurrentMonth,
                  'is-outside-month': !cell.inCurrentMonth,
                  'is-selected': cell.isSelected,
                  'is-today': cell.isToday,
                  'has-note': cell.hasNote
                }"
                @click="selectCalendarDate(cell)"
              >
                <span class="dashboard-calendar__day">{{ cell.dayNumber }}</span>
                <span v-if="cell.hasNote" class="dashboard-calendar__dot" />
              </button>
            </div>

            <div class="dashboard-daily__calendar-foot">
              <span class="dashboard-daily__legend">
                <i class="dashboard-calendar__dot" />
                已写每日日记
              </span>
              <span v-if="workspaceStore.dailyNoteLoading" class="dashboard-daily__legend dashboard-daily__legend--muted">
                正在加载本月记录…
              </span>
            </div>
          </section>
        </div>
      </transition>
    </section>

    <section id="dashboard-stats" class="dashboard-overview panel">
      <div class="dashboard-panel__title-row">
        <div>
          <span class="section-kicker">Overview</span>
          <h3 class="section-title">关键指标</h3>
          <small class="dashboard-panel__meta">先看整体规模，再决定今天优先推进哪一块内容。</small>
        </div>

        <button
          type="button"
          class="dashboard-panel__toggle"
          :aria-expanded="panelState.stats"
          :title="getPanelToggleTitle('stats')"
          @click="togglePanel('stats')"
        >
          {{ getPanelToggleSymbol('stats') }}
        </button>
      </div>

      <transition name="dashboard-panel-collapse">
        <div v-if="panelState.stats" class="dashboard-panel__body">
          <div class="grid-three dashboard-overview__grid">
            <article v-for="item in stats" :key="item.label" class="metric-card panel">
              <span class="section-kicker">{{ item.label }}</span>
              <strong>{{ item.value }}</strong>
              <span>{{ item.note }}</span>
            </article>
          </div>
        </div>
      </transition>
    </section>

    <section class="grid-two">
      <article id="dashboard-inspiration" class="dashboard-card panel">
        <div class="dashboard-panel__title-row">
          <div>
            <span class="section-kicker">Tag Radar</span>
            <h3 class="section-title">标签分布</h3>
            <small class="dashboard-panel__meta">高频主题会逐渐沉淀成你的长期知识主轴。</small>
          </div>

          <button
            type="button"
            class="dashboard-panel__toggle"
            :aria-expanded="panelState.tags"
            :title="getPanelToggleTitle('tags')"
            @click="togglePanel('tags')"
          >
            {{ getPanelToggleSymbol('tags') }}
          </button>
        </div>

        <transition name="dashboard-panel-collapse">
          <div v-if="panelState.tags" class="dashboard-panel__body">
            <EChartPanel :option="tagDistributionOption" height="340px" :loading="workspaceStore.dashboardLoading" />
          </div>
        </transition>
      </article>

      <article id="dashboard-recent" class="dashboard-card panel">
        <div class="dashboard-panel__title-row">
          <div>
            <span class="section-kicker">Edit Pulse</span>
            <h3 class="section-title">编辑热力图</h3>
            <small class="dashboard-panel__meta">帮你回看近期最活跃、最值得继续推进的写作时段。</small>
          </div>

          <button
            type="button"
            class="dashboard-panel__toggle"
            :aria-expanded="panelState.heatmap"
            :title="getPanelToggleTitle('heatmap')"
            @click="togglePanel('heatmap')"
          >
            {{ getPanelToggleSymbol('heatmap') }}
          </button>
        </div>

        <transition name="dashboard-panel-collapse">
          <div v-if="panelState.heatmap" class="dashboard-panel__body">
            <EChartPanel :option="heatmapOption" height="340px" :loading="workspaceStore.dashboardLoading" />
          </div>
        </transition>
      </article>
    </section>

    <section class="grid-two">
      <article class="dashboard-card panel">
        <div class="dashboard-panel__title-row">
          <div>
            <span class="section-kicker">Inspiration</span>
            <h3 class="section-title">每日灵感推荐</h3>
            <small class="dashboard-panel__meta">根据近期编辑痕迹整理出的轻量提示，适合用来发散和起草。</small>
          </div>

          <button
            type="button"
            class="dashboard-panel__toggle"
            :aria-expanded="panelState.inspiration"
            :title="getPanelToggleTitle('inspiration')"
            @click="togglePanel('inspiration')"
          >
            {{ getPanelToggleSymbol('inspiration') }}
          </button>
        </div>

        <transition name="dashboard-panel-collapse">
          <div v-if="panelState.inspiration" class="dashboard-panel__body">
            <div v-if="workspaceStore.inspiration" class="inspiration-card">
              <blockquote>{{ workspaceStore.inspiration.inspirationQuote }}</blockquote>
              <p v-if="workspaceStore.inspiration.matchSummary" class="inspiration-card__summary">
                {{ workspaceStore.inspiration.matchSummary }}
              </p>

              <div class="inspiration-card__tags">
                <el-tag v-for="tag in workspaceStore.inspiration.recommendedTags" :key="tag" effect="plain">
                  #{{ tag }}
                </el-tag>
              </div>

              <div v-if="inspirationPrompts.length" class="inspiration-card__prompts">
                <span class="section-kicker">可以马上写</span>
                <ol>
                  <li v-for="prompt in inspirationPrompts" :key="prompt">{{ prompt }}</li>
                </ol>
              </div>

              <div v-if="inspirationRecommendations.length" class="inspiration-card__matches">
                <article
                  v-for="item in inspirationRecommendations"
                  :key="item.noteId"
                  class="inspiration-card__match"
                >
                  <div class="inspiration-card__match-head">
                    <div>
                      <strong>{{ item.title }}</strong>
                      <p>{{ item.reason }}</p>
                    </div>
                    <span>匹配度 {{ item.score }}</span>
                  </div>

                  <div class="inspiration-card__match-foot">
                    <div class="inspiration-card__match-tags">
                      <span v-for="tag in item.matchedTags" :key="`${item.noteId}-${tag}`">#{{ tag }}</span>
                    </div>
                    <el-button type="primary" plain @click="openInspirationNote(item.noteId)">打开笔记</el-button>
                  </div>

                  <small v-if="item.updateTime">最近更新 {{ relativeTime(item.updateTime) }}</small>
                </article>
              </div>

              <div class="inspiration-card__meta">
                <span>{{ formatDateOnly(workspaceStore.inspiration.date) }}</span>
                <span>关联笔记 {{ workspaceStore.inspiration.relatedNotes.length }} 篇</span>
              </div>
            </div>

            <div v-else class="empty-state">等你积累更多编辑行为后，这里会逐步出现更贴近当前主题的灵感建议。</div>
          </div>
        </transition>
      </article>

      <article class="dashboard-card panel">
        <div class="dashboard-panel__title-row">
          <div>
            <span class="section-kicker">Recent Notes</span>
            <h3 class="section-title">最近更新</h3>
            <small class="dashboard-panel__meta">直接回到你最近处理过的内容，不用再回文件夹里逐层查找。</small>
          </div>

          <button
            type="button"
            class="dashboard-panel__toggle"
            :aria-expanded="panelState.recent"
            :title="getPanelToggleTitle('recent')"
            @click="togglePanel('recent')"
          >
            {{ getPanelToggleSymbol('recent') }}
          </button>
        </div>

        <transition name="dashboard-panel-collapse">
          <div v-if="panelState.recent" class="dashboard-panel__body">
            <div v-if="recentNotes.length" class="recent-list">
              <article v-for="note in recentNotes" :key="note.id" class="recent-list__item">
                <div class="recent-list__copy">
                  <strong>{{ note.title }}</strong>
                  <span>{{ note.folderName || (note.dailyNoteDate ? '每日日记' : '未归档文件夹') }}</span>
                </div>

                <div class="recent-list__actions">
                  <small>{{ relativeTime(note.updateTime) }}</small>
                  <el-button type="primary" plain @click="openRecentNote(note.id)">继续编辑</el-button>
                </div>
              </article>
            </div>

            <div v-else class="empty-state">还没有笔记内容，先写下今天的第一条灵感吧。</div>
          </div>
        </transition>
      </article>
    </section>
  </div>
</template>

<style scoped>
.dashboard-view,
.dashboard-start,
.dashboard-start__copy,
.dashboard-workspace,
.dashboard-daily,
.dashboard-overview,
.dashboard-card,
.dashboard-panel__body,
.dashboard-daily__focus,
.dashboard-daily__focus-card,
.dashboard-daily__metrics,
.dashboard-daily__highlights,
.dashboard-workspace__copy,
.recent-list,
.recent-list__copy,
.inspiration-card {
  display: grid;
  gap: 12px;
}

.dashboard-mobile-jump {
  display: none;
}

.dashboard-widget-board {
  display: grid;
  gap: 10px;
  padding: 14px;
  background:
    radial-gradient(circle at top right, rgba(184, 92, 56, 0.12), transparent 34%),
    linear-gradient(135deg, rgba(255, 252, 247, 0.98), rgba(247, 241, 232, 0.9));
}

.dashboard-widget-board__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
}

.dashboard-widget-board__head > div {
  display: grid;
  min-width: 0;
  gap: 2px;
}

.dashboard-widget-board__head strong {
  font-family: var(--header-font);
  font-size: 1.08rem;
}

.dashboard-widget-board__head span {
  overflow: hidden;
  color: var(--text-soft);
  font-size: 0.86rem;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.dashboard-widget-board__reset {
  flex: 0 0 auto;
  min-height: 32px;
  padding: 0 12px;
  border: 1px solid rgba(184, 92, 56, 0.16);
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.72);
  color: #8d4529;
  cursor: pointer;
  font-size: 0.82rem;
  font-weight: 800;
}

.dashboard-widget-board__grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 8px;
}

.dashboard-widget-tile {
  position: relative;
  display: grid;
  align-content: start;
  min-width: 0;
  min-height: 92px;
  gap: 3px;
  padding: 10px;
  border: 1px solid rgba(184, 92, 56, 0.12);
  border-radius: 16px;
  background:
    radial-gradient(circle at top right, rgba(184, 92, 56, 0.08), transparent 40%),
    rgba(255, 255, 255, 0.72);
  color: inherit;
  cursor: grab;
  text-align: left;
  touch-action: none;
  user-select: none;
  transition:
    transform 0.16s ease,
    border-color 0.16s ease,
    box-shadow 0.16s ease,
    background-color 0.16s ease;
}

.dashboard-widget-tile:hover {
  transform: translateY(-1px);
  border-color: rgba(141, 69, 41, 0.28);
  box-shadow: 0 12px 26px rgba(93, 63, 40, 0.08);
}

.dashboard-widget-tile:active,
.dashboard-widget-tile.is-dragging {
  cursor: grabbing;
}

.dashboard-widget-tile.is-dragging {
  z-index: 2;
  transform: scale(0.98);
  border-color: rgba(54, 92, 75, 0.34);
  box-shadow: 0 16px 34px rgba(54, 92, 75, 0.14);
}

.dashboard-widget-tile.is-accent {
  border-color: rgba(184, 92, 56, 0.18);
  background:
    radial-gradient(circle at top right, rgba(184, 92, 56, 0.16), transparent 42%),
    rgba(255, 255, 255, 0.78);
}

.dashboard-widget-tile.is-moss {
  border-color: rgba(54, 92, 75, 0.18);
  background:
    radial-gradient(circle at top right, rgba(54, 92, 75, 0.14), transparent 42%),
    rgba(255, 255, 255, 0.74);
}

.dashboard-widget-tile.is-gold {
  border-color: rgba(197, 157, 88, 0.22);
  background:
    radial-gradient(circle at top right, rgba(197, 157, 88, 0.18), transparent 42%),
    rgba(255, 255, 255, 0.74);
}

.dashboard-widget-tile__drag {
  justify-self: start;
  padding: 2px 7px;
  border-radius: 999px;
  background: rgba(54, 92, 75, 0.08);
  color: #365c4b;
  font-size: 0.68rem;
  font-weight: 900;
  letter-spacing: 0.08em;
}

.dashboard-widget-tile__label,
.dashboard-widget-tile__meta {
  overflow: hidden;
  color: var(--text-soft);
  text-overflow: ellipsis;
  white-space: nowrap;
}

.dashboard-widget-tile__label {
  font-size: 0.76rem;
  font-weight: 800;
}

.dashboard-widget-tile strong {
  overflow: hidden;
  color: var(--accent-strong);
  font-size: clamp(1rem, 1.8vw, 1.22rem);
  line-height: 1.05;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.dashboard-widget-tile__meta {
  font-size: 0.72rem;
}

.dashboard-widget-tile__action {
  margin-top: 2px;
  color: #8d4529;
  font-size: 0.72rem;
  font-weight: 800;
}

.dashboard-workspace,
.dashboard-start,
.dashboard-daily,
.dashboard-overview,
.dashboard-card {
  padding: 18px;
}

.dashboard-start {
  grid-template-columns: minmax(0, 1fr) minmax(300px, 0.9fr);
  align-items: stretch;
  background:
    radial-gradient(circle at top right, rgba(54, 92, 75, 0.14), transparent 32%),
    linear-gradient(135deg, rgba(255, 252, 247, 0.96), rgba(247, 241, 232, 0.88));
}

.dashboard-start__copy h2 {
  margin: 0;
  font-family: var(--header-font);
  font-size: clamp(1.55rem, 3vw, 2.4rem);
  line-height: 1.15;
}

.dashboard-start__copy p {
  max-width: 680px;
  margin: 0;
  color: var(--text-soft);
  line-height: 1.58;
}

.dashboard-start__actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.dashboard-start__cards {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 10px;
  align-content: stretch;
}

.dashboard-start__cards article {
  display: grid;
  align-content: start;
  gap: 5px;
  min-width: 0;
  padding: 14px;
  border: 1px solid rgba(54, 92, 75, 0.12);
  border-radius: 16px;
  background: rgba(255, 255, 255, 0.64);
}

.dashboard-start__cards span,
.dashboard-start__cards small {
  overflow: hidden;
  color: var(--text-soft);
  text-overflow: ellipsis;
  white-space: nowrap;
}

.dashboard-start__cards strong {
  overflow: hidden;
  color: var(--text);
  font-size: 1.28rem;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.dashboard-workspace {
  grid-template-columns: minmax(0, 1.35fr) repeat(2, minmax(0, 0.72fr));
  align-items: stretch;
  background:
    radial-gradient(circle at top right, rgba(197, 157, 88, 0.14), transparent 40%),
    linear-gradient(180deg, rgba(255, 252, 248, 0.92), rgba(255, 247, 239, 0.84));
}

.dashboard-daily {
  background:
    radial-gradient(circle at top left, rgba(54, 92, 75, 0.12), transparent 38%),
    linear-gradient(145deg, rgba(255, 250, 244, 0.94), rgba(248, 241, 232, 0.88));
}

.dashboard-daily__body {
  grid-template-columns: minmax(0, 1.08fr) minmax(0, 0.92fr);
  align-items: stretch;
}

.dashboard-daily__focus-card {
  padding: 18px;
  border-radius: 18px;
  background:
    linear-gradient(150deg, rgba(255, 255, 255, 0.94), rgba(250, 239, 224, 0.9)),
    rgba(255, 255, 255, 0.8);
  border: 1px solid rgba(184, 92, 56, 0.12);
  box-shadow: 0 18px 36px rgba(93, 63, 40, 0.08);
}

.dashboard-daily__focus-card strong {
  font-family: var(--header-font);
  font-size: clamp(1.45rem, 2.6vw, 2.1rem);
  line-height: 1.12;
}

.dashboard-daily__focus-card p,
.dashboard-panel__meta,
.dashboard-start__copy p,
.dashboard-workspace__copy p,
.recent-list__copy span,
.recent-list__actions small,
.inspiration-card__summary,
.inspiration-card__match p,
.inspiration-card__match small,
.inspiration-card__meta,
.dashboard-daily__highlight p {
  color: var(--text-soft);
}

.dashboard-daily__eyebrow {
  display: inline-flex;
  width: fit-content;
  padding: 6px 11px;
  border-radius: 999px;
  background: rgba(184, 92, 56, 0.12);
  color: #8d4529;
  font-size: 0.82rem;
  letter-spacing: 0.06em;
}

.dashboard-daily__metrics {
  grid-template-columns: repeat(2, minmax(0, 1fr));
}

.dashboard-daily__metric,
.dashboard-workspace__stat,
.dashboard-daily__highlight,
.inspiration-card__match,
.recent-list__item {
  padding: 14px 16px;
  border-radius: 16px;
  border: 1px solid rgba(184, 92, 56, 0.12);
  background: rgba(255, 255, 255, 0.68);
}

.dashboard-daily__metric span,
.dashboard-workspace__stat span {
  color: var(--text-soft);
}

.dashboard-daily__metric strong,
.dashboard-workspace__stat strong {
  display: block;
  margin-top: 5px;
  font-size: 1.35rem;
}

.dashboard-daily__actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.dashboard-daily__chips {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.dashboard-daily__chip {
  display: inline-flex;
  align-items: center;
  padding: 6px 10px;
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.78);
  border: 1px solid rgba(184, 92, 56, 0.14);
  color: #7a5d47;
  font-size: 0.9rem;
}

.dashboard-daily__chip--muted {
  color: var(--text-soft);
}

.dashboard-daily__calendar {
  display: grid;
  gap: 12px;
  padding: 16px;
  border-radius: 18px;
  background:
    radial-gradient(circle at top right, rgba(197, 157, 88, 0.12), transparent 34%),
    rgba(255, 255, 255, 0.82);
  border: 1px solid rgba(184, 92, 56, 0.12);
}

.dashboard-daily__calendar-head,
.dashboard-workspace__actions,
.dashboard-panel__title-row,
.inspiration-card__meta,
.inspiration-card__match-head,
.inspiration-card__match-foot,
.recent-list__item,
.recent-list__actions,
.dashboard-daily__calendar-foot,
.dashboard-daily__calendar-nav {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 10px;
}

.dashboard-daily__calendar-head strong {
  display: block;
  margin-top: 6px;
  font-size: 1.15rem;
}

.dashboard-calendar {
  display: grid;
  grid-template-columns: repeat(7, minmax(0, 1fr));
  gap: 6px;
}

.dashboard-calendar__weekday {
  text-align: center;
  color: var(--text-soft);
  font-size: 0.88rem;
}

.dashboard-calendar__cell {
  position: relative;
  min-height: 50px;
  padding: 8px 6px;
  border: 1px solid rgba(184, 92, 56, 0.1);
  border-radius: 14px;
  background: rgba(255, 255, 255, 0.8);
  color: var(--text);
  cursor: pointer;
  transition:
    transform 0.2s ease,
    border-color 0.2s ease,
    box-shadow 0.2s ease,
    background-color 0.2s ease;
}

.dashboard-calendar__cell:hover {
  transform: translateY(-1px);
  border-color: rgba(184, 92, 56, 0.28);
  box-shadow: 0 10px 20px rgba(93, 63, 40, 0.08);
}

.dashboard-calendar__cell.is-outside-month {
  color: rgba(102, 88, 74, 0.48);
  background: rgba(250, 247, 242, 0.72);
}

.dashboard-calendar__cell.is-selected {
  border-color: rgba(184, 92, 56, 0.58);
  background: linear-gradient(180deg, rgba(255, 243, 234, 0.94), rgba(255, 255, 255, 0.9));
  box-shadow: 0 14px 26px rgba(184, 92, 56, 0.12);
}

.dashboard-calendar__cell.is-today .dashboard-calendar__day {
  color: #8d4529;
}

.dashboard-calendar__cell.has-note::after {
  content: '';
  position: absolute;
  inset: auto 8px 8px auto;
  width: 24px;
  height: 24px;
  border-radius: 50%;
  background: rgba(184, 92, 56, 0.08);
}

.dashboard-calendar__day {
  position: relative;
  z-index: 1;
  font-size: 1rem;
  font-weight: 600;
}

.dashboard-calendar__dot {
  position: absolute;
  right: 14px;
  bottom: 14px;
  z-index: 1;
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: #b85c38;
  box-shadow: 0 0 0 4px rgba(184, 92, 56, 0.12);
}

.dashboard-calendar__nav-btn,
.dashboard-panel__toggle {
  border: 1px solid rgba(184, 92, 56, 0.2);
  background: rgba(255, 255, 255, 0.78);
  color: #8d4529;
  cursor: pointer;
  transition:
    border-color 0.2s ease,
    background-color 0.2s ease,
    transform 0.2s ease;
}

.dashboard-calendar__nav-btn {
  padding: 7px 11px;
  border-radius: 999px;
  font-size: 0.92rem;
}

.dashboard-calendar__nav-btn:hover,
.dashboard-panel__toggle:hover {
  border-color: rgba(141, 69, 41, 0.42);
  background: rgba(255, 255, 255, 0.96);
  transform: translateY(-1px);
}

.dashboard-daily__legend {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  color: #7a5d47;
  font-size: 0.9rem;
}

.dashboard-daily__legend--muted {
  color: var(--text-soft);
}

.dashboard-workspace__copy p {
  margin: 6px 0 0;
  line-height: 1.55;
}

.dashboard-workspace__stats {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
}

.dashboard-workspace__actions {
  flex-direction: column;
  justify-content: center;
}

.dashboard-overview__grid {
  width: 100%;
}

.dashboard-panel__toggle {
  width: 30px;
  height: 30px;
  border-radius: 999px;
  font-size: 1.08rem;
  line-height: 1;
}

.inspiration-card blockquote {
  margin: 0;
  padding: 14px 16px;
  border-left: 4px solid var(--gold);
  border-radius: 16px;
  background: rgba(255, 255, 255, 0.54);
  color: var(--text);
  font-family: var(--header-font);
  font-size: 1.05rem;
  line-height: 1.55;
}

.inspiration-card__tags {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.inspiration-card__summary,
.inspiration-card__match p {
  margin: 0;
  line-height: 1.55;
}

.inspiration-card__prompts {
  display: grid;
  gap: 8px;
  padding: 12px 14px;
  border: 1px solid rgba(197, 157, 88, 0.22);
  border-radius: 16px;
  background:
    radial-gradient(circle at top left, rgba(197, 157, 88, 0.14), transparent 36%),
    rgba(255, 250, 240, 0.74);
}

.inspiration-card__prompts ol {
  display: grid;
  gap: 6px;
  margin: 0;
  padding-left: 1.2rem;
  color: var(--text);
  line-height: 1.55;
}

.inspiration-card__prompts li::marker {
  color: #8d4529;
  font-weight: 800;
}

.inspiration-card__matches {
  display: grid;
  gap: 8px;
}

.inspiration-card__match {
  display: grid;
  gap: 8px;
  background:
    radial-gradient(circle at top right, rgba(54, 92, 75, 0.08), transparent 34%),
    rgba(255, 255, 255, 0.72);
}

.inspiration-card__match-head strong {
  display: block;
  margin-bottom: 6px;
}

.inspiration-card__match-head > span {
  flex: 0 0 auto;
  padding: 7px 10px;
  border-radius: 999px;
  background: rgba(54, 92, 75, 0.1);
  color: #365c4b;
  font-size: 0.78rem;
  font-weight: 800;
}

.inspiration-card__match-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.inspiration-card__match-tags span {
  display: inline-flex;
  align-items: center;
  min-height: 26px;
  padding: 0 9px;
  border-radius: 999px;
  background: rgba(184, 92, 56, 0.09);
  color: #8d4529;
  font-size: 0.78rem;
}

.recent-list__copy strong {
  display: block;
}

.dashboard-panel-collapse-enter-active,
.dashboard-panel-collapse-leave-active {
  transition: opacity 0.2s ease, transform 0.2s ease;
}

.dashboard-panel-collapse-enter-from,
.dashboard-panel-collapse-leave-to {
  opacity: 0;
  transform: translateY(-6px);
}

@media (max-width: 1180px) {
  .dashboard-start,
  .dashboard-workspace,
  .dashboard-daily__body {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 900px) {
  .dashboard-panel__title-row,
  .dashboard-daily__calendar-head,
  .dashboard-daily__calendar-foot,
  .dashboard-daily__calendar-nav,
  .inspiration-card__meta,
  .inspiration-card__match-head,
  .inspiration-card__match-foot,
  .recent-list__item,
  .recent-list__actions {
    flex-direction: column;
    align-items: flex-start;
  }

  .dashboard-daily__metrics,
  .dashboard-workspace__stats {
    grid-template-columns: 1fr;
  }

  .dashboard-calendar {
    gap: 8px;
  }

  .dashboard-calendar__cell {
    min-height: 56px;
  }
}

@media (max-width: 640px) {
  .dashboard-widget-board {
    gap: 7px;
    padding: 8px;
    border-radius: 16px;
  }

  .dashboard-widget-board__head {
    gap: 8px;
  }

  .dashboard-widget-board__head strong {
    font-size: 0.98rem;
  }

  .dashboard-widget-board__head span {
    max-width: 190px;
    font-size: 0.74rem;
  }

  .dashboard-widget-board__reset {
    min-height: 28px;
    padding: 0 9px;
    font-size: 0.74rem;
  }

  .dashboard-widget-board__grid {
    gap: 6px;
  }

  .dashboard-widget-tile {
    min-height: 72px;
    gap: 2px;
    padding: 7px;
    border-radius: 13px;
  }

  .dashboard-widget-tile__drag {
    padding: 1px 5px;
    font-size: 0.58rem;
  }

  .dashboard-widget-tile__label {
    font-size: 0.68rem;
  }

  .dashboard-widget-tile strong {
    font-size: 0.96rem;
  }

  .dashboard-widget-tile__meta {
    font-size: 0.62rem;
  }

  .dashboard-widget-tile__action {
    overflow: hidden;
    font-size: 0.64rem;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  .dashboard-start {
    display: none;
  }

  .dashboard-mobile-jump {
    display: flex;
    gap: 8px;
    overflow-x: auto;
    padding: 5px;
    border-radius: 16px;
    scrollbar-width: none;
  }

  .dashboard-mobile-jump::-webkit-scrollbar {
    display: none;
  }

  .dashboard-mobile-jump button {
    flex: 0 0 auto;
    min-height: 30px;
    padding: 0 9px;
    border: 1px solid rgba(184, 92, 56, 0.14);
    border-radius: 999px;
    background: rgba(255, 255, 255, 0.74);
    color: #8d4529;
    font-size: 0.8rem;
    font-weight: 700;
  }

  .dashboard-workspace,
  .dashboard-daily,
  .dashboard-overview,
  .dashboard-card {
    padding: 12px;
  }

  .dashboard-panel__meta,
  .dashboard-start__copy p,
  .dashboard-workspace__copy p,
  .dashboard-daily__highlight p {
    display: none;
  }

  .dashboard-start__cards {
    display: flex;
    grid-template-columns: none;
    gap: 8px;
    overflow-x: auto;
    padding-bottom: 2px;
    scrollbar-width: none;
  }

  .dashboard-start__cards::-webkit-scrollbar {
    display: none;
  }

  .dashboard-start__cards article {
    flex: 0 0 154px;
    padding: 12px;
  }

  .dashboard-start__actions {
    align-items: stretch;
  }

  .dashboard-start__actions :deep(.el-button) {
    flex: 1 1 128px;
    min-width: 0;
  }

  .dashboard-workspace {
    gap: 12px;
  }

  .dashboard-workspace__stats {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .dashboard-workspace__stats,
  .dashboard-daily__metrics {
    gap: 8px;
  }

  .dashboard-workspace__stat small,
  .dashboard-daily__metric small {
    display: none;
  }

  .dashboard-workspace__actions,
  .dashboard-daily__actions {
    align-items: stretch;
  }

  .dashboard-workspace__actions :deep(.el-button),
  .dashboard-daily__actions :deep(.el-button),
  .recent-list__actions :deep(.el-button) {
    width: 100%;
  }

  .dashboard-daily__focus-card,
  .dashboard-daily__calendar,
  .dashboard-daily__metric,
  .dashboard-workspace__stat,
  .dashboard-daily__highlight,
  .recent-list__item {
    padding: 12px;
  }

  .dashboard-calendar {
    gap: 6px;
  }

  .dashboard-calendar__cell {
    min-height: 46px;
    padding: 8px 6px;
    border-radius: 14px;
  }

  .dashboard-calendar__cell.has-note::after {
    width: 18px;
    height: 18px;
  }

  .dashboard-calendar__dot {
    right: 10px;
    bottom: 10px;
    width: 6px;
    height: 6px;
  }

  .dashboard-calendar__nav-btn {
    flex: 1 1 96px;
  }
}

@media (max-width: 420px) {
  .dashboard-widget-board__head span,
  .dashboard-widget-tile__meta {
    display: none;
  }

  .dashboard-widget-tile {
    min-height: 62px;
  }

  .dashboard-widget-tile__action {
    font-size: 0.6rem;
  }

  .dashboard-workspace,
  .dashboard-daily,
  .dashboard-overview,
  .dashboard-card {
    padding: 10px;
  }

  .dashboard-calendar {
    gap: 5px;
  }

  .dashboard-calendar__cell {
    min-height: 38px;
    border-radius: 12px;
  }

  .dashboard-calendar__day {
    font-size: 0.88rem;
  }
}
</style>
