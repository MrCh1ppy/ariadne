import type { AnalysisPoint } from '../types'
import { displayMaPercent } from './maPercent'

export const maColors = [['MA5', '#5b66ad'], ['MA15', '#a85c9a'], ['MA30', '#0a9f98'], ['MA60', '#7a6fd0'], ['MA120', '#d08a3e']] as const

function escapeHtml(value: string): string {
  return value.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;').replace(/'/g, '&#39;')
}

export function formatNavTooltip(point: AnalysisPoint): string {
  const rows = [
    `<div style="font-weight:700;margin-bottom:2px">${escapeHtml(point.date)}</div>`,
    `<div><span style="display:inline-block;width:8px;height:8px;border-radius:2px;background:#1b5f9e;margin-right:6px"></span>单位净值：${point.unitNav === null ? '暂无' : escapeHtml(point.unitNav)}</div>`,
  ]
  for (const [period, color] of maColors) {
    const ma = point.movingAverages[period]
    if (!ma) continue
    const comparison = displayMaPercent(ma.deviationPercent)
    rows.push(`<div><span style="display:inline-block;width:8px;height:8px;border-radius:2px;background:${color};margin-right:6px"></span>${period}：${ma.value === null ? '暂无' : escapeHtml(ma.value)}</div>`)
    rows.push(`<div>相对 ${period}：<span style="color:${comparison.color}">${comparison.text}</span></div>`)
  }
  return rows.join('')
}
