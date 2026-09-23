<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { loadAnalysis, searchFunds } from './api'
import NavChart from './components/NavChart.vue'
import { defaultDateRange } from './lib/date'
import { RequestGate } from './lib/requests'
import { isSearchablePrefix, normalizePrefix } from './lib/prefix'
import type { FundAnalysis, FundOption } from './types'

const query = ref('')
const suggestions = ref<FundOption[]>([])
const selected = ref<FundOption | null>(null)
const analysis = ref<FundAnalysis | null>(null)
const initialDateRange = defaultDateRange()
const startDate = ref(initialDateRange.startDate)
const endDate = ref(initialDateRange.endDate)
const searchBusy = ref(false)
const analysisBusy = ref(false)
const error = ref('')
const warningToast = ref('')
const hint = ref('输入至少 3 位基金代码前缀；结果过多时继续输入完整代码')
const highlighted = ref(-1)
const searchGate = new RequestGate()
const analysisGate = new RequestGate()
let debounceTimer: ReturnType<typeof setTimeout> | undefined
let errorToastTimer: ReturnType<typeof setTimeout> | undefined
let warningToastTimer: ReturnType<typeof setTimeout> | undefined

function clearErrorToast(): void {
  window.clearTimeout(errorToastTimer)
  errorToastTimer = undefined
  error.value = ''
}

function showError(message: string): void {
  window.clearTimeout(errorToastTimer)
  error.value = message
  errorToastTimer = window.setTimeout(clearErrorToast, 4000)
}

function clearWarningToast(): void {
  window.clearTimeout(warningToastTimer)
  warningToastTimer = undefined
  warningToast.value = ''
}

function showWarning(message: string): void {
  window.clearTimeout(warningToastTimer)
  warningToast.value = message
  warningToastTimer = window.setTimeout(clearWarningToast, 5000)
}

const showDropdown = computed(() => suggestions.value.length > 0 && !selected.value)
const dateRangeInvalid = computed(() => startDate.value > endDate.value)
const activeOptionId = computed(() =>
  showDropdown.value && highlighted.value >= 0 ? `fund-option-${highlighted.value}` : undefined,
)
const chartTitle = computed(() => {
  if (!selected.value) return ''
  return selected.value.fundName
})

function onInput(event: Event): void {
  const input = event.target as HTMLInputElement
  query.value = normalizePrefix(input.value)
  selected.value = null
  analysis.value = null
  analysisGate.cancel()
  analysisBusy.value = false
  clearErrorToast()
  clearWarningToast()
  highlighted.value = -1
  window.clearTimeout(debounceTimer)
  searchGate.cancel()
  searchBusy.value = false
  if (!isSearchablePrefix(query.value)) {
    suggestions.value = []
    hint.value = query.value.length === 0 ? '输入至少 3 位基金代码前缀；结果过多时继续输入完整代码' : '再输入至少一位数字'
    return
  }
  searchBusy.value = true
  debounceTimer = window.setTimeout(() => void runSearch(query.value), 250)
}

async function runSearch(prefix: string): Promise<void> {
  const request = searchGate.next()
  try {
    const result = await searchFunds(prefix, request.signal)
    if (!searchGate.isCurrent(request.id)) return
    suggestions.value = result
    hint.value = result.length ? '' : '没有找到匹配的基金代码'
  } catch (cause) {
    if (cause instanceof DOMException && cause.name === 'AbortError') return
    if (searchGate.isCurrent(request.id)) {
      suggestions.value = []
      showError(cause instanceof Error ? cause.message : '搜索失败，请稍后重试。')
    }
  } finally {
    if (searchGate.isCurrent(request.id)) searchBusy.value = false
  }
}

function choose(option: FundOption): void {
  selected.value = option
  query.value = option.fundCode
  suggestions.value = []
  highlighted.value = -1
  void runAnalysis()
}

