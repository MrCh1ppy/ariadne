<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import * as echarts from 'echarts'
import type { FundAnalysis } from '../types'
import { formatNavTooltip, maColors } from '../lib/formatNavTooltip'

const props = defineProps<{ analysis: FundAnalysis }>()
const chartEl = ref<HTMLDivElement>()
let chart: echarts.ECharts | undefined

const prefersReducedMotion = ref(false)
let motionQuery: MediaQueryList | undefined
let motionListener: ((event: MediaQueryListEvent) => void) | undefined

const isNarrow = ref(false)
let widthQuery: MediaQueryList | undefined
let widthListener: ((event: MediaQueryListEvent) => void) | undefined

const legendLayout = computed(() =>
  isNarrow.value
    ? { top: 4, left: 0, right: 0, type: 'scroll' as const, orient: 'horizontal' as const, itemGap: 8 }
    : { top: 4, right: 4, itemGap: 24 },
)

function render(): void {
  if (!chart) return
  const points = props.analysis.points
  const byDate = new Map(points.map((point) => [point.date, point]))
  chart.setOption({
    animation: !prefersReducedMotion.value,
    animationDuration: 450,
    color: ['#1b5f9e', ...maColors.map(([, color]) => color)],
    grid: { left: 12, right: 20, top: 56, bottom: 32, containLabel: true },
    legend: {
      ...legendLayout.value,
      selected: chart.getOption()?.legend?.[0]?.selected ?? {
        '单位净值': true, MA5: false, MA15: false, MA30: true, MA60: false, MA120: false,
      },
      textStyle: { color: '#5c6d81', fontSize: 12.5 },
      inactiveColor: '#c3cdd6',
      itemWidth: isNarrow.value ? 14 : 18,
      itemHeight: 4,
      icon: 'roundRect',
      pageTextStyle: { color: '#5c6d81' },
    },
    tooltip: {
      trigger: 'axis',
      confine: true,
      backgroundColor: 'rgba(8, 27, 51, 0.94)',
      borderWidth: 0,
      padding: [10, 14],
      textStyle: { color: '#eef4f8', fontSize: 12, lineHeight: 20 },
      axisPointer: {
        type: 'cross',
        crossStyle: { color: '#94a6b8', width: 1, type: 'dashed' },
        lineStyle: { color: '#94a6b8', width: 1, type: 'dashed' },
        label: {
          show: true,
          backgroundColor: '#0b3157',
          color: '#eef4f8',
          fontSize: 11,
          padding: [3, 6],
        },
      },
      formatter: (items: unknown) => {
        const entries = items as Array<{ axisValue: string }>
        const point = byDate.get(entries[0]?.axisValue)
        if (!point) return ''
        return formatNavTooltip(point)
      },
    },
    xAxis: {
      type: 'category',
      name: '交易日',
      nameLocation: 'middle',
      nameGap: 26,
      nameTextStyle: { color: '#8a99a8', fontSize: 11 },
      data: points.map((point) => point.date),
      boundaryGap: false,
      axisLine: { lineStyle: { color: '#cfdae4' } },
      axisTick: { show: false },
      axisLabel: { color: '#718096', fontSize: 11, hideOverlap: true },
    },
    yAxis: {
      type: 'value',
      name: '净值（元）',
      nameTextStyle: { color: '#8a99a8', fontSize: 11, align: 'left' },
      scale: true,
      axisLabel: { color: '#718096', fontSize: 11 },
      splitLine: { lineStyle: { color: '#e9eef3' } },
    },
    series: [
      {
        name: '单位净值',
        type: 'line',
        data: points.map((point) => (point.unitNav === null ? null : Number(point.unitNav))),
        showSymbol: false,
        smooth: false,
        connectNulls: false,
        lineStyle: { width: 2 },
        areaStyle: { color: {
          type: 'linear', x: 0, y: 0, x2: 0, y2: 1,
          colorStops: [
            { offset: 0, color: 'rgba(27, 95, 158, 0.18)' },
            { offset: 1, color: 'rgba(27, 95, 158, 0)' },
          ],
        } },
        emphasis: { lineStyle: { width: 2.5 } },
        z: 2,
      },
      {
        name: 'MA5',
        type: 'line',
        data: points.map((point) => point.movingAverages.MA5?.value == null ? null : Number(point.movingAverages.MA5.value)),
        showSymbol: false,
        smooth: false,
        connectNulls: false,
        lineStyle: { width: 1.5 },
        emphasis: { lineStyle: { width: 3 } },
        z: 4,
      },
      {
        name: 'MA15',
        type: 'line',
        data: points.map((point) => point.movingAverages.MA15?.value == null ? null : Number(point.movingAverages.MA15.value)),
        showSymbol: false,
        smooth: false,
        connectNulls: false,
        lineStyle: { width: 2, type: 'dashed' },
        emphasis: { lineStyle: { width: 3 } },
        z: 3,
      },
      {
        name: 'MA30',
        type: 'line',
        data: points.map((point) => point.movingAverages.MA30?.value == null ? null : Number(point.movingAverages.MA30.value)),
        showSymbol: false,
        smooth: false,
        connectNulls: false,
        lineStyle: { width: 2.5 },
        emphasis: { lineStyle: { width: 4 } },
        z: 3,
      },
      {
        name: 'MA60',
        type: 'line',
        data: points.map((point) => point.movingAverages.MA60?.value == null ? null : Number(point.movingAverages.MA60.value)),
        showSymbol: false,
        smooth: false,
        connectNulls: false,
        lineStyle: { width: 2, type: 'dashed' },
        emphasis: { lineStyle: { width: 3 } },
        z: 1,
      },
      {
        name: 'MA120',
        type: 'line',
        data: points.map((point) => point.movingAverages.MA120?.value == null ? null : Number(point.movingAverages.MA120.value)),
        showSymbol: false,
        smooth: false,
        connectNulls: false,
        lineStyle: { width: 2, type: 'dotted' },
        emphasis: { lineStyle: { width: 3 } },
        z: 1,
      },
    ],
  }, true)
}

function resize(): void { chart?.resize() }

onMounted(async () => {
  motionQuery = window.matchMedia('(prefers-reduced-motion: reduce)')
  prefersReducedMotion.value = motionQuery.matches
  motionListener = (event: MediaQueryListEvent) => { prefersReducedMotion.value = event.matches }
  motionQuery.addEventListener('change', motionListener)

  widthQuery = window.matchMedia('(max-width: 780px)')
  isNarrow.value = widthQuery.matches
  widthListener = (event: MediaQueryListEvent) => { isNarrow.value = event.matches }
  widthQuery.addEventListener('change', widthListener)

  await nextTick()
  if (chartEl.value) chart = echarts.init(chartEl.value)
  render()
  window.addEventListener('resize', resize)
})
watch(() => props.analysis, render, { deep: true })
watch([prefersReducedMotion, isNarrow], render)
onBeforeUnmount(() => {
  window.removeEventListener('resize', resize)
  if (motionQuery && motionListener) motionQuery.removeEventListener('change', motionListener)
  if (widthQuery && widthListener) widthQuery.removeEventListener('change', widthListener)
  chart?.dispose()
})
</script>

<template>
  <div ref="chartEl" class="chart" role="img" aria-label="单位净值与MA5、MA15、MA30、MA60、MA120走势图" />
</template>
