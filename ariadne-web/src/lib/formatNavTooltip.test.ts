import { describe, expect, it } from 'vitest'
import { formatNavTooltip } from './formatNavTooltip'
import type { AnalysisPoint } from '../types'

describe('NAV chart tooltip', () => {
  it('shows averages and signed comparisons without calculating them', () => {
    const point: AnalysisPoint = {
      date: '2026-09-22', unitNav: '1.2345000000', movingAverages: {
        MA5: { value: '1.2000000000', deviationPercent: '2.88' },
        MA15: { value: '1.2200000000', deviationPercent: '1.19' },
        MA30: { value: '1.2300000000', deviationPercent: '0.37' },
        MA60: { value: '1.2400000000', deviationPercent: '-0.44' },
        MA120: { value: '1.2500000000', deviationPercent: '-1.24' },
      },
    }
    const html = formatNavTooltip(point)
    expect(html.indexOf('MA5：')).toBeLessThan(html.indexOf('MA15：'))
    expect(html.indexOf('MA15：')).toBeLessThan(html.indexOf('MA30：'))
    expect(html).toContain('background:#5b66ad;margin-right:6px"></span>MA5：1.2000000000')
    expect(html).toContain('background:#a85c9a;margin-right:6px"></span>MA15：1.2200000000')
    expect(html).toContain('MA30：1.2300000000')
    expect(html).toContain('相对 MA30：<span style="color:#ffba95">+0.37%</span>')
    expect(html).toContain('MA60：1.2400000000')
    expect(html).toContain('background:#7a6fd0;margin-right:6px"></span>MA60：')
    expect(html).toContain('相对 MA60：<span style="color:#79d4cc">−0.44%</span>')
    expect(html).toContain('MA120：1.2500000000')
    expect(html).toContain('background:#d08a3e;margin-right:6px"></span>MA120：')
    expect(html).toContain('相对 MA120：<span style="color:#79d4cc">−1.24%</span>')
    expect(html).not.toContain('收益率')
  })

  it('leaves unavailable values blank and escapes upstream strings', () => {
    const html = formatNavTooltip({
      date: '<script>', unitNav: '<img>', movingAverages: {
        MA5: { value: null, deviationPercent: null },
        MA30: { value: '1.0000000000', deviationPercent: '0.00' },
        MA60: { value: null, deviationPercent: '<svg>' },
      },
    })
    expect(html).toContain('&lt;script&gt;')
    expect(html).toContain('&lt;img&gt;')
    expect(html).toContain('相对 MA30：<span style="color:#eef4f8">0.00%</span>')
    expect(html).toContain('MA60：暂无')
    expect(html).toContain('MA5：暂无')
    expect(html).toContain('相对 MA5：<span style="color:#eef4f8">暂无</span>')
    expect(html).not.toContain('MA15：')
    expect(html).toContain('相对 MA60：<span style="color:#eef4f8">暂无</span>')
    expect(html).not.toContain('<svg>')
    expect(html).not.toContain('MA120：')
  })
})
