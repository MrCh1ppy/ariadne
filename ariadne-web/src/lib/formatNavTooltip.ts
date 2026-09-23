import type { AnalysisPoint } from '../types'
import { displayMaPercent } from './maPercent'

function escapeHtml(value: string): string {
  return value.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;').replace(/'/g, '&#39;')
}

export function formatNavTooltip(point: AnalysisPoint): string {
  const comparison30 = displayMaPercent(point.navVsMa30Percent)
  const comparison60 = displayMaPercent(point.navVsMa60Percent)
  return [
    `<div style="font-weight:700;margin-bottom:2px">${escapeHtml(point.date)}</div>`,
    `<div><span style="display:inline-block;width:8px;height:8px;border-radius:2px;background:#1b5f9e;margin-right:6px"></span>单位净值：${point.unitNav === null ? '暂无' : escapeHtml(point.unitNav)}</div>`,
    `<div><span style="display:inline-block;width:8px;height:8px;border-radius:2px;background:#0a9f98;margin-right:6px"></span>MA30：${point.ma30 === null ? '暂无' : escapeHtml(point.ma30)}</div>`,
    `<div>相对 MA30：<span style="color:${comparison30.color}">${comparison30.text}</span></div>`,
    `<div><span style="display:inline-block;width:8px;height:8px;border-radius:2px;background:#8670af;margin-right:6px"></span>MA60：${point.ma60 === null ? '暂无' : escapeHtml(point.ma60)}</div>`,
    `<div>相对 MA60：<span style="color:${comparison60.color}">${comparison60.text}</span></div>`,
  ].join('')
}
