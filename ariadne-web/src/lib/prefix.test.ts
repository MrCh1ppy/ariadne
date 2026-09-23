import { describe, expect, it } from 'vitest'
import { isSearchablePrefix, normalizePrefix } from './prefix'

describe('code prefix input', () => {
  it('keeps digits and leading zeroes, capped at six', () => {
    expect(normalizePrefix('a0222488')).toBe('022248')
    expect(isSearchablePrefix('022')).toBe(true)
    expect(isSearchablePrefix('02')).toBe(false)
    expect(isSearchablePrefix('485')).toBe(true)
    expect(isSearchablePrefix('abcdef')).toBe(false)
  })
})
