export function displayMaPercent(percent: string | null): { text: string; color: string } {
  if (percent === null || !/^-?(?:0|[1-9]\d*)\.\d{2}$/.test(percent)) return { text: '暂无', color: '#eef4f8' }
  if (percent.startsWith('-')) return { text: `−${percent.slice(1)}%`, color: '#79d4cc' }
  if (percent === '0.00') return { text: '0.00%', color: '#eef4f8' }
  return { text: `+${percent}%`, color: '#ffba95' }
}
