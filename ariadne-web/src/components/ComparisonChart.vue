<script setup lang="ts">
import { nextTick, onBeforeUnmount, ref, watch } from 'vue'
import * as echarts from 'echarts'
import type { MaStructure } from '../lib/maStructure'
import { STRUCTURE_SERIES, valuesOf } from '../lib/maStructure'

const props = defineProps<{
  pinned: MaStructure[]
  preview: MaStructure | null
  reducedMotion: boolean
}>()

const emit = defineEmits<{ unpin: [date: string] }>()

const chartEl = ref<HTMLDivElement>()
let chart: echarts.ECharts | undefined

const PIN_COLORS = ['#1b5f9e', '#0a9f98', '#c8434b', '#7a6fd0', '#b8860b', '#1e9e6a']

function render(): void {
  if (!chart) return
  const pinnedSeries = props.pinned.map((structure, index) => ({
    name: structure.date,
    type: 'line' as const,
    data: valuesOf(structure),
    symbol: 'circle',
    symbolSize: 6,
    connectNulls: false,
    lineStyle: { width: 2, color: PIN_COLORS[index % PIN_COLORS.length] },
    itemStyle: { color: PIN_COLORS[index % PIN_COLORS.length] },
    emphasis: { lineStyle: { width: 3 } },
    z: 3,
  }))
  const series = props.preview && !props.pinned.some((p) => p.date === props.preview?.date)
    ? [...pinnedSeries, {
        name: `${props.preview.date}（预览）`,
        type: 'line' as const,
        data: valuesOf(props.preview),
        symbol: 'circle',
        symbolSize: 5,
        connectNulls: false,
        lineStyle: { width: 1.6, type: 'dashed' as const, color: '#5c6d81', opacity: 0.65 },
        itemStyle: { color: '#5c6d81', opacity: 0.65 },
        silent: true,
        z: 2,
      }]
    : pinnedSeries
  chart.setOption({
    animation: !props.reducedMotion,
    animationDuration: 300,
    grid: { left: 12, right: 16, top: 44, bottom: 26, containLabel: true },
    legend: {
      top: 2,
      left: 0,
      type: 'scroll',
      itemGap: 14,
      selectedMode: true,
      textStyle: { color: '#5c6d81', fontSize: 12 },
      pageTextStyle: { color: '#5c6d81' },
      icon: 'roundRect',
      itemWidth: 16,
      itemHeight: 4,
    },
    tooltip: {
      trigger: 'axis',
      confine: true,
      backgroundColor: 'rgba(8, 27, 51, 0.94)',
      borderWidth: 0,
      padding: [8, 12],
      textStyle: { color: '#eef4f8', fontSize: 12 },
    },
    xAxis: {
      type: 'category',
      data: [...STRUCTURE_SERIES],
      boundaryGap: false,
      axisLine: { lineStyle: { color: '#cfdae4' } },
      axisTick: { show: false },
      axisLabel: { color: '#718096', fontSize: 11 },
    },
    yAxis: {
      type: 'value',
      scale: true,
      name: '净值（元）',
      nameTextStyle: { color: '#8a99a8', fontSize: 11, align: 'left' },
      axisLabel: { color: '#718096', fontSize: 11 },
      splitLine: { lineStyle: { color: '#e9eef3' } },
    },
    series,
  }, true)
}

function onLegendSelectChanged(event: { selected: Record<string, boolean> }): void {
  for (const [name, active] of Object.entries(event.selected)) {
    if (active) continue
    if (props.pinned.some((structure) => structure.date === name)) emit('unpin', name)
  }
}

function resize(): void { chart?.resize() }

async function mountChart(el: HTMLDivElement): Promise<void> {
  await nextTick()
  if (chartEl.value !== el) return
  chart = echarts.init(el)
  render()
  chart.on('legendselectchanged', onLegendSelectChanged)
  window.addEventListener('resize', resize)
}

watch(() => [props.pinned, props.preview, props.reducedMotion], render, { deep: true })

watch(chartEl, (el, previous) => {
  if (previous && chart) {
    window.removeEventListener('resize', resize)
    chart.dispose()
    chart = undefined
  }
  if (el) void mountChart(el)
}, { flush: 'post' })

onBeforeUnmount(() => {
  window.removeEventListener('resize', resize)
  chart?.dispose()
  chart = undefined
})
</script>

<template>
  <section class="comparison-card" aria-label="固定日期结构对比">
    <div class="comparison-head">
      <h3>结构对比</h3>
      <p class="comparison-hint">同一交易日按 MA120 → MA60 → MA30 → MA15 → MA5 → 单位净值 展开；点击图例日期可取消固定。</p>
    </div>
    <div ref="chartEl" class="comparison-chart" role="img" aria-label="已固定日期的均线结构对比折线图" />
  </section>
</template>

<style scoped>
.comparison-card {
  margin-top: 14px;
  border: 1px solid var(--line, #dde5ec);
  border-radius: 10px;
  background: #fbfdfe;
  padding: 12px 14px 8px;
}
.comparison-head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 12px;
  flex-wrap: wrap;
  margin-bottom: 4px;
}
.comparison-head h3 {
  margin: 0;
  font-size: 14px;
  color: #10243d;
}
.comparison-hint {
  margin: 0;
  font-size: 11.5px;
  color: #8a99a8;
}
.comparison-chart { width: 100%; height: 230px; }
</style>
