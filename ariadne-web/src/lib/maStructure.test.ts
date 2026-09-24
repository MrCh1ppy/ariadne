import { describe, expect, it } from 'vitest'
import type { AnalysisPoint } from '../types'
import {
  MAX_PINNED_DATES,
  buildMaStructure,
  formatStructureValue,
  hasAnyValue,
  locateCategoryIndex,
  reconcilePinned,
  valuesOf,
} from './maStructure'

function point(date: string, unitNav: string | null, ma: Partial<Record<'MA5' | 'MA15' | 'MA30' | 'MA60' | 'MA120', { value: string | null; deviationPercent: string | null }>>): AnalysisPoint {
  return { date, unitNav, movingAverages: ma }
}

const full = point('2026-09-22', '1.0500', {
  MA5: { value: '1.0100000000', deviationPercent: null },
  MA15: { value: '1.0200000000', deviationPercent: null },
  MA30: { value: '1.0300000000', deviationPercent: null },
  MA60: { value: '1.0400000000', deviationPercent: null },
  MA120: { value: '0.9900000000', deviationPercent: null },
})

describe('maStructure builder', () => {
  it('orders entries MA120 -> MA60 -> MA30 -> MA15 -> MA5 -> NAV and converts to numbers', () => {
    const structure = buildMaStructure(full)
    expect(structure.date).toBe('2026-09-22')
    expect(structure.entries.map((entry) => entry.name)).toEqual(['MA120', 'MA60', 'MA30', 'MA15', 'MA5', '单位净值'])
    expect(valuesOf(structure)).toEqual([0.99, 1.04, 1.03, 1.02, 1.01, 1.05])
  })

  it('keeps null gaps for missing windows, missing series and missing NAV', () => {
    const structure = buildMaStructure(point('2026-09-01', null, {
      MA5: { value: '1.0', deviationPercent: null },
      MA60: { value: null, deviationPercent: null },
    }))
    expect(structure.entries.map((entry) => entry.name)).toEqual(['MA120', 'MA60', 'MA30', 'MA15', 'MA5', '单位净值'])
    expect(valuesOf(structure)).toEqual([null, null, null, null, 1, null])
  })

  it('reports a fully empty structure as having no values', () => {
    const empty = buildMaStructure(point('2026-09-01', null, {}))
    expect(hasAnyValue(empty)).toBe(false)
    expect(hasAnyValue(buildMaStructure(full))).toBe(true)
  })

  it('formats missing values distinctly from zero', () => {
    expect(formatStructureValue(null)).toBe('缺失')
    expect(formatStructureValue(0.98)).toBe('0.98')
  })
})

describe('reconcilePinned', () => {
  const points = [full]

  it('keeps still-valid pinned dates in original order', () => {
    const result = reconcilePinned(['2026-09-22'], points)
    expect(result.dates).toEqual(['2026-09-22'])
    expect(result.dropped).toEqual([])
    expect(result.overflow).toEqual([])
  })

  it('drops dates that disappeared from the analysis', () => {
    const result = reconcilePinned(['2026-01-04', '2026-09-22'], points)
    expect(result.dates).toEqual(['2026-09-22'])
    expect(result.dropped).toEqual(['2026-01-04'])
  })

  it('drops dates whose structure became fully empty', () => {
    const empty = point('2026-09-23', null, { MA5: { value: null, deviationPercent: null } })
    const result = reconcilePinned(['2026-09-22', '2026-09-23'], [full, empty])
    expect(result.dates).toEqual(['2026-09-22'])
    expect(result.dropped).toEqual(['2026-09-23'])
  })

  it('overflows oldest pins when the cap is exceeded', () => {
    const many = Array.from({ length: MAX_PINNED_DATES + 2 }, (_, index) =>
      point(`2026-09-${String(index + 1).padStart(2, '0')}`, '1.0', {}))
    const result = reconcilePinned(many.map((p) => p.date), many)
    expect(result.dates).toHaveLength(MAX_PINNED_DATES)
    expect(result.overflow).toEqual(['2026-09-01', '2026-09-02'])
    expect(result.dates).not.toContain('2026-09-01')
    expect(result.dates).toContain('2026-09-08')
  })
})

describe('locateCategoryIndex', () => {
  const contain = (offset: [number, number]) => offset[0] <= 500 && offset[1] <= 300
  const distance = (offset: [number, number]) => (offset[0] / 100)

  it('returns the nearest category index inside the grid', () => {
    expect(locateCategoryIndex(contain, distance, [202, 150], 6)).toBe(2)
    expect(locateCategoryIndex(contain, distance, [0, 299], 6)).toBe(0)
    expect(locateCategoryIndex(contain, distance, [499, 150], 6)).toBe(5)
  })

  it('rejects pixels outside the grid or below the x axis', () => {
    expect(locateCategoryIndex(contain, distance, [600, 150], 6)).toBeNull()
    expect(locateCategoryIndex(contain, distance, [200, 301], 6)).toBeNull()
  })

  it('rejects indices beyond the data range', () => {
    expect(locateCategoryIndex(() => true, () => 6.2, [100, 100], 6)).toBeNull()
    expect(locateCategoryIndex(() => true, () => -1, [100, 100], 6)).toBeNull()
  })

  it('rejects conversions that are not usable numbers', () => {
    expect(locateCategoryIndex(() => true, () => null, [100, 100], 6)).toBeNull()
    expect(locateCategoryIndex(() => true, () => Number.NaN, [100, 100], 6)).toBeNull()
  })

  it('rounds to the nearest category band like the axis tooltip', () => {
    expect(locateCategoryIndex(() => true, () => 2.6, [100, 100], 6)).toBe(3)
    expect(locateCategoryIndex(() => true, () => 2.49, [100, 100], 6)).toBe(2)
    expect(locateCategoryIndex(() => true, () => 5.49, [100, 100], 6)).toBe(5)
    expect(locateCategoryIndex(() => true, () => 5.5, [100, 100], 6)).toBeNull()
  })

  it('rejects empty data sets', () => {
    expect(locateCategoryIndex(() => true, () => 0, [100, 100], 0)).toBeNull()
  })
})
