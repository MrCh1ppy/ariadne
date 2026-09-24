<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import * as echarts from 'echarts'
import type { FundAnalysis } from '../types'
import { formatNavTooltip, maColors } from '../lib/formatNavTooltip'
import {
  MAX_PINNED_DATES,
  buildMaStructure,
  hasAnyValue,
  locateCategoryIndex,
  reconcilePinned,
  type MaStructure,
} from '../lib/maStructure'
import StructurePreview from './StructurePreview.vue'
import ComparisonChart from './ComparisonChart.vue'

const props = defineProps<{ analysis: FundAnalysis }>()
const emit = defineEmits<{ notice: [message: string] }>()
const chartEl = ref<HTMLDivElement>()
let chart: echarts.ECharts | undefined

const prefersReducedMotion = ref(false)
let motionQuery: MediaQueryList | undefined
let motionListener: ((event: MediaQueryListEvent) => void) | undefined

const isNarrow = ref(false)
let widthQuery: MediaQueryList | undefined
let widthListener: ((event: MediaQueryListEvent) => void) | undefined

/* Preview is wider than the tooltip to fit the curve and compact comparison table. */
const PREVIEW_TARGET_WIDTH_RATIO = 2

const legendLayout = computed(() =>
  isNarrow.value
    ? { top: 4, left: 0, right: 0, type: 'scroll' as const, orient: 'horizontal' as const, itemGap: 8 }
    : { top: 4, right: 4, itemGap: 24 },
)

/* ---- Structure preview & pinned comparison state ---- */

const hoverDate = ref<string | null>(null)
const previewDate = ref<string | null>(null)
const previewPinnedVisible = ref(false)
const previewHover = ref(false)
const isTouch = ref(false)
const pinnedDates = ref<string[]>([])
const keyboardDate = ref('')
const hideDelay = 620

let previewTimer: ReturnType<typeof setTimeout> | undefined
let hideTimer: ReturnType<typeof setTimeout> | undefined
let pendingDate: string | null = null
let pointerOffset: [number, number] | null = null
let dragStart: [number, number] | null = null
let dragMoved = false
const DRAG_THRESHOLD_PX = 6
let zrMove: ((event: { offsetX: number; offsetY: number }) => void) | undefined
let zrClick: ((event: { offsetX: number; offsetY: number }) => void) | undefined
let zrDown: ((event: { offsetX: number; offsetY: number }) => void) | undefined
let zrOut: (() => void) | undefined

const pointByDate = computed(() => new Map(props.analysis.points.map((point) => [point.date, point])))
const previewDateIsHovered = computed(() => hoverDate.value !== null && hoverDate.value === previewDate.value)

/* measured tooltip width, used to keep the preview at ~2x width */
const tooltipWidth = ref<number | null>(null)
const previewTop = ref(34)
let tooltipSizeMeasured = false

function refreshTooltipSize(): void {
  if (isTouch.value || isNarrow.value) return
  const el = [...document.querySelectorAll<HTMLDivElement>('div')].find((d) =>
    d.style.position === 'absolute' && /\d{4}-\d{2}-\d{2}/.test(d.textContent ?? '') && (d.textContent ?? '').includes('单位净值'),
  )
  if (!el) return
  const rect = el.getBoundingClientRect()
  if (rect.width > 0 && rect.height > 0) {
    tooltipWidth.value = rect.width
    tooltipSizeMeasured = true
  }
}

function computePreviewTop(): void {
  previewTop.value = 34
}

const previewStructure = computed<MaStructure | null>(() => {
  if (!previewDate.value) return null
  const point = pointByDate.value.get(previewDate.value)
  if (!point) return null
  return buildMaStructure(point)
})

const previewVisible = computed(() =>
  previewStructure.value !== null
  && (previewPinnedVisible.value || previewHover.value || (isTouch.value && previewDate.value !== null)),
)

/* Use the measured tooltip width without clipping the chart/table to the now-short tooltip height. */
const previewStyle = computed(() => {
  const style: Record<string, string> = { top: `${previewTop.value}px` }
  if (tooltipWidth.value) style.width = `${Math.max(360, Math.round(tooltipWidth.value * PREVIEW_TARGET_WIDTH_RATIO))}px`
  return style
})

const previewPinned = computed(() =>
  previewDate.value !== null && pinnedDates.value.includes(previewDate.value),
)

const pinnedStructures = computed<MaStructure[]>(() =>
  pinnedDates.value
    .map((date) => pointByDate.value.get(date))
    .filter((point): point is NonNullable<typeof point> => point !== undefined)
    .map((point) => buildMaStructure(point)),
)

