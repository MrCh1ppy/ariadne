import { describe, expect, it } from 'vitest'
import { formatNavTooltip } from './formatNavTooltip'

describe('NAV chart tooltip', () => {
  it('shows only date and NAV even when every MA is present', () => {
    const html = formatNavTooltip({
      date: '2026-09-22', unitNav: '1.2345000000', movingAverages: {
        MA5: { value: '1.20', deviationPercent: '2.88' },
        MA15: { value: '1.22', deviationPercent: '1.19' },
        MA30: { value: '1.23', deviationPercent: '0.37' },
        MA60: { value: '1.24', deviationPercent: '-0.44' },
        MA120: { value: '1.25', deviationPercent: '-1.24' },
      },
    })
    expect(html).toContain('2026-09-22')
    expect(html).toContain('单位净值：1.2345000000')
    expect(html).not.toMatch(/MA\d+|相对/)
  })

  it('shows missing NAV and escapes upstream strings', () => {
    const html = formatNavTooltip({ date: '<script>', unitNav: null, movingAverages: {} })
    expect(html).toContain('&lt;script&gt;')
    expect(html).toContain('单位净值：暂无')
    expect(formatNavTooltip({ date: '2026-09-22', unitNav: '<img>', movingAverages: {} })).toContain('&lt;img&gt;')
    expect(html).not.toContain('<script>')
  })
})
