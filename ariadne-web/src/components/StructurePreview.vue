<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import * as echarts from 'echarts'
import type { MaStructure } from '../lib/maStructure'
import { MA_KEYS, formatNavDeviationPercent, formatStructureValue, navDeviationValuesOf } from '../lib/maStructure'

const props = defineProps<{
  structure: MaStructure
  pinned: boolean
  narrow: boolean
  reducedMotion: boolean
  targetStyle?: Record<string, string>
}>()

const emit = defineEmits<{
  hoverChange: [hovering: boolean]
  pin: []
  close: []
}>()

const chartEl = ref<HTMLDivElement>()
let chart: echarts.ECharts | undefined

function render(): void {
  if (!chart) return
  const dark = props.narrow
  const values = navDeviationValuesOf(props.structure)
  const numericValues = values.filter((value): value is number => value !== null)
  const allZero = numericValues.every((value) => value === 0)
  chart.setOption({
    animation: !props.reducedMotion,
    animationDuration: 200,
    textStyle: { color: dark ? '#e8f1f7' : '#38506b' },
    grid: { left: 6, right: 10, top: 8, bottom: 18, containLabel: true },
    xAxis: {
      type: 'category',
      data: [...MA_KEYS],
      axisLine: { onZero: true, lineStyle: { color: dark ? 'rgba(232,241,247,0.4)' : '#cfdae4' } },
      axisTick: { show: false },
      axisLabel: {
        color: dark ? '#c3d4e2' : '#718096',
        fontSize: 10,
        interval: 0,
        rotate: props.narrow ? 32 : 22,
      },
    },
    yAxis: {
      type: 'value',
      scale: true,
      min: allZero ? -1 : Math.min(0, ...numericValues),
      max: allZero ? 1 : Math.max(0, ...numericValues),
      axisLabel: { color: dark ? '#c3d4e2' : '#718096', formatter: (value: number) => `${Number(value.toFixed(2))}%` },
      splitLine: { lineStyle: { color: dark ? 'rgba(232,241,247,0.12)' : '#e9eef3' } },
    },
    series: [{
      type: 'bar',
      data: values,
      barWidth: '45%',
      itemStyle: { color: (params: { value: number | null }) => params.value !== null && params.value >= 0 ? '#c8434b' : '#0a7f7a' },
      label: {
        show: false,
      },
      z: 2,
    }],
  }, true)
}

function onEnter(): void { if (!props.narrow) emit('hoverChange', true) }
function onLeave(): void { if (!props.narrow) emit('hoverChange', false) }

watch(() => [props.structure, props.narrow, props.reducedMotion], render, { deep: true })

watch(chartEl, (el, previous) => {
  if (previous && chart) { chart.dispose(); chart = undefined }
  if (el) { chart = echarts.init(el); render() }
}, { flush: 'post' })

onBeforeUnmount(() => { chart?.dispose(); chart = undefined })

const title = computed(() => `${props.structure.date} 结构预览`)
const columns = computed(() => props.structure.entries)
const comparisons = computed(() => {
  const nav = props.structure.entries.find((entry) => entry.name === '单位净值')?.value ?? null
  return props.structure.entries.map((entry) => ({
    name: entry.name,
    text: entry.name === '单位净值'
      ? (entry.value === null ? '—' : '基准')
      : formatNavDeviationPercent(nav, entry.value),
  }))
})
</script>

<template>
  <div
    class="structure-preview"
    :class="{ narrow }"
    :style="narrow ? undefined : targetStyle"
    role="group"
    :aria-label="title"
    @mouseenter="onEnter"
    @mouseleave="onLeave"
  >
    <div class="preview-head">
      <span class="preview-title">{{ structure.date }} 结构预览</span>
      <span class="preview-actions">
        <button
          type="button"
          class="preview-pin"
          :aria-pressed="pinned"
          :aria-label="pinned ? `取消固定 ${structure.date}` : `固定 ${structure.date} 用于对比`"
          @click.stop="emit('pin')"
        >{{ pinned ? '已固定' : '固定' }}</button>
        <button
          v-if="narrow"
          type="button"
          class="preview-close"
          aria-label="关闭结构预览"
          @click="emit('close')"
        >×</button>
      </span>
    </div>
    <div ref="chartEl" class="preview-chart" aria-hidden="true" />
    <div class="preview-chart-note"><span class="positive-nav">正值：净值高于均线</span>；<span class="negative-nav">负值：净值低于均线</span>（不是收益率）</div>
    <table class="preview-comparison">
      <caption>净值相对均线（NAV − MA）/ MA × 100%</caption>
      <thead><tr><th scope="col">相对均线</th><th v-for="item in columns" :key="item.name" scope="col">{{ item.name }}</th></tr></thead>
      <tbody>
        <tr class="preview-absolute"><th scope="row">绝对值</th><td v-for="item in columns" :key="item.name">{{ formatStructureValue(item.value) }}</td></tr>
        <tr class="preview-percent"><th scope="row">百分比</th><td v-for="item in comparisons" :key="item.name">{{ item.text }}</td></tr>
      </tbody>
    </table>
    <table class="visually-hidden">
      <caption>{{ title }}（按 MA120 到 MA5、单位净值顺序）</caption>
      <tbody>
        <tr v-for="entry in structure.entries" :key="entry.name">
          <th scope="row">{{ entry.name }}</th>
          <td>{{ formatStructureValue(entry.value) }}</td>
        </tr>
      </tbody>
    </table>
  </div>