const hoverPreviewStructure = computed<MaStructure | null>(() => {
  if (!hoverDate.value || pinnedDates.value.includes(hoverDate.value)) return null
  const point = pointByDate.value.get(hoverDate.value)
  if (!point) return null
  const structure = buildMaStructure(point)
  return hasAnyValue(structure) ? structure : null
})

const pinnedDateSet = computed(() => new Set(pinnedDates.value))
const pinnedFull = computed(() => pinnedDates.value.length >= MAX_PINNED_DATES)

function clearPreviewTimer(): void {
  window.clearTimeout(previewTimer)
  previewTimer = undefined
  pendingDate = null
}

function clearHideTimer(): void {
  window.clearTimeout(hideTimer)
  hideTimer = undefined
}

function resetPreview(): void {
  clearPreviewTimer()
  clearHideTimer()
  previewDate.value = null
  previewPinnedVisible.value = false
  previewHover.value = false
}

function scheduleHide(): void {
  if (isTouch.value) return
  clearPreviewTimer()
  clearHideTimer()
  hideTimer = window.setTimeout(() => {
    if (!previewHover.value) {
      previewPinnedVisible.value = false
      previewDate.value = null
    }
  }, hideDelay)
}

function schedulePreviewReveal(date: string): void {
  if (isTouch.value) return
  const point = pointByDate.value.get(date)
  if (!point || !hasAnyValue(buildMaStructure(point))) {
    resetPreview()
    return
  }
  /* same-date movement must not restart the 1000ms dwell */
  if (previewTimer !== undefined && pendingDate === date) return
  if (previewPinnedVisible.value && previewDate.value === date) {
    /* returning to the same date while the hide timer is running must cancel it */
    clearHideTimer()
    return
  }
  clearPreviewTimer()
  clearHideTimer()
  pendingDate = date
  previewTimer = window.setTimeout(() => {
    previewTimer = undefined
    pendingDate = null
    previewDate.value = date
    previewPinnedVisible.value = true
    refreshTooltipSize()
  }, 1000)
}

function dateAtOffset(offset: [number, number]): string | null {
  if (!chart) return null
  const index = locateCategoryIndex(
    (value) => chart?.containPixel({ gridIndex: 0 }, value) ?? false,
    (value) => {
      const converted = chart?.convertFromPixel({ seriesIndex: 0 }, value)
      const first = Array.isArray(converted) ? converted[0] : converted
      return typeof first === 'number' ? first : null
    },
    offset,
    props.analysis.points.length,
  )
  if (index === null) return null
  return props.analysis.points[index]?.date ?? null
}

function onPointerMove(offsetX: number, offsetY: number): void {
  if (!chart) return
  pointerOffset = [offsetX, offsetY]
  if (isTouch.value) return
  if (dragStart) {
    const dx = offsetX - dragStart[0]
    const dy = offsetY - dragStart[1]
    if (dx * dx + dy * dy > DRAG_THRESHOLD_PX * DRAG_THRESHOLD_PX) {
      dragMoved = true
    }
  }
  const date = dateAtOffset(pointerOffset)
  if (date) {
    hoverDate.value = date
    if (!tooltipSizeMeasured) refreshTooltipSize()
    schedulePreviewReveal(date)
  } else {
    hoverDate.value = null
    /* leaving a valid date always cancels any pending 1s dwell timer */
    clearPreviewTimer()
    if (previewDate.value) scheduleHide()
  }
}

function onPointerDown(offsetX?: number, offsetY?: number): void {
  pointerOffset = null
  resetPreview()
  if (offsetX !== undefined && offsetY !== undefined) {
    dragStart = [offsetX, offsetY]
    dragMoved = false
  }
}

function onPointerUp(offset?: [number, number]): void {
  if (!chart) return
  const wasDrag = dragMoved
  dragStart = null
  dragMoved = false
  if (offset) pointerOffset = offset
  let date: string | null = null
  if (pointerOffset && chart.containPixel({ gridIndex: 0 }, pointerOffset)) {
    date = dateAtOffset(pointerOffset)
  }
  pointerOffset = null
  if (!date || wasDrag) return
  hoverDate.value = date
  if (isTouch.value) {
    clearPreviewTimer()
    clearHideTimer()
    previewDate.value = date
  }
  togglePin(date)
}

