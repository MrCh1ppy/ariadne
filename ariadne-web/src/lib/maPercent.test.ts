import { describe, expect, it } from 'vitest'
import { displayMaPercent } from './maPercent'

describe('MA30 comparison presentation', () => {
  it('shows signed percentages and missing values without injecting markup', () => {
    expect(displayMaPercent('1.23').text).toBe('+1.23%')
    expect(displayMaPercent('-0.45').text).toBe('−0.45%')
    expect(displayMaPercent('0.00').text).toBe('0.00%')
    expect(displayMaPercent(null).text).toBe('暂无')
    expect(displayMaPercent('<img src=x>').text).toBe('暂无')
    expect(displayMaPercent('NaN').text).toBe('暂无')
  })
})
