import type { FundAnalysis, FundOption } from './types'

const base = (import.meta.env.VITE_API_BASE || '/api').replace(/\/$/, '')

export class ApiError extends Error {
  constructor(public readonly status: number, message: string) {
    super(message)
    this.name = 'ApiError'
  }
}

function userMessage(status: number, message: string): string {
  if (status === 400) return message || '请求范围无效，请检查基金代码和日期。'
  if (status === 404) return '没有找到对应基金。'
  if (status === 502) return '数据源暂时不可用，请稍后重试。'
  if (status === 503) return '数据库暂时不可用，请稍后重试。'
  return message || '请求失败，请稍后重试。'
}

async function getJson<T>(path: string, signal: AbortSignal): Promise<T> {
  let response: Response
  try {
    response = await fetch(`${base}${path}`, { signal })
  } catch (error) {
    if (error instanceof DOMException && error.name === 'AbortError') throw error
    throw new ApiError(0, '网络连接失败，请确认 Java 服务正在运行。')
  }
  const body = await response.json().catch(() => ({})) as { message?: string }
  if (!response.ok) throw new ApiError(response.status, userMessage(response.status, body.message || ''))
  return body as T
}

export function searchFunds(prefix: string, signal: AbortSignal): Promise<FundOption[]> {
  return getJson<FundOption[]>(`/funds/search?prefix=${encodeURIComponent(prefix)}`, signal)
}

export function loadAnalysis(fundCode: string, startDate: string, endDate: string, signal: AbortSignal): Promise<FundAnalysis> {
  const query = new URLSearchParams({ startDate, endDate })
  return getJson<FundAnalysis>(`/funds/${encodeURIComponent(fundCode)}/analysis?${query}`, signal)
}
