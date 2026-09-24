import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import type { MaStructure } from '../lib/maStructure'

const setOption = vi.hoisted(() => vi.fn<(options: unknown) => void>())
const chartOn = vi.hoisted(() => vi.fn())
const chartDispose = vi.hoisted(() => vi.fn())
vi.mock('echarts', () => ({
  init: vi.fn(() => ({ setOption, on: chartOn, resize: vi.fn(), dispose: chartDispose })),
}))

import ComparisonChart from './ComparisonChart.vue'

function structure(date: string, values: (number | null)[]): MaStructure {
  const names = ['MA120', 'MA60', 'MA30', 'MA15', 'MA5', '单位净值'] as const
  return { date, entries: names.map((name, index) => ({ name, value: values[index] ?? null })) }
}

describe('comparison chart', () => {
  beforeEach(() => {
    setOption.mockClear()
    chartOn.mockClear()
    chartDispose.mockClear()
    vi.stubGlobal('matchMedia', () => ({ matches: false, addEventListener: vi.fn(), removeEventListener: vi.fn() }))
  })

  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it('plots every pinned structure on one shared value axis with the date as legend name', async () => {
    const wrapper = mount(ComparisonChart, {
      props: {
        pinned: [
          structure('2026-09-01', [0.8, null, 0.9, 0.95, 0.98, 1.0]),
          structure('2026-09-02', [null, 0.85, 0.92, null, 1.0, 1.01]),
        ],
        preview: null,
        reducedMotion: false,
      },
    })
    await flushPromises()
    const option = setOption.mock.lastCall?.[0] as {
      yAxis: { type: string }
      series: { name: string; data: (number | null)[]; lineStyle: { type?: string } }[]
      xAxis: { data: string[] }
    }
    expect(option.yAxis.type).toBe('value')
    expect(option.xAxis.data).toEqual(['MA120', 'MA60', 'MA30', 'MA15', 'MA5', '单位净值'])
    expect(option.series.map((series) => series.name)).toEqual(['2026-09-01', '2026-09-02'])
    expect(option.series[0]?.data).toEqual([0.8, null, 0.9, 0.95, 0.98, 1.0])
    expect(option.series.every((series) => series.lineStyle.type !== 'dashed')).toBe(true)
    wrapper.unmount()
  })

  it('appends the hover preview as a dashed transient series without duplicating pinned dates', async () => {
    const pinned = [structure('2026-09-01', [0.8, null, 0.9, 0.95, 0.98, 1.0])]
    const wrapper = mount(ComparisonChart, {
      props: { pinned, preview: structure('2026-09-02', [1, 1, 1, 1, 1, 1]), reducedMotion: false },
    })
    await flushPromises()
    let option = setOption.mock.lastCall?.[0] as { series: { name: string; lineStyle: { type?: string } }[] }
    expect(option.series.map((series) => series.name)).toEqual(['2026-09-01', '2026-09-02（预览）'])
    expect(option.series[1]?.lineStyle.type).toBe('dashed')

    await wrapper.setProps({ preview: structure('2026-09-01', [1, 1, 1, 1, 1, 1]) })
    await flushPromises()
    option = setOption.mock.lastCall?.[0] as typeof option
    expect(option.series.map((series) => series.name)).toEqual(['2026-09-01'])

    await wrapper.setProps({ preview: null })
    await flushPromises()
    option = setOption.mock.lastCall?.[0] as typeof option
    expect(option.series).toHaveLength(1)
    wrapper.unmount()
  })

  it('emits unpin when a pinned date legend entry is toggled off', async () => {
    const wrapper = mount(ComparisonChart, {
      props: { pinned: [structure('2026-09-01', [0.8, null, 0.9, 0.95, 0.98, 1.0])], preview: null, reducedMotion: false },
    })
    await flushPromises()
    const legendHandler = chartOn.mock.calls.find(([name]) => name === 'legendselectchanged')?.[1] as
      (event: { selected: Record<string, boolean> }) => void
    expect(legendHandler).toBeDefined()
    legendHandler({ selected: { '2026-09-01': false } })
    expect(wrapper.emitted('unpin')).toEqual([['2026-09-01']])

    legendHandler({ selected: { '2026-09-01': true } })
    expect(wrapper.emitted('unpin')).toHaveLength(1)
    wrapper.unmount()
  })

  it('disposes its chart and listeners on unmount', async () => {
    const wrapper = mount(ComparisonChart, {
      props: { pinned: [structure('2026-09-01', [0.8, null, 0.9, 0.95, 0.98, 1.0])], preview: null, reducedMotion: false },
    })
    await flushPromises()
    wrapper.unmount()
    expect(chartDispose).toHaveBeenCalled()
  })
})