function onKeydown(event: KeyboardEvent): void {
  if (event.key === 'Escape' && suggestions.value.length) {
    suggestions.value = []
    highlighted.value = -1
    return
  }
  if (!showDropdown.value) return
  if (event.key === 'ArrowDown') {
    event.preventDefault()
    highlighted.value = (highlighted.value + 1) % suggestions.value.length
    scrollHighlightedIntoView()
  }
  if (event.key === 'ArrowUp') {
    event.preventDefault()
    highlighted.value = (highlighted.value - 1 + suggestions.value.length) % suggestions.value.length
    scrollHighlightedIntoView()
  }
  if (event.key === 'Enter' && highlighted.value >= 0) {
    event.preventDefault()
    choose(suggestions.value[highlighted.value])
  }
}

function scrollHighlightedIntoView(): void {
  requestAnimationFrame(() => {
    document.getElementById(activeOptionId.value ?? '')?.scrollIntoView({ block: 'nearest' })
  })
}

async function runAnalysis(): Promise<void> {
  if (!selected.value || dateRangeInvalid.value) return
  analysisGate.cancel()
  const request = analysisGate.next()
  analysisBusy.value = true
  clearErrorToast()
  clearWarningToast()
  analysis.value = null
  try {
    const result = await loadAnalysis(selected.value.fundCode, startDate.value, endDate.value, request.signal)
    if (analysisGate.isCurrent(request.id)) {
      analysis.value = result
      if (result.warnings.length) showWarning(result.warnings.join('；'))
    }
  } catch (cause) {
    if (cause instanceof DOMException && cause.name === 'AbortError') return
    if (analysisGate.isCurrent(request.id)) showError(cause instanceof Error ? cause.message : '分析失败，请稍后重试。')
  } finally {
    if (analysisGate.isCurrent(request.id)) analysisBusy.value = false
  }
}

onBeforeUnmount(() => {
  window.clearTimeout(debounceTimer)
  window.clearTimeout(errorToastTimer)
  window.clearTimeout(warningToastTimer)
  searchGate.cancel()
  analysisGate.cancel()
})
watch([startDate, endDate], () => { if (selected.value && !dateRangeInvalid.value) void runAnalysis() })
</script>

