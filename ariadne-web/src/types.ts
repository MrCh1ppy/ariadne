export interface FundOption {
  fundCode: string
  fundName: string
}

export interface AnalysisPoint {
  date: string
  unitNav: string | null
  ma30: string | null
}

export interface FundAnalysis {
  fundCode: string
  startDate: string
  endDate: string
  points: AnalysisPoint[]
  warnings: string[]
}
