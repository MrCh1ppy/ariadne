import type { AnalysisPoint } from '../types'

export const MA_KEYS = ['MA120', 'MA60', 'MA30', 'MA15', 'MA5'] as const
export type MaKey = (typeof MA_KEYS)[number]
export const STRUCTURE_SERIES = [...MA_KEYS, '单位净值'] as const
export type StructureSeriesName = (typeof STRUCTURE_SERIES)[number]

export const STRUCTURE_COLORS: Record<StructureSeriesName, string> = {
  MA120: '#d08a3e',
  MA60: '#7a6fd0',
  MA30: '#0a9f98',
  MA15: '#a85c9a',
  MA5: '#5b66ad',
  '单位净值': '#1b5f9e',
}

export interface MaStructure {
  date: string
  entries: { name: StructureSeriesName; value: number | null }[]
}

export function buildMaStructure(point: AnalysisPoint): MaStructure {
  return {
    date: point.date,
    entries: [
      ...MA_KEYS.map((key) => ({
        name: key as StructureSeriesName,
        value: point.movingAverages[key]?.value == null ? null : Number(point.movingAverages[key]?.value),
      })),
      { name: '单位净值' as StructureSeriesName, value: point.unitNav === null ? null : Number(point.unitNav) },
    ],
  }
}

export function hasAnyValue(structure: MaStructure): boolean {
  return structure.entries.some((entry) => entry.value !== null)
}

export function valuesOf(structure: MaStructure): (number | null)[] {
  return structure.entries.map((entry) => entry.value)
}

/** NAV deviation from each MA; only used by the structure preview. */
export function navDeviationValuesOf(structure: MaStructure): (number | null)[] {
  const nav = structure.entries.find((entry) => entry.name === '单位净值')?.value ?? null
  return structure.entries
    .filter((entry) => entry.name !== '单位净值')
    .map((entry) => navDeviationPercent(nav, entry.value))
}

export function formatStructureValue(value: number | null): string {
  return value === null ? '缺失' : String(value)
}

/** NAV relative to MA; computed locally for the structure preview. */
export function navDeviationPercent(nav: number | null, ma: number | null): number | null {
  if (ma === null || nav === null || ma === 0) return null
  const percent = (nav - ma) / ma * 100
  return Number.isFinite(percent) ? percent : null
}

export function formatNavDeviationPercent(nav: number | null, ma: number | null): string {
  const percent = navDeviationPercent(nav, ma)
  if (percent === null) return '—'
  const rounded = percent.toFixed(2)
  if (rounded === '-0.00') return '0.00%'
  return `${percent > 0 && rounded !== '0.00' ? '+' : ''}${rounded}%`
}

export const MAX_PINNED_DATES = 6

export interface PinnedReconcileResult {
  dates: string[]
  dropped: string[]
  overflow: string[]
}

/**
 * Reconcile pinned dates against a freshly loaded analysis:
 * keep every pinned date that still exists and still carries at least one
 * non-null structure value, preserving the original pin order; dates that
 * disappeared or became empty are reported as dropped. If the kept dates
 * exceed the cap, the oldest pins overflow out.
 */
export function reconcilePinned(pinned: readonly string[], points: readonly AnalysisPoint[]): PinnedReconcileResult {
  const byDate = new Map(points.map((point) => [point.date, point]))
  const kept: string[] = []
  const dropped: string[] = []
  for (const date of pinned) {
    const point = byDate.get(date)
    if (point && hasAnyValue(buildMaStructure(point))) kept.push(date)
    else dropped.push(date)
  }
  const overflow = kept.length > MAX_PINNED_DATES ? kept.splice(0, kept.length - MAX_PINNED_DATES) : []
  return { dates: kept, dropped, overflow }
}

/**
 * Locate the nearest category index for a pixel offset inside the main chart.
 * Returns null when the pixel falls outside the grid, the x-axis band, or
 * when the converted index does not correspond to a real data point.
 * The `distance` callback captures chart.convertFromPixel in tests.
 */
export function locateCategoryIndex(
  contain: (offset: [number, number]) => boolean,
  distance: (offset: [number, number]) => number | null | undefined,
  offset: [number, number],
  pointCount: number,
): number | null {
  if (pointCount <= 0 || !contain(offset)) return null
  const converted = distance(offset)
  if (converted == null || !Number.isFinite(converted)) return null
  const index = Math.round(converted)
  if (index < 0 || index >= pointCount) return null
  return index
}
