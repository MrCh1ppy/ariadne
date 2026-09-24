import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import type { FundAnalysis } from '../types'

const setOption = vi.hoisted(() => vi.fn<(options: unknown) => void>())
const getOption = vi.hoisted(() => vi.fn<() => unknown>())
const zrHandlers = vi.hoisted(() => new Map<string, (...args: unknown[]) => void>())
const zrOn = vi.hoisted(() => vi.fn((name: string, handler: (...args: unknown[]) => void) => { zrHandlers.set(name, handler) }))
const zrOff = vi.hoisted(() => vi.fn((name: string) => { zrHandlers.delete(name) }))
const containPixel = vi.hoisted(() => vi.fn<(finder: unknown, value: number[]) => boolean>(() => false))
const convertFromPixel = vi.hoisted(() => vi.fn<(finder: unknown, value: number[]) => number[]>(() => [Number.NaN]))
const resize = vi.hoisted(() => vi.fn())
const dispose = vi.hoisted(() => vi.fn())
const echartsInit = vi.hoisted(() => vi.fn(() => ({
  setOption, getOption, resize, dispose, containPixel, convertFromPixel,
  getZr: () => ({ on: zrOn, off: zrOff }),
  on: vi.fn(),
})))
vi.mock('echarts', () => ({ init: echartsInit }))

vi.mock('./ComparisonChart.vue', () => ({
  default: {
    name: 'ComparisonChart',
    props: ['pinned', 'preview', 'reducedMotion'],
    emits: ['unpin'],
    template: '<div class="comparison-chart-stub" />',
  },
}))

import NavChart from './NavChart.vue'

function ma(value: string | null): { value: string | null; deviationPercent: string | null } {
  return { value, deviationPercent: null }
}

function point(date: string, unitNav: string | null, maValues: Partial<Record<'MA5' | 'MA15' | 'MA30' | 'MA60' | 'MA120', string | null>> = {}) {
  const movingAverages: Record<string, { value: string | null; deviationPercent: string | null }> = {}
  for (const key of ['MA5', 'MA15', 'MA30', 'MA60', 'MA120'] as const) {
    if (key in maValues) movingAverages[key] = ma(maValues[key] ?? null)
  }
  return { date, unitNav, movingAverages }
}

function makeAnalysis(): FundAnalysis {
  return {
    fundCode: '022485', startDate: '2026-09-01', endDate: '2026-09-03', warnings: [],
    points: [
      point('2026-09-01', '1.0000', { MA5: '0.98', MA15: '0.95', MA30: '0.90', MA60: null, MA120: '0.80' }),
      point('2026-09-02', '1.0100', { MA5: '1.00', MA15: null, MA30: '0.92', MA60: '0.85', MA120: null }),
      point('2026-09-03', null, { MA5: null, MA15: null, MA30: null, MA60: null, MA120: null }),
    ],
  }
}

function stubMatchMedia(pointer: 'coarse' | 'fine' = 'fine', narrow = false): void {
  vi.stubGlobal('matchMedia', (query: string) => ({
    matches: query.includes('coarse') ? pointer === 'coarse' : (query.includes('max-width') ? narrow : false),
    addEventListener: vi.fn(),
    removeEventListener: vi.fn(),
  }))
}

/** grid: x in [0,300], y in [0,200]; category index = x/100 */
function useGridMapping(): void {
  containPixel.mockImplementation((_, value: number[]) => value[0] >= 0 && value[0] <= 300 && value[1] >= 0 && value[1] <= 200)
  convertFromPixel.mockImplementation((_, value: number[]) => [value[0] / 100])
}

function fireZr(name: string, event: { offsetX: number; offsetY: number } = { offsetX: 0, offsetY: 0 }): void {
  zrHandlers.get(name)?.(event)
}