function togglePin(date: string): void {
  if (pinnedDates.value.includes(date)) {
    unpin(date)
    return
  }
  if (pinnedDates.value.length >= MAX_PINNED_DATES) {
    emit('notice', `最多同时固定 ${MAX_PINNED_DATES} 个交易日，请先移除一个已固定日期`)
    return
  }
  const point = pointByDate.value.get(date)
  if (!point || !hasAnyValue(buildMaStructure(point))) return
  pinnedDates.value = [...pinnedDates.value, date]
}

function unpin(date: string): void {
  pinnedDates.value = pinnedDates.value.filter((pinned) => pinned !== date)
}

function onPreviewHoverChange(hovering: boolean): void {
  previewHover.value = hovering
  if (hovering) {
    clearHideTimer()
  } else if (previewDate.value && !previewDateIsHovered.value) {
    scheduleHide()
  }
}

function pinFromKeyboard(): void {
  const date = keyboardDate.value
  if (!pointByDate.value.has(date)) {
    emit('notice', `交易日 ${date || '（空）'} 不在当前分析范围内`)
    return
  }
  if (isTouch.value) {
    previewDate.value = date
  }
  togglePin(date)
}

function resetInteraction(): void {
  pointerOffset = null
  hoverDate.value = null
  resetPreview()
  pinnedDates.value = []
  keyboardDate.value = ''
}

function attachPointerListeners(): void {
  if (!chart) return
  const zr = chart.getZr()
  zrMove = (event) => onPointerMove(event.offsetX, event.offsetY)
  zrClick = (event) => onPointerUp([event.offsetX, event.offsetY])
  zrDown = (event) => onPointerDown(event.offsetX, event.offsetY)
  zrOut = () => {
    hoverDate.value = null
    clearPreviewTimer()
    if (previewDate.value) scheduleHide()
  }
  zr.on('mousemove', zrMove)
  zr.on('mousedown', zrDown)
  zr.on('click', zrClick)
  zr.on('globalout', zrOut)
}

function detachPointerListeners(): void {
  if (!chart) return
  const zr = chart.getZr()
  if (zrMove) zr.off('mousemove', zrMove)
  if (zrDown) zr.off('mousedown', zrDown)
  if (zrClick) zr.off('click', zrClick)
  if (zrOut) zr.off('globalout', zrOut)
  zrMove = undefined
  zrClick = undefined
  zrDown = undefined
  zrOut = undefined
}

function render(): void {
  if (!chart) return
  const points = props.analysis.points
  const byDate = pointByDate.value
  const series: Record<string, unknown>[] = [
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
  ]
  if (pinnedDateSet.value.size) {
    series[0]!.markLine = {
      silent: true,
      symbol: 'none',
      label: { show: false },
      lineStyle: { color: '#b8860b', width: 1.4, type: 'dashed', opacity: 0.9 },
      data: pinnedDates.value
        .filter((date) => byDate.has(date))
        .map((date) => ({ xAxis: date })),
      animation: false,
    }
  }
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
      /* keeps the preview from covering legend glyphs / hit areas */
      inactiveWidth: 0,
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
    series,
  }, true)
  computePreviewTop()
}

function resize(): void { chart?.resize(); computePreviewTop() }

onMounted(async () => {
  motionQuery = window.matchMedia('(prefers-reduced-motion: reduce)')
  prefersReducedMotion.value = motionQuery.matches
  motionListener = (event: MediaQueryListEvent) => { prefersReducedMotion.value = event.matches }
  motionQuery.addEventListener('change', motionListener)

  widthQuery = window.matchMedia('(max-width: 780px)')
  isNarrow.value = widthQuery.matches
  widthListener = (event: MediaQueryListEvent) => { isNarrow.value = event.matches }
  widthQuery.addEventListener('change', widthListener)

  const touchQuery = window.matchMedia('(pointer: coarse)')
  isTouch.value = touchQuery.matches

  await nextTick()
  if (chartEl.value) chart = echarts.init(chartEl.value)
  render()
  computePreviewTop()
  attachPointerListeners()
  window.addEventListener('resize', resize)
})

watch(() => props.analysis, (next, prev) => {
  if (next.fundCode !== prev.fundCode) {
    resetInteraction()
  } else {
    const result = reconcilePinned(pinnedDates.value, next.points)
    pinnedDates.value = result.dates
    if (result.dropped.length) emit('notice', `以下固定日期已不在当前范围或数据为空，已取消：${result.dropped.join('、')}`)
    if (result.overflow.length) emit('notice', `最多固定 ${MAX_PINNED_DATES} 个交易日，已移除最早的 ${result.overflow.join('、')}`)
    if (previewDate.value && !next.points.some((point) => point.date === previewDate.value)) {
      resetPreview()
    }
    if (hoverDate.value && !next.points.some((point) => point.date === hoverDate.value)) {
      hoverDate.value = null
    }
  }
  render()
}, { deep: true })

