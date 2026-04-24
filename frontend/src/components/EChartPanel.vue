<script setup lang="ts">
import { nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { use } from 'echarts/core'
import { PieChart, GraphChart, HeatmapChart } from 'echarts/charts'
import {
  TooltipComponent,
  VisualMapComponent,
  CalendarComponent,
  GraphicComponent,
  LegendComponent,
  GridComponent
} from 'echarts/components'
import { CanvasRenderer } from 'echarts/renderers'
import { init, type EChartsType } from 'echarts/core'
import type { EChartsOption } from 'echarts'

use([
  PieChart,
  GraphChart,
  HeatmapChart,
  TooltipComponent,
  VisualMapComponent,
  CalendarComponent,
  GraphicComponent,
  LegendComponent,
  GridComponent,
  CanvasRenderer
])

const props = withDefaults(
  defineProps<{
    option: EChartsOption
    height?: string
    loading?: boolean
  }>(),
  {
    height: '320px',
    loading: false
  }
)

const emit = defineEmits<{
  (event: 'chart-click', params: unknown): void
}>()

const chartElement = ref<HTMLDivElement | null>(null)
let chart: EChartsType | null = null

function resizeChart() {
  chart?.resize()
}

function syncLoading() {
  if (!chart) {
    return
  }

  if (props.loading) {
    chart.showLoading('default', {
      text: '图表加载中...'
    })
  } else {
    chart.hideLoading()
  }
}

function renderChart() {
  if (!chartElement.value) {
    return
  }

  if (!chart) {
    chart = init(chartElement.value)
    chart.on('click', (params) => {
      emit('chart-click', params)
    })
  }

  chart.setOption(props.option, true)
  syncLoading()
  resizeChart()
}

onMounted(async () => {
  await nextTick()
  renderChart()
  window.addEventListener('resize', resizeChart)
})

watch(
  () => props.option,
  () => {
    renderChart()
  },
  {
    deep: true
  }
)

watch(
  () => props.loading,
  () => {
    syncLoading()
  }
)

onBeforeUnmount(() => {
  window.removeEventListener('resize', resizeChart)
  chart?.dispose()
  chart = null
})
</script>

<template>
  <div class="chart-shell panel panel--flat">
    <div ref="chartElement" class="chart-shell__canvas" :style="{ height }" />
  </div>
</template>

<style scoped>
.chart-shell {
  min-height: 120px;
}

.chart-shell__canvas {
  width: 100%;
}

@media (max-width: 640px) {
  .chart-shell__canvas {
    min-height: 260px;
  }
}
</style>
