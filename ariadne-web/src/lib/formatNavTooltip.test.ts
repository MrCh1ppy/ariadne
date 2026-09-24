import { describe, expect, it } from 'vitest'
import { formatNavTooltip } from './formatNavTooltip'
import type { AnalysisPoint } from '../types'

describe('NAV chart tooltip', () => {
  it('shows averages and signed comparisons without calculating them', () => {
    const point: AnalysisPoint = {
      date: '2026-09-22', unitNav: '1.2345000000', movingAverages: {
        MA30: { value: '1.2300000000', deviationPercent: '0.37' },
        MA60: { value: '1.2400000000', deviationPercent: '-0.44' },
        MA120: { value: '1.2500000000', deviationPercent: '-1.24' },
      },
    }
    const html = formatNavTooltip(point)
    expect(html).toContain('MA30：1.2300000000')
    expect(html).toContain('相对 MA30：<span style="color:#ffba95">+0.37%</span>')
    expect(html).toContain('MA60：1.2400000000')
    expect(html).toContain('相对 MA60：<span style="color:#79d4cc">−0.44%</span>')
    expect(html).toContain('MA120：1.2500000000')
    expect(html).toContain('相对 MA120：<span style="color:#79d4cc">−1.24%</span>')
    expect(html).not.toContain('收益率')
  })

  it('leaves unavailable values blank and escapes upstream strings', () => {
    const html = formatNavTooltip({
      date: '<script>', unitNav: '<img>', movingAverages: {
        MA30: { value: '1.0000000000', deviationPercent: '0.00' },
        MA60: { value: null, deviationPercent: '<svg>' },
      },
    })
    expect(html).toContain('&lt;script&gt;')
    expect(html).toContain('&lt;img&gt;')
    expect(html).toContain('相对 MA30：<span style="color:#eef4f8">0.00%</span>')
    expect(html).toContain('MA60：暂无')
    expect(html).toContain('相对 MA60：<span style="color:#eef4f8">暂无</span>')
    expect(html).not.toContain('<svg>')
    expect(html).not.toContain('MA120：')
  })
})