watch(pinnedDates, render)
watch([prefersReducedMotion, isNarrow], render)

onBeforeUnmount(() => {
  window.removeEventListener('resize', resize)
  if (motionQuery && motionListener) motionQuery.removeEventListener('change', motionListener)
  if (widthQuery && widthListener) widthQuery.removeEventListener('change', widthListener)
  clearPreviewTimer()
  clearHideTimer()
  detachPointerListeners()
  chart?.dispose()
  chart = undefined
})
</script>

<template>
  <div class="nav-chart-stack">
    <div class="chart-stage">
      <div ref="chartEl" class="chart" role="img" aria-label="单位净值与MA5、MA15、MA30、MA60、MA120走势图；悬停查看结构预览，点击固定日期进行对比" />
      <StructurePreview
        v-if="previewVisible && previewStructure"
        :structure="previewStructure"
        :pinned="previewPinned"
        :narrow="isNarrow"
        :reduced-motion="prefersReducedMotion"
        :target-style="previewStyle"
        @hover-change="onPreviewHoverChange"
        @pin="togglePin(previewStructure.date)"
        @close="resetPreview"
      />
    </div>

    <div class="pin-controls">
      <span class="pin-hint">悬停 1 秒查看结构预览；点击绘图区固定交易日。</span>
      <label class="pin-field">
        <span class="visually-hidden">选择要固定的交易日</span>
        <input
          v-model="keyboardDate"
          type="date"
          :min="analysis.startDate"
          :max="analysis.endDate"
          aria-label="选择要固定的交易日"
        />
      </label>
      <button
        type="button"
        class="pin-action"
        :disabled="pinnedFull && !pinnedDates.includes(keyboardDate)"
        @click="pinFromKeyboard"
      >固定所选日期</button>
      <span v-if="pinnedDates.length" class="pin-count" role="status">已固定 {{ pinnedDates.length }} / {{ MAX_PINNED_DATES }}</span>
    </div>

    <ul v-if="pinnedDates.length" class="pin-chips" aria-label="已固定日期">
      <li v-for="date in pinnedDates" :key="date">
        <span class="chip-date">{{ date }}</span>
        <button type="button" class="chip-remove" :aria-label="`取消固定 ${date}`" @click="unpin(date)">×</button>
      </li>
    </ul>

    <ComparisonChart
      v-if="pinnedStructures.length"
      :pinned="pinnedStructures"
      :preview="hoverPreviewStructure"
      :reduced-motion="prefersReducedMotion"
      @unpin="unpin"
    />
  </div>
</template>

<style scoped>
.nav-chart-stack { display: block; }
.chart-stage { position: relative; }
.pin-controls {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
  margin-top: 8px;
}
.pin-hint { color: #8a99a8; font-size: 11.5px; }
.pin-field input {
  border: 1px solid #d3dde5;
  border-radius: 8px;
  padding: 5px 8px;
  font-size: 12.5px;
  color: var(--ink, #10243d);
  background: #fff;
}
.pin-action {
  border: 1px solid rgba(10, 159, 152, 0.55);
  background: rgba(10, 159, 152, 0.08);
  color: #0a7f7a;
  font-size: 12px;
  font-weight: 700;
  border-radius: 8px;
  padding: 6px 12px;
  cursor: pointer;
}
.pin-action:disabled { opacity: 0.45; cursor: not-allowed; }
.pin-count { color: #5c6d81; font-size: 11.5px; font-variant-numeric: tabular-nums; }
.pin-chips {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
  list-style: none;
  padding: 0;
  margin: 8px 0 0;
}
.pin-chips li {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  border: 1px solid rgba(184, 134, 11, 0.45);
  background: rgba(184, 134, 11, 0.08);
  border-radius: 999px;
  padding: 3px 6px 3px 10px;
}
.chip-date { font-size: 12px; font-weight: 700; color: #7a5b16; font-variant-numeric: tabular-nums; }
.chip-remove {
  border: none;
  background: transparent;
  color: #a73b45;
  font-size: 14px;
  line-height: 1;
  padding: 2px 4px;
  cursor: pointer;
  border-radius: 50%;
}
</style>