<template>
  <main class="page-shell">
    <header class="hero">
      <div>
        <p class="eyebrow">ARIADNE · FUND INSIGHT</p>
        <h1>净值，沿着交易日看得更清楚。</h1>
        <p class="subtitle">用真实净值描摹节奏，以 MA30 看见更平滑的长期趋势。</p>
      </div>
      <div class="hero-mark" aria-hidden="true">
        <svg width="120" height="72" viewBox="0 0 120 72" fill="none" xmlns="http://www.w3.org/2000/svg">
          <path d="M6 62 C 30 62, 34 18, 58 18 S 88 44, 114 10" stroke="#0a9f98" stroke-width="1.5" stroke-linecap="round" opacity="0.9" />
          <path d="M6 62 C 30 62, 34 18, 58 18 S 88 44, 114 10" stroke="#1b5f9e" stroke-width="1" stroke-linecap="round" stroke-dasharray="1 7" opacity="0.5" />
          <circle cx="6" cy="62" r="3.5" fill="#0a9f98" />
          <circle cx="58" cy="18" r="3" fill="white" stroke="#0a9f98" stroke-width="1.5" />
          <circle cx="114" cy="10" r="3.5" fill="#0a9f98" />
        </svg>
      </div>
    </header>

    <section class="control-card" aria-label="基金查询">
      <div class="field search-field">
        <label for="fund-search">搜索基金</label>
        <div class="search-box">
          <input
            id="fund-search"
            :value="query"
            inputmode="numeric"
            autocomplete="off"
            maxlength="6"
            placeholder="输入 3–6 位代码前缀"
            role="combobox"
            :aria-expanded="showDropdown"
            aria-controls="fund-suggestions"
            :aria-activedescendant="activeOptionId"
            aria-autocomplete="list"
            @input="onInput"
            @keydown="onKeydown"
          />
          <span v-if="searchBusy" class="spinner" aria-label="搜索中" />
        </div>
        <ul v-if="showDropdown" id="fund-suggestions" class="dropdown" role="listbox" aria-label="匹配基金列表">
          <li
            v-for="(option, index) in suggestions"
            :id="`fund-option-${index}`"
            :key="option.fundCode"
            :class="{ active: index === highlighted }"
            role="option"
            :aria-selected="index === highlighted"
            @mousedown.prevent="choose(option)"
            @mousemove="highlighted = index"
          >
            <strong>{{ option.fundCode }}</strong><span>{{ option.fundName }}</span>
          </li>
        </ul>
        <small>{{ hint }}</small>
      </div>
      <div class="field date-field">
        <label for="start-date">开始日期</label>
        <input id="start-date" v-model="startDate" type="date" :aria-invalid="dateRangeInvalid" aria-describedby="date-range-error" />
      </div>
      <div class="field date-field">
        <label for="end-date">结束日期</label>
        <input id="end-date" v-model="endDate" type="date" :aria-invalid="dateRangeInvalid" aria-describedby="date-range-error" />
      </div>
      <button class="query-button" :disabled="!selected || analysisBusy || dateRangeInvalid" @click="runAnalysis">{{ analysisBusy ? '加载中…' : '查询趋势' }}</button>
      <span v-if="dateRangeInvalid" id="date-range-error" class="field-error" role="alert">开始日期不能晚于结束日期，请调整。</span>
    </section>

    <Transition name="toast">
      <p v-if="error" class="notice toast error-notice" role="alert" aria-live="assertive">{{ error }}</p>
    </Transition>
    <Transition name="toast">
      <p v-if="warningToast" class="notice toast warning-notice" role="alert" aria-live="polite">{{ warningToast }}</p>
    </Transition>

    <section v-if="analysis" class="chart-card" aria-live="polite">
      <div class="chart-heading">
        <div>
          <p class="eyebrow">净值走势</p>
          <div class="chart-title">
            <h2>{{ chartTitle }}</h2>
            <span class="chart-code">{{ analysis.fundCode }}</span>
          </div>
        </div>
        <p class="range-label">{{ analysis.startDate }} — {{ analysis.endDate }}</p>
      </div>
      <NavChart v-if="analysis.points.length" :analysis="analysis" />
      <div v-else class="empty-state">所选范围没有交易日数据，请调整日期。</div>
      <div class="chart-footnote">MA30 / MA60 分别为当前及此前 29 / 59 个 A 股交易日净值的平均值；缺失窗口保持为空，不填充或插值。相对均线百分比不是实际投资收益率。</div>
    </section>

    <section v-else-if="analysisBusy" class="loading-card chart-card" aria-busy="true" aria-label="数据加载中">
      <div class="skeleton skeleton-heading" />
      <div class="skeleton skeleton-chart" />
      <p class="loading-label">正在沿交易日铺展净值路径…</p>
    </section>

    <section v-else-if="!error" class="welcome-card">
      <div class="welcome-icon" aria-hidden="true">
        <svg width="56" height="56" viewBox="0 0 56 56" fill="none" xmlns="http://www.w3.org/2000/svg">
          <circle cx="28" cy="28" r="26" stroke="#0a9f98" stroke-width="1" opacity="0.35" />
          <circle cx="28" cy="28" r="18" stroke="#0a9f98" stroke-width="1" opacity="0.5" stroke-dasharray="3 5" />
          <path d="M14 38 C 22 38, 24 20, 32 20 S 40 30, 44 16" stroke="#0a9f98" stroke-width="1.8" stroke-linecap="round" />
          <circle cx="14" cy="38" r="2.6" fill="#0a9f98" />
          <circle cx="44" cy="16" r="2.6" fill="#0a9f98" />
        </svg>
      </div>
      <h2>从一只基金开始</h2>
      <p>选择基金后，趋势图会自动加载。默认展示最近 30 个自然日内的交易日。</p>
    </section>
  </main>
</template>