</template>

<style scoped>
.structure-preview {
  position: absolute;
  top: 34px;
  right: 8px;
  z-index: 6;
  width: 360px;
  padding: 6px 10px 4px;
  border-radius: 10px;
  background: rgba(255, 255, 255, 0.9);
  border: 1px solid rgba(16, 36, 61, 0.12);
  box-shadow: 0 10px 28px rgba(16, 36, 61, 0.16);
  backdrop-filter: blur(3px);
}
.preview-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  margin-bottom: 0;
}
.preview-actions { display: inline-flex; align-items: center; gap: 6px; }
.preview-title {
  font-size: 12px;
  font-weight: 700;
  color: #38506b;
  font-variant-numeric: tabular-nums;
}
.preview-pin {
  border: 1px solid rgba(10, 159, 152, 0.5);
  background: rgba(10, 159, 152, 0.1);
  color: #0a7f7a;
  font-size: 11px;
  font-weight: 700;
  line-height: 1;
  border-radius: 999px;
  padding: 4px 9px;
  cursor: pointer;
}
.preview-pin[aria-pressed="true"] {
  background: #0a9f98;
  border-color: #0a9f98;
  color: #fff;
}
.preview-chart { width: 100%; height: 155px; }
.preview-chart-note { margin-top: -3px; font-size: 9px; color: #718096; text-align: center; }
.preview-chart-note .positive-nav { color: #c8434b; }
.preview-chart-note .negative-nav { color: #0a7f7a; }
.preview-comparison { width: 100%; border-collapse: collapse; table-layout: fixed; font-size: 10px; color: #38506b; text-align: center; font-variant-numeric: tabular-nums; }
.preview-comparison caption { caption-side: top; text-align: left; font-size: 10px; }
.preview-comparison th, .preview-comparison td { padding: 2px 1px; white-space: nowrap; }
.preview-comparison th { font-weight: 600; }
.preview-comparison .preview-absolute td,
.preview-comparison .preview-percent td {
  white-space: normal;
  overflow-wrap: anywhere;
  vertical-align: top;
}

.structure-preview.narrow {
  position: relative;
  inset: auto;
  width: 100%;
  margin-top: 8px;
  background: rgba(8, 27, 51, 0.88);
  border-color: rgba(232, 241, 247, 0.2);
}
.structure-preview.narrow .preview-title { color: #e8f1f7; }
.structure-preview.narrow .preview-chart { height: 180px; }
.structure-preview.narrow .preview-chart-note { color: #c3d4e2; }
.structure-preview.narrow .preview-chart-note .negative-nav { color: #79d4cc; }
.structure-preview.narrow .preview-comparison { color: #e8f1f7; }
.structure-preview.narrow .preview-pin {
  border-color: rgba(232, 241, 247, 0.5);
  background: rgba(232, 241, 247, 0.12);
  color: #e8f1f7;
}
.structure-preview.narrow .preview-pin[aria-pressed="true"] {
  background: #0a9f98;
  border-color: #0a9f98;
}
.preview-close {
  border: 1px solid rgba(232, 241, 247, 0.4);
  background: rgba(232, 241, 247, 0.1);
  color: #e8f1f7;
  font-size: 14px;
  line-height: 1;
  width: 26px;
  height: 26px;
  border-radius: 50%;
  cursor: pointer;
  display: inline-flex;
  align-items: center;
  justify-content: center;
}
</style>
