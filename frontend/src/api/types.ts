// Mirrors docs/API-CONTRACT.md — the single source of truth for these shapes.

export interface ApiEnvelope<T> {
  success: boolean
  data: T | null
  error: { code: string; message: string } | null
}

export interface Page<T> {
  content: T[]
  page: number
  size: number
  totalElements: number
  totalPages: number
}

export type EmployeeStatus = 'ACTIVE' | 'ON_HOLD'

export interface Employee {
  id: number
  employeeCode: string
  name: string
  email: string
  country: string
  department: string
  currencyCode: string
  annualSalary: number
  status: EmployeeStatus
  joinedOn: string
  version: number
}

export type ChangeType = 'PERCENT' | 'AMOUNT' | 'CORRECTION'

export interface SalaryChange {
  id: number
  employeeId: number
  oldSalary: number
  newSalary: number
  changeType: ChangeType
  percentValue: number | null
  actor: string
  bulkRaiseRunId: number | null
  createdAt: string
}

export interface SalaryChangeOutcome {
  status: 'APPLIED' | 'PARKED_FOR_REVIEW'
  change: SalaryChange | null
  employee: Employee | null
  reviewItemId: number | null
  reason: string | null
}

export type RaiseType = 'PERCENT' | 'AMOUNT'
export type JobStatus = 'QUEUED' | 'RUNNING' | 'COMPLETED'

export interface BulkRaiseRun {
  id: number
  raiseType: RaiseType
  raiseValue: number
  filterCountry: string | null
  filterDepartment: string | null
  status: JobStatus
  appliedCount: number
  reviewCount: number
  excludedCount: number
  initiatedBy: string
  createdAt: string
}

export interface BulkRaisePreview {
  affectedCount: number
  costImpact: { currencyCode: string; current: number; proposed: number; delta: number }[]
  costImpactUsdDelta: number
  recentlyRaised: { employeeId: number; employeeCode: string; name: string; lastRaiseAt: string }[]
}

export type ReviewStatus = 'PENDING' | 'APPROVED' | 'REJECTED'

export interface ReviewItem {
  id: number
  employeeId: number
  employeeCode: string | null
  name: string | null
  bulkRaiseRunId: number | null
  proposedOld: number
  proposedNew: number
  reason: string
  status: ReviewStatus
  createdAt: string
  resolvedAt: string | null
}

export interface PayrollRun {
  id: number
  year: number
  month: number
  status: JobStatus
  employeeId: number | null
  processedCount: number
  skippedHeldCount: number
  alreadyProcessedCount: number
  initiatedBy: string
  createdAt: string
}

export interface SalaryCredit {
  id: number
  employeeId: number
  year: number
  month: number
  amount: number
  currencyCode: string
  usdRate: number
  payrollRunId: number
  createdAt: string
}

export interface CurrencyRate {
  code: string
  name: string
  usdRate: number
  updatedAt: string
}

export interface Settings {
  raiseThresholdPercent: number
}

export interface SessionUser {
  username: string
  name: string
}

export interface AuditFeedItem {
  kind: 'ENTRY' | 'RUN'
  id: number
  entityType: string
  entityId: number
  action: string
  actor: string
  changedFields: Record<string, unknown> | null
  refTable: string | null
  refId: number | null
  runId: number | null
  runSummary: Record<string, unknown> | null
  createdAt: string
}

export interface AuditFeed {
  items: AuditFeedItem[]
  nextCursor: string | null
}

export interface AnalyticsSummary {
  totalMonthlySpendUsd: number
  headcount: number
  onHoldCount: number
  lastPayrollRun: { year: number; month: number; processedCount: number; createdAt: string } | null
}

export interface CountrySpend {
  country: string
  headcount: number
  monthlySpendUsd: number
}

export interface DepartmentStats {
  department: string
  headcount: number
  avgAnnualUsd: number
  medianAnnualUsd: number
}

export interface SalaryDistribution {
  bucketUsd: number
  buckets: { bucketFloorUsd: number; bucketCeilingUsd: number; count: number }[]
}