describe('NAV chart structure preview & comparison', () => {
  beforeEach(() => {
    vi.useFakeTimers()
    getOption.mockReturnValue(undefined)
    zrHandlers.clear()
    containPixel.mockReset(); containPixel.mockReturnValue(false)
    convertFromPixel.mockReset(); convertFromPixel.mockReturnValue([Number.NaN])
    setOption.mockClear()
    dispose.mockClear()
    stubMatchMedia()
  })

  afterEach(() => {
    vi.useRealTimers()
    vi.unstubAllGlobals()
  })

  it('keeps the immediate dark axis tooltip and legend defaults, with only date and NAV in the tooltip', async () => {
    const wrapper = mount(NavChart, { props: { analysis: makeAnalysis() } })
    await flushPromises()
    const option = setOption.mock.lastCall?.[0] as {
      tooltip: { trigger: string; confine: boolean; backgroundColor: string; formatter: (items: { axisValue: string }[]) => string }
      legend: { selected: Record<string, boolean> }
      series: { name: string; markLine?: unknown }[]
    }
    expect(option.tooltip.trigger).toBe('axis')
    expect(option.tooltip.confine).toBe(true)
    expect(option.tooltip.backgroundColor).toBe('rgba(8, 27, 51, 0.94)')
    expect(option.tooltip.formatter([{ axisValue: '2026-09-01' }])).toContain('单位净值：1.0000')
    expect(option.tooltip.formatter([{ axisValue: '2026-09-01' }])).not.toMatch(/MA\d+|相对/)
    expect(option.tooltip.formatter([{ axisValue: '2026-09-03' }])).toContain('单位净值：暂无')
    expect(option.legend.selected).toEqual({ '单位净值': true, MA5: false, MA15: false, MA30: true, MA60: false, MA120: false })
    expect(option.series.map((series) => series.name)).toEqual(['单位净值', 'MA5', 'MA15', 'MA30', 'MA60', 'MA120'])
    expect(option.series.every((series) => series.markLine === undefined)).toBe(true)
    wrapper.unmount()
  })

  it('shows the preview after hovering one date for 1s and hides it after leaving with a delay', async () => {
    useGridMapping()
    const wrapper = mount(NavChart, { props: { analysis: makeAnalysis() } })
    await flushPromises()
    expect(wrapper.find('.structure-preview').exists()).toBe(false)

    fireZr('mousemove', { offsetX: 0, offsetY: 100 })
    vi.advanceTimersByTime(999)
    expect(wrapper.find('.structure-preview').exists()).toBe(false)
    vi.advanceTimersByTime(1)
    await flushPromises()
    expect(wrapper.find('.structure-preview').exists()).toBe(true)
    expect(wrapper.find('.preview-title').text()).toContain('2026-09-01')
    expect(wrapper.findComponent({ name: 'StructurePreview' }).props('targetStyle')).not.toHaveProperty('maxHeight')
    expect(wrapper.find('.preview-comparison').text()).toContain('相对均线')
    expect(wrapper.find('.preview-comparison tbody').text()).toContain('+25.00%')

    fireZr('globalout')
    vi.advanceTimersByTime(619)
    expect(wrapper.find('.structure-preview').exists()).toBe(true)
    vi.advanceTimersByTime(1)
    await flushPromises()
    expect(wrapper.find('.structure-preview').exists()).toBe(false)
    wrapper.unmount()
  })

  it('cancels the pending 1s timer when the pointer leaves the valid date area', async () => {
    useGridMapping()
    const wrapper = mount(NavChart, { props: { analysis: makeAnalysis() } })
    await flushPromises()

    fireZr('mousemove', { offsetX: 0, offsetY: 100 })
    vi.advanceTimersByTime(400)
    /* move outside the grid before 1000ms elapses */
    fireZr('mousemove', { offsetX: 500, offsetY: 500 })
    vi.advanceTimersByTime(1000)
    await flushPromises()
    expect(wrapper.find('.structure-preview').exists()).toBe(false)

    /* re-enter and complete the dwell */
    fireZr('mousemove', { offsetX: 0, offsetY: 100 })
    vi.advanceTimersByTime(1000)
    await flushPromises()
    expect(wrapper.find('.structure-preview').exists()).toBe(true)
    wrapper.unmount()
  })

  it('cancels the pending 1s timer on globalout as well', async () => {
    useGridMapping()
    const wrapper = mount(NavChart, { props: { analysis: makeAnalysis() } })
    await flushPromises()

    fireZr('mousemove', { offsetX: 0, offsetY: 100 })
    vi.advanceTimersByTime(600)
    fireZr('globalout')
    vi.advanceTimersByTime(1000)
    await flushPromises()
    expect(wrapper.find('.structure-preview').exists()).toBe(false)
    wrapper.unmount()
  })

  it('cancels the hide timer when returning to the same date within 620ms', async () => {
    useGridMapping()
    const wrapper = mount(NavChart, { props: { analysis: makeAnalysis() } })
    await flushPromises()

    fireZr('mousemove', { offsetX: 0, offsetY: 100 })
    vi.advanceTimersByTime(1000)
    await flushPromises()
    expect(wrapper.find('.structure-preview').exists()).toBe(true)

    fireZr('globalout')
    vi.advanceTimersByTime(300)
    expect(wrapper.find('.structure-preview').exists()).toBe(true)
    /* return to the same date before 620ms hide fires */
    fireZr('mousemove', { offsetX: 0, offsetY: 100 })
    vi.advanceTimersByTime(2000)
    await flushPromises()
    expect(wrapper.find('.structure-preview').exists()).toBe(true)
    expect(wrapper.find('.preview-title').text()).toContain('2026-09-01')
    wrapper.unmount()
  })

  it('does not restart the 1000ms timer on same-date moves; t=1000ms shows preview', async () => {
    useGridMapping()
    const wrapper = mount(NavChart, { props: { analysis: makeAnalysis() } })
    await flushPromises()
    expect(wrapper.find('.structure-preview').exists()).toBe(false)

    fireZr('mousemove', { offsetX: 0, offsetY: 100 })
    vi.advanceTimersByTime(400)
    expect(wrapper.find('.structure-preview').exists()).toBe(false)
    fireZr('mousemove', { offsetX: 4, offsetY: 104 })
    vi.advanceTimersByTime(400)
    expect(wrapper.find('.structure-preview').exists()).toBe(false)
    fireZr('mousemove', { offsetX: 8, offsetY: 96 })
    vi.advanceTimersByTime(200)
    await flushPromises()
    expect(wrapper.find('.structure-preview').exists()).toBe(true)
    expect(wrapper.find('.preview-title').text()).toContain('2026-09-01')
    wrapper.unmount()
  })

  it('still shows no stale preview after rapid date switches', async () => {
    useGridMapping()
    const wrapper = mount(NavChart, { props: { analysis: makeAnalysis() } })
    await flushPromises()

    fireZr('mousemove', { offsetX: 0, offsetY: 100 })
    vi.advanceTimersByTime(300)
    fireZr('mousemove', { offsetX: 100, offsetY: 100 })
    vi.advanceTimersByTime(300)
    fireZr('mousemove', { offsetX: 0, offsetY: 100 })
    vi.advanceTimersByTime(999)
    expect(wrapper.find('.structure-preview').exists()).toBe(false)
    vi.advanceTimersByTime(1)
    await flushPromises()
    expect(wrapper.find('.structure-preview').exists()).toBe(true)
    expect(wrapper.find('.preview-title').text()).toContain('2026-09-01')
    wrapper.unmount()
  })

  it('rejects a drag instead of pinning the date', async () => {
    useGridMapping()
    const wrapper = mount(NavChart, { props: { analysis: makeAnalysis() } })
    await flushPromises()

    fireZr('mousedown', { offsetX: 0, offsetY: 100 })
    fireZr('mousemove', { offsetX: 60, offsetY: 108 })
    fireZr('click', { offsetX: 60, offsetY: 108 })
    await flushPromises()
    expect(wrapper.text()).not.toContain('已固定')
    expect(wrapper.find('.pin-chips').exists()).toBe(false)

    /* a small wiggle below the threshold still pins */
    fireZr('mousedown', { offsetX: 100, offsetY: 100 })
    fireZr('mousemove', { offsetX: 103, offsetY: 102 })
    fireZr('click', { offsetX: 103, offsetY: 102 })
    await flushPromises()
    expect(wrapper.text()).toContain('已固定 1 / 6')
    wrapper.unmount()
  })

  it('restarts the timer when the date changes and never stacks multiple previews', async () => {
    useGridMapping()
    const wrapper = mount(NavChart, { props: { analysis: makeAnalysis() } })
    await flushPromises()

    fireZr('mousemove', { offsetX: 0, offsetY: 100 })
    vi.advanceTimersByTime(900)
    fireZr('mousemove', { offsetX: 100, offsetY: 100 })
    vi.advanceTimersByTime(999)
    expect(wrapper.find('.structure-preview').exists()).toBe(false)
    vi.advanceTimersByTime(1)
    await flushPromises()
    const previews = wrapper.findAll('.structure-preview')
    expect(previews).toHaveLength(1)
    expect(previews[0]?.find('.preview-title').text()).toContain('2026-09-02')
    wrapper.unmount()
  })

  it('keeps the preview alive while hovering it and hides it 500-800ms after leaving both', async () => {
    useGridMapping()
    const wrapper = mount(NavChart, { props: { analysis: makeAnalysis() } })
    await flushPromises()

    fireZr('mousemove', { offsetX: 0, offsetY: 100 })
    vi.advanceTimersByTime(1000)
    await flushPromises()
    expect(wrapper.find('.structure-preview').exists()).toBe(true)

    fireZr('globalout')
    await wrapper.find('.structure-preview').trigger('mouseenter')
    vi.advanceTimersByTime(2000)
    expect(wrapper.find('.structure-preview').exists()).toBe(true)

    await wrapper.find('.structure-preview').trigger('mouseleave')
    vi.advanceTimersByTime(799)
    expect(wrapper.find('.structure-preview').exists()).toBe(true)
    vi.advanceTimersByTime(1)
    await flushPromises()
    expect(wrapper.find('.structure-preview').exists()).toBe(false)
    wrapper.unmount()
  })

  it('never shows the preview for dates whose structure is fully empty', async () => {
    useGridMapping()
    const wrapper = mount(NavChart, { props: { analysis: makeAnalysis() } })
    await flushPromises()
    fireZr('mousemove', { offsetX: 200, offsetY: 100 })
    vi.advanceTimersByTime(1500)
    await flushPromises()
    expect(wrapper.find('.structure-preview').exists()).toBe(false)
    wrapper.unmount()
  })

  it('toggles pins via zrender click, marks pinned dates on the main chart and renders the comparison chart', async () => {
    useGridMapping()
    const wrapper = mount(NavChart, { props: { analysis: makeAnalysis() } })
    await flushPromises()
    expect(wrapper.findComponent({ name: 'ComparisonChart' }).exists()).toBe(false)

    fireZr('click', { offsetX: 0, offsetY: 100 })
    await flushPromises()
    let option = setOption.mock.lastCall?.[0] as { series: { name: string; markLine?: { data: { xAxis: string }[] } }[] }
    expect(option.series[0]?.markLine?.data).toEqual([{ xAxis: '2026-09-01' }])
    const comparison = wrapper.findComponent({ name: 'ComparisonChart' })
    expect(comparison.exists()).toBe(true)
    expect(wrapper.text()).toContain('已固定 1 / 6')

    fireZr('click', { offsetX: 100, offsetY: 100 })
    await flushPromises()
    option = setOption.mock.lastCall?.[0] as typeof option
    expect(option.series[0]?.markLine?.data).toEqual([{ xAxis: '2026-09-01' }, { xAxis: '2026-09-02' }])

    fireZr('click', { offsetX: 0, offsetY: 100 })
    await flushPromises()
    option = setOption.mock.lastCall?.[0] as typeof option
    expect(option.series[0]?.markLine?.data).toEqual([{ xAxis: '2026-09-02' }])
    wrapper.unmount()
  })

  it('ignores clicks outside the grid, below the axis or on empty dates', async () => {
    useGridMapping()
    const wrapper = mount(NavChart, { props: { analysis: makeAnalysis() } })
    await flushPromises()

    fireZr('click', { offsetX: 400, offsetY: 100 })
    fireZr('click', { offsetX: 100, offsetY: 250 })
    fireZr('click', { offsetX: 200, offsetY: 100 })
    await flushPromises()
    expect(wrapper.find('.pin-chips').exists()).toBe(false)
    expect(wrapper.findComponent({ name: 'ComparisonChart' }).exists()).toBe(false)
    wrapper.unmount()
  })

  it('caps pins at 6 and evicts the oldest with a notice', async () => {
    useGridMapping()
    const points = Array.from({ length: 8 }, (_, index) =>
      point(`2026-09-0${index + 1}`, '1.0', { MA5: '1.0' }))
    containPixel.mockImplementation((_, value: number[]) => value[0] >= 0 && value[0] <= 700 && value[1] >= 0 && value[1] <= 200)
    convertFromPixel.mockImplementation((_, value: number[]) => [value[0] / 100])
    const wrapper = mount(NavChart, { props: { analysis: { fundCode: '022485', startDate: '2026-09-01', endDate: '2026-09-08', warnings: [], points } } })
    await flushPromises()

    for (let index = 0; index < 7; index += 1) {
      fireZr('click', { offsetX: index * 100, offsetY: 100 })
    }
    await flushPromises()
    expect(wrapper.text()).toContain('已固定 6 / 6')
    expect(wrapper.emitted('notice')?.some(([message]) => String(message).includes('最多同时固定 6 个交易日'))).toBe(true)
    const option = setOption.mock.lastCall?.[0] as { series: { markLine?: { data: { xAxis: string }[] } }[] }
    expect(option.series[0]?.markLine?.data?.map((entry) => entry.xAxis)).toEqual(
      ['2026-09-01', '2026-09-02', '2026-09-03', '2026-09-04', '2026-09-05', '2026-09-06'],
    )
    wrapper.unmount()
  })

  it('unpins via chip button and hides the comparison chart when the last pin is removed', async () => {
    useGridMapping()
    const wrapper = mount(NavChart, { props: { analysis: makeAnalysis() } })
    await flushPromises()

    fireZr('click', { offsetX: 0, offsetY: 100 })
    await flushPromises()
    expect(wrapper.findComponent({ name: 'ComparisonChart' }).exists()).toBe(true)

    await wrapper.find('.chip-remove').trigger('click')
    await flushPromises()
    expect(wrapper.findComponent({ name: 'ComparisonChart' }).exists()).toBe(false)
    expect(wrapper.find('.pin-chips').exists()).toBe(false)
    const option = setOption.mock.lastCall?.[0] as { series: { markLine?: unknown }[] }
    expect(option.series[0]?.markLine).toBeUndefined()
    wrapper.unmount()
  })

  it('pins from the keyboard date input for touch and keyboard users', async () => {
    const wrapper = mount(NavChart, { props: { analysis: makeAnalysis() } })
    await flushPromises()

    await wrapper.find('input[type="date"]').setValue('2026-09-02')
    await wrapper.find('.pin-action').trigger('click')
    await flushPromises()
    expect(wrapper.text()).toContain('已固定 1 / 6')
    expect(wrapper.findComponent({ name: 'ComparisonChart' }).exists()).toBe(true)

    await wrapper.find('input[type="date"]').setValue('2026-09-09')
    await wrapper.find('.pin-action').trigger('click')
    expect(wrapper.emitted('notice')?.some(([message]) => String(message).includes('不在当前分析范围内'))).toBe(true)
    expect(wrapper.text()).toContain('已固定 1 / 6')
    wrapper.unmount()
  })

  it('shows the comparison preview curve while hovering an unpinned date and removes it on leave', async () => {
    useGridMapping()
    const wrapper = mount(NavChart, { props: { analysis: makeAnalysis() } })
    await flushPromises()

    fireZr('click', { offsetX: 0, offsetY: 100 })
    await flushPromises()

    fireZr('mousemove', { offsetX: 100, offsetY: 100 })
    await flushPromises()
    let comparison = wrapper.findComponent({ name: 'ComparisonChart' })
    expect(comparison.props('preview')).toMatchObject({ date: '2026-09-02' })

    fireZr('mousemove', { offsetX: 0, offsetY: 100 })
    await flushPromises()
    comparison = wrapper.findComponent({ name: 'ComparisonChart' })
    expect(comparison.props('preview')).toBeNull()

    fireZr('mousemove', { offsetX: 100, offsetY: 100 })
    fireZr('globalout')
    await flushPromises()
    expect(wrapper.findComponent({ name: 'ComparisonChart' }).props('preview')).toBeNull()
    wrapper.unmount()
  })

  it('reconciles pins when the analysis reloads and clears everything when the fund changes', async () => {
    useGridMapping()
    const analysis = makeAnalysis()
    const wrapper = mount(NavChart, { props: { analysis } })
    await flushPromises()

    fireZr('click', { offsetX: 0, offsetY: 100 })
    fireZr('click', { offsetX: 100, offsetY: 100 })
    await flushPromises()
    expect(wrapper.text()).toContain('已固定 2 / 6')

    const reloaded: FundAnalysis = { ...analysis, points: analysis.points.slice(1) }
    await wrapper.setProps({ analysis: reloaded })
    await flushPromises()
    expect(wrapper.text()).toContain('已固定 1 / 6')
    expect(wrapper.emitted('notice')?.some(([message]) => String(message).includes('2026-09-01'))).toBe(true)

    await wrapper.setProps({ analysis: { ...makeAnalysis(), fundCode: '110022' } })
    await flushPromises()
    expect(wrapper.find('.pin-chips').exists()).toBe(false)
    expect(wrapper.findComponent({ name: 'ComparisonChart' }).exists()).toBe(false)
    expect(wrapper.find('.structure-preview').exists()).toBe(false)
    wrapper.unmount()
  })

  it('shows the preview after tapping a date on touch devices and closes it explicitly', async () => {
    stubMatchMedia('coarse', true)
    useGridMapping()
    const wrapper = mount(NavChart, { props: { analysis: makeAnalysis() } })
    await flushPromises()

    /* tap pins the date and shows the preview panel */
    fireZr('click', { offsetX: 100, offsetY: 100 })
    await flushPromises()
    expect(wrapper.find('.structure-preview').exists()).toBe(true)
    expect(wrapper.find('.preview-title').text()).toContain('2026-09-02')
    expect(wrapper.text()).toContain('已固定 1 / 6')

    /* no auto-hide on touch */
    vi.advanceTimersByTime(5000)
    expect(wrapper.find('.structure-preview').exists()).toBe(true)

    /* close button dismisses the preview without unpinning */
    await wrapper.find('.preview-close').trigger('click')
    await flushPromises()
    expect(wrapper.find('.structure-preview').exists()).toBe(false)
    expect(wrapper.text()).toContain('已固定 1 / 6')
    wrapper.unmount()
  })

  it('removes zrender listeners and timers on unmount', async () => {
    useGridMapping()
    const wrapper = mount(NavChart, { props: { analysis: makeAnalysis() } })
    await flushPromises()
    expect(zrHandlers.has('mousemove')).toBe(true)
    expect(zrHandlers.has('click')).toBe(true)

    fireZr('mousemove', { offsetX: 0, offsetY: 100 })
    const timerCount = vi.getTimerCount()
    expect(timerCount).toBeGreaterThan(0)

    wrapper.unmount()
    expect(zrHandlers.size).toBe(0)
    expect(vi.getTimerCount()).toBe(0)
    expect(dispose).toHaveBeenCalled()
  })
})
