import { useEffect, useState } from 'react'
import { get } from '../api/client'
import type { AnalyticsSummary, CountrySpend, DepartmentStats, SalaryDistribution } from '../api/types'
import { useToast } from '../components/Toaster'
import { Spinner, formatDateTime, formatMoney } from '../components/ui'

const MONTHS = ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec']

/** Analytics dashboard — every number is a backend SQL aggregate; the charts are pure CSS bars. */
export default function DashboardPage() {
  const toast = useToast()
  const [summary, setSummary] = useState<AnalyticsSummary | null>(null)
  const [countries, setCountries] = useState<CountrySpend[] | null>(null)
  const [departments, setDepartments] = useState<DepartmentStats[] | null>(null)
  const [distribution, setDistribution] = useState<SalaryDistribution | null>(null)

  useEffect(() => {
    Promise.all([
      get<AnalyticsSummary>('/analytics/summary'),
      get<CountrySpend[]>('/analytics/by-country'),
      get<DepartmentStats[]>('/analytics/by-department'),
      get<SalaryDistribution>('/analytics/salary-distribution'),
    ])
      .then(([s, c, d, dist]) => {
        setSummary(s)
        setCountries(c)
        setDepartments(d)
        setDistribution(dist)
      })
      .catch((e) => toast.error(e.message))
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  if (!summary || !countries || !departments || !distribution) return <Spinner />

  const maxCountrySpend = Math.max(...countries.map((c) => c.monthlySpendUsd), 1)
  const maxDeptAvg = Math.max(...departments.map((d) => Math.max(d.avgAnnualUsd, d.medianAnnualUsd)), 1)
  const maxBucket = Math.max(...distribution.buckets.map((b) => b.count), 1)

  return (
    <div className="stack">
      <div className="page-header">
        <div>
          <h2 className="page-title">Dashboard</h2>
          <div className="page-subtitle">How the organization pays people — USD-normalized at current rates</div>
        </div>
      </div>

      <div className="stat-row">
        <div className="stat">
          <div className="stat-label">Monthly spend (USD)</div>
          <div className="stat-value">{formatMoney(summary.totalMonthlySpendUsd)}</div>
        </div>
        <div className="stat">
          <div className="stat-label">Headcount</div>
          <div className="stat-value">{summary.headcount.toLocaleString()}</div>
        </div>
        <div className="stat">
          <div className="stat-label">On hold</div>
          <div className="stat-value" style={{ color: summary.onHoldCount > 0 ? 'var(--warn)' : undefined }}>
            {summary.onHoldCount.toLocaleString()}
          </div>
        </div>
        <div className="stat">
          <div className="stat-label">Last payroll run</div>
          <div className="stat-value" style={{ fontSize: 15 }}>
            {summary.lastPayrollRun
              ? `${MONTHS[summary.lastPayrollRun.month - 1]} ${summary.lastPayrollRun.year} · ${summary.lastPayrollRun.processedCount.toLocaleString()} paid`
              : 'Never'}
          </div>
          {summary.lastPayrollRun && (
            <div className="muted" style={{ fontSize: 12 }}>
              {formatDateTime(summary.lastPayrollRun.createdAt)}
            </div>
          )}
        </div>
      </div>

      <div className="grid-2">
        <div className="card">
          <h3 style={{ marginBottom: 12 }}>Monthly spend by country</h3>
          {countries.map((c) => (
            <div key={c.country} className="bar-row">
              <span className="bar-label">{c.country}</span>
              <div className="bar-track">
                <div className="bar-fill" style={{ width: `${(c.monthlySpendUsd / maxCountrySpend) * 100}%` }} />
              </div>
              <span className="bar-value">
                {formatMoney(c.monthlySpendUsd, 'USD')} · {c.headcount.toLocaleString()}
              </span>
            </div>
          ))}
        </div>

        <div className="card">
          <h3 style={{ marginBottom: 12 }}>Salary distribution (annual USD)</h3>
          {distribution.buckets.map((b) => (
            <div key={b.bucketFloorUsd} className="bar-row">
              <span className="bar-label">
                {compactUsd(b.bucketFloorUsd)}–{compactUsd(b.bucketCeilingUsd)}
              </span>
              <div className="bar-track">
                <div className="bar-fill alt" style={{ width: `${(b.count / maxBucket) * 100}%` }} />
              </div>
              <span className="bar-value">{b.count.toLocaleString()} employees</span>
            </div>
          ))}
        </div>
      </div>

      <div className="card">
        <h3 style={{ marginBottom: 4 }}>Pay by department (annual USD)</h3>
        <p className="muted" style={{ marginTop: 0, fontSize: 12.5 }}>
          Purple = average · Green = median. A gap between them signals skew from outliers.
        </p>
        {departments.map((d) => (
          <div key={d.department} style={{ marginBottom: 10 }}>
            <div className="bar-row" style={{ padding: '3px 0' }}>
              <span className="bar-label">
                {d.department} <span className="muted">({d.headcount.toLocaleString()})</span>
              </span>
              <div className="bar-track">
                <div className="bar-fill" style={{ width: `${(d.avgAnnualUsd / maxDeptAvg) * 100}%` }} />
              </div>
              <span className="bar-value">avg {formatMoney(d.avgAnnualUsd, 'USD')}</span>
            </div>
            <div className="bar-row" style={{ padding: '3px 0' }}>
              <span className="bar-label" />
              <div className="bar-track">
                <div className="bar-fill alt" style={{ width: `${(d.medianAnnualUsd / maxDeptAvg) * 100}%` }} />
              </div>
              <span className="bar-value">median {formatMoney(d.medianAnnualUsd, 'USD')}</span>
            </div>
          </div>
        ))}
      </div>
    </div>
  )
}

const compactUsd = (v: number) => (v >= 1000 ? `$${Math.round(v / 1000)}k` : `$${v}`)
