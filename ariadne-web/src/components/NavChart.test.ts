import { describe, expect, it, vi } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import type { FundAnalysis } from '../types'

const setOption = vi.hoisted(() => vi.fn<(options: unknown) => void>())
vi.mock('echarts', () => ({ init: () => ({ setOption, resize: vi.fn(), dispose: vi.fn() }) }))

import NavChart from './NavChart.vue'

describe('NAV chart', () => {
  it('plots only NAV, MA30 and MA60, preserving gaps and confining the tooltip', async () => {
    vi.stubGlobal('matchMedia', () => ({ matches: true, addEventListener: vi.fn(), removeEventListener: vi.fn() }))
    const analysis: FundAnalysis = {
      fundCode: '022485', startDate: '2026-09-21', endDate: '2026-09-22', warnings: [],
      points: [
        { date: '2026-09-21', unitNav: '1.0000', ma30: '0.9000000000', navVsMa30Percent: '11.11', ma60: null, navVsMa60Percent: null },
        { date: '2026-09-22', unitNav: null, ma30: null, navVsMa30Percent: null, ma60: null, navVsMa60Percent: null },
      ],
    }
    const wrapper = mount(NavChart, { props: { analysis } })
    await flushPromises()
    const option = setOption.mock.lastCall?.[0] as {
      tooltip: { confine: boolean; formatter: (items: { axisValue: string }[]) => string }
      series: { name: string; data: (number | null)[]; connectNulls: boolean; smooth: boolean }[]
    }
    expect(option.series.map((series) => series.name)).toEqual(['单位净值', 'MA30', 'MA60'])
    expect(option.series.map((series) => series.data)).toEqual([[1, null], [0.9, null], [null, null]])
    expect(option.series.every((series) => !series.connectNulls && !series.smooth)).toBe(true)
    expect(option.tooltip.confine).toBe(true)
    expect(option.tooltip.formatter([{ axisValue: '2026-09-22' }])).toContain('相对 MA60：<span style="color:#eef4f8">暂无</span>')
    wrapper.unmount()
    vi.unstubAllGlobals()
  })
})
