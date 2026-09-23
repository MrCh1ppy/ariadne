import { describe, expect, it, vi } from 'vitest'
import { ApiError, searchFunds } from '../api'
import { RequestGate } from './requests'

describe('request safety', () => {
  it('aborts and invalidates stale requests', () => {
    const gate = new RequestGate()
    const first = gate.next()
    const second = gate.next()
    expect(first.signal.aborted).toBe(true)
    expect(gate.isCurrent(first.id)).toBe(false)
    expect(gate.isCurrent(second.id)).toBe(true)
  })

  it('keeps a typed API error for safe UI mapping', () => {
    const error = new ApiError(502, '数据源暂时不可用，请稍后重试。')
    expect(error.status).toBe(502)
    expect(error.message).toContain('数据源')
  })

  it('maps a backend failure without swallowing it', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response(JSON.stringify({ message: 'upstream unavailable' }), {
      status: 502,
      headers: { 'Content-Type': 'application/json' },
    })))
    await expect(searchFunds('022', new AbortController().signal)).rejects.toMatchObject({
      status: 502,
      message: '数据源暂时不可用，请稍后重试。',
    })
    vi.unstubAllGlobals()
  })

  it('sends the selected code prefix as the search query', async () => {
    const fetchMock = vi.fn().mockResolvedValue(new Response('[]', { status: 200 }))
    vi.stubGlobal('fetch', fetchMock)
    await searchFunds('022', new AbortController().signal)
    expect(fetchMock.mock.calls[0][0]).toBe('/api/funds/search?prefix=022')
    vi.unstubAllGlobals()
  })
})
