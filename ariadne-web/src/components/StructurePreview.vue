<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import * as echarts from 'echarts'
import type { MaStructure } from '../lib/maStructure'
import { STRUCTURE_COLORS, STRUCTURE_SERIES, formatStructureValue, valuesOf } from '../lib/maStructure'

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
  chart.setOption({
    animation: !props.reducedMotion,
    animationDuration: 200,
    textStyle: { color: dark ? '#e8f1f7' : '#38506b' },
    grid: { left: 6, right: 10, top: 8, bottom: 18, containLabel: true },
    xAxis: {
      type: 'category',
      data: [...STRUCTURE_SERIES],
      axisLine: { lineStyle: { color: dark ? 'rgba(232,241,247,0.4)' : '#cfdae4' } },
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
      axisLabel: { show: false },
      splitLine: { lineStyle: { color: dark ? 'rgba(232,241,247,0.12)' : '#e9eef3' } },
    },
    series: [{
      type: 'line',
      data: valuesOf(props.structure),
      symbol: 'circle',
      symbolSize: 6.5,
      connectNulls: false,
      lineStyle: { color: dark ? '#9ec8e2' : '#1b5f9e', width: 1.8 },
      itemStyle: { color: (params: { dataIndex: number }) => STRUCTURE_COLORS[STRUCTURE_SERIES[params.dataIndex] ?? '单位净值'] },
      label: {
        show: true,
        position: 'top',
        distance: 5,
        fontSize: 10,
        color: dark ? '#e8f1f7' : '#38506b',
        formatter: (params: { value: unknown }) => {
          const value = Array.isArray(params.value) ? params.value[1] : params.value
          return formatStructureValue(typeof value === 'number' ? value : null)
        },
      },
      z: 3,
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
  max-height: 258px;
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
.preview-chart { width: 100%; height: 218px; }

.structure-preview.narrow {
  position: relative;
  inset: auto;
  width: 100%;
  max-height: none;
  margin-top: 8px;
  background: rgba(8, 27, 51, 0.88);
  border-color: rgba(232, 241, 247, 0.2);
}
.structure-preview.narrow .preview-title { color: #e8f1f7; }
.structure-preview.narrow .preview-chart { height: 180px; }
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
