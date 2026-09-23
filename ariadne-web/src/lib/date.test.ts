import { describe, expect, it } from 'vitest'
import { defaultDateRange, shiftDate } from './date'

describe('date helpers', () => {
  it('handles month and year boundaries with 30 natural days', () => {
    expect(defaultDateRange('2025-01-02')).toEqual({ startDate: '2024-12-04', endDate: '2025-01-02' })
    expect(shiftDate('2024-03-01', -29)).toBe('2024-02-01')
  })
})
