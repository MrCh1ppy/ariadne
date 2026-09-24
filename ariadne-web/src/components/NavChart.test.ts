import { describe, expect, it, vi } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import type { FundAnalysis } from '../types'

const setOption = vi.hoisted(() => vi.fn<(options: unknown) => void>())
vi.mock('echarts', () => ({ init: () => ({ setOption, resize: vi.fn(), dispose: vi.fn() }) }))

import NavChart from './NavChart.vue'

describe('NAV chart', () => {
  it('plots NAV and five averages, preserving gaps and confining the tooltip', async () => {
    vi.stubGlobal('matchMedia', () => ({ matches: true, addEventListener: vi.fn(), removeEventListener: vi.fn() }))
    const analysis: FundAnalysis = {
      fundCode: '022485', startDate: '2026-09-21', endDate: '2026-09-22', warnings: [],
      points: [
        { date: '2026-09-21', unitNav: '1.0000', movingAverages: {
          MA5: { value: '0.9800000000', deviationPercent: '2.04' },
          MA15: { value: '0.9500000000', deviationPercent: '5.26' },
          MA30: { value: '0.9000000000', deviationPercent: '11.11' },
          MA60: { value: null, deviationPercent: null },
          MA120: { value: '0.8000000000', deviationPercent: '25.00' },
        } },
        { date: '2026-09-22', unitNav: null, movingAverages: {
          MA5: { value: null, deviationPercent: null },
          MA15: { value: null, deviationPercent: null },
          MA30: { value: null, deviationPercent: null },
          MA60: { value: null, deviationPercent: null },
          MA120: { value: null, deviationPercent: null },
        } },
      ],
    }
    const wrapper = mount(NavChart, { props: { analysis } })
    await flushPromises()
    const option = setOption.mock.lastCall?.[0] as {
      tooltip: { confine: boolean; formatter: (items: { axisValue: string }[]) => string }
      series: { name: string; data: (number | null)[]; connectNulls: boolean; smooth: boolean; lineStyle: { type?: string }; areaStyle?: unknown }[]
      legend: { type: string; left: number; right: number }
      color: string[]
      animation: boolean
    }
    expect(option.series.map((series) => series.name)).toEqual(['单位净值', 'MA5', 'MA15', 'MA30', 'MA60', 'MA120'])
    expect(option.series.map((series) => series.data)).toEqual([[1, null], [0.98, null], [0.95, null], [0.9, null], [null, null], [0.8, null]])
    expect(option.color).toEqual(['#1b5f9e', '#5b66ad', '#a85c9a', '#0a9f98', '#7a6fd0', '#d08a3e'])
    expect(option.series.map((series) => series.lineStyle.type)).toEqual([undefined, undefined, 'dashed', undefined, 'dashed', 'dotted'])
    expect(option.series[0]?.areaStyle).toBeDefined()
    expect(option.animation).toBe(false)
    expect(option.legend).toMatchObject({ type: 'scroll', left: 0, right: 0 })
    expect(option.series.every((series) => !series.connectNulls && !series.smooth)).toBe(true)
    expect(option.tooltip.confine).toBe(true)
    expect(option.tooltip.formatter([{ axisValue: '2026-09-22' }])).toContain('相对 MA60：<span style="color:#eef4f8">暂无</span>')
    expect(option.tooltip.formatter([{ axisValue: '2026-09-22' }])).toContain('MA5：暂无')
    wrapper.unmount()
    vi.unstubAllGlobals()
  })
})
