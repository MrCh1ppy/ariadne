export interface FundOption {
  fundCode: string
  fundName: string
}

export interface AnalysisPoint {
  date: string
  unitNav: string | null
  movingAverages: Partial<Record<'MA5' | 'MA15' | 'MA30' | 'MA60' | 'MA120', {
    value: string | null
    deviationPercent: string | null
  }>>
}

export interface FundAnalysis {
  fundCode: string
  startDate: string
  endDate: string
  points: AnalysisPoint[]
  warnings: string[]
}
