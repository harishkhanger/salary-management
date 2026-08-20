import { useEffect, useRef, useState } from 'react'
import { get } from '../api/client'
import type { AnalyticsSummary, CountrySpend, DepartmentStats, SalaryDistribution } from '../api/types'
import { useToast } from '../components/Toaster'
import { Spinner, formatDateTime, formatMoney } from '../components/ui'

const MONTHS = ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec']

// swatch palette for donut slices + legends (pure CSS conic-gradient)
const PALETTE = [
  '#4f6df5', '#7a5cf0', '#22a06b', '#e8871e', '#d64577',
  '#18a3c4', '#8a9a17', '#b04ae0', '#e05252', '#5c6bc0',
]

/**
 * Analytics dashboard — every number is a backend SQL aggregate; charts are
 * pure CSS (conic-gradient donut, flex column chart, gradient bars).
 * Country/department filters re-query everything.
 */
export default function DashboardPage() {
  const toast = useToast()
  const [summary, setSummary] = useState<AnalyticsSummary | null>(null)
  const [countries, setCountries] = useState<CountrySpend[] | null>(null)
  const [departments, setDepartments] = useState<DepartmentStats[] | null>(null)
  const [distribution, setDistribution] = useState<SalaryDistribution | null>(null)

  // filter options come from the unfiltered lists, loaded once
  const [countryOptions, setCountryOptions] = useState<string[]>([])
  const [departmentOptions, setDepartmentOptions] = useState<string[]>([])
  const [country, setCountry] = useState('')
  const [department, setDepartment] = useState('')
  const [bucketUsd, setBucketUsd] = useState(50000)
  const requestSeq = useRef(0)

  useEffect(() => {
    Promise.all([get<CountrySpend[]>('/analytics/by-country'), get<DepartmentStats[]>('/analytics/by-department')])
      .then(([c, d]) => {
        setCountryOptions(c.map((r) => r.country).sort())
        setDepartmentOptions(d.map((r) => r.department).sort())
      })
      .catch(() => {})
  }, [])

  useEffect(() => {
    const seq = ++requestSeq.current
    const params = { country: country || undefined, department: department || undefined }
    Promise.all([
      get<AnalyticsSummary>('/analytics/summary', params),
      get<CountrySpend[]>('/analytics/by-country', params),
      get<DepartmentStats[]>('/analytics/by-department', params),
      get<SalaryDistribution>('/analytics/salary-distribution', { ...params, bucketUsd }),
    ])
      .then(([s, c, d, dist]) => {
        if (seq !== requestSeq.current) return
        setSummary(s)
        setCountries(c)
        setDepartments(d)
        setDistribution(dist)
      })
      .catch((e) => toast.error(e.message))
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [country, department, bucketUsd])

  if (!summary || !countries || !departments || !distribution) return <Spinner />

  const totalHeadcount = countries.reduce((sum, c) => sum + c.headcount, 0)
  const maxCountrySpend = Math.max(...countries.map((c) => c.monthlySpendUsd), 1)
  const maxDeptValue = Math.max(...departments.map((d) => Math.max(d.avgAnnualUsd, d.medianAnnualUsd)), 1)
  // merge the sparse long tail into one overflow bucket so a few executive
  // salaries cannot stretch the chart into dozens of near-empty columns
  const MAX_COLUMNS = 16
  const buckets = (() => {
    const all = distribution.buckets
    if (all.length <= MAX_COLUMNS) return all
    const kept = all.slice(0, MAX_COLUMNS - 1)
    const tail = all.slice(MAX_COLUMNS - 1)
    return [...kept, {
      bucketFloorUsd: tail[0].bucketFloorUsd,
      bucketCeilingUsd: tail[tail.length - 1].bucketCeilingUsd,
      count: tail.reduce((sum, b) => sum + b.count, 0),
    }]
  })()
  const maxBucket = Math.max(...buckets.map((b) => b.count), 1)

  // conic-gradient stops for the headcount donut
  let acc = 0
  const stops = countries.map((c, i) => {
    const from = (acc / totalHeadcount) * 360
    acc += c.headcount
    const to = (acc / totalHeadcount) * 360
    return `${PALETTE[i % PALETTE.length]} ${from.toFixed(2)}deg ${to.toFixed(2)}deg`
  })

  return (
    <div className="stack">
      <div className="page-header">
        <div>
          <h2 className="page-title">Dashboard</h2>
          <div className="page-subtitle">How the organization pays people — USD-normalized at current rates</div>
        </div>
        <div className="filters-bar">
          <select className="select" value={country} onChange={(e) => setCountry(e.target.value)}>
            <option value="">All countries</option>
            {countryOptions.map((c) => (
              <option key={c} value={c}>
                {c}
              </option>
            ))}
          </select>
          <select className="select" value={department} onChange={(e) => setDepartment(e.target.value)}>
            <option value="">All departments</option>
            {departmentOptions.map((d) => (
              <option key={d} value={d}>
                {d}
              </option>
            ))}
          </select>
          {(country || department) && (
            <button
              className="btn btn-sm"
              onClick={() => {
                setCountry('')
                setDepartment('')
              }}
            >
              Clear
            </button>
          )}
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
          <h3 style={{ marginBottom: 16 }}>Headcount by country</h3>
          <div className="donut-wrap">
            <div
              className="donut"
              style={{ background: `conic-gradient(${stops.join(', ')})` }}
              data-center={`${totalHeadcount.toLocaleString()}\nemployees`}
            />
            <div className="legend">
              {countries.map((c, i) => (
                <div key={c.country} className="legend-row">
                  <span className="legend-swatch" style={{ background: PALETTE[i % PALETTE.length] }} />
                  <span className="legend-label">{c.country}</span>
                  <span className="legend-value">
                    {c.headcount.toLocaleString()} · {((c.headcount / totalHeadcount) * 100).toFixed(1)}%
                  </span>
                </div>
              ))}
            </div>
          </div>
        </div>

        <div className="card">
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'baseline', flexWrap: 'wrap', gap: 8 }}>
            <div>
              <h3 style={{ marginBottom: 4 }}>Salary distribution (annual USD)</h3>
              <p className="muted" style={{ marginTop: 0, fontSize: 12.5 }}>
                Hover a column for the exact band
              </p>
            </div>
            <div className="band-picker">
              {[5000, 10000, 20000, 50000].map((b) => (
                <button
                  key={b}
                  className={`band-option${bucketUsd === b ? ' active' : ''}`}
                  onClick={() => setBucketUsd(b)}
                >
                  ${b / 1000}k
                </button>
              ))}
            </div>
          </div>
          <div className="col-chart">
            {buckets.map((b, i) => (
              <div
                key={b.bucketFloorUsd}
                className="col"
                title={`${bandLabel(b, i === buckets.length - 1 && distribution.buckets.length > buckets.length)}: ${b.count.toLocaleString()} employees`}
              >
                <span className="col-count">{b.count > maxBucket * 0.08 ? b.count.toLocaleString() : ''}</span>
                <div className="col-bar" style={{ height: `${(b.count / maxBucket) * 100}%` }} />
              </div>
            ))}
          </div>
          <div style={{ display: 'flex', gap: 6 }}>
            {buckets.map((b, i) => (
              <div key={b.bucketFloorUsd} className="col">
                <span className="col-label">
                  {i === buckets.length - 1 && distribution.buckets.length > buckets.length
                    ? `${compactUsd(b.bucketFloorUsd)}+`
                    : compactUsd(b.bucketFloorUsd)}
                </span>
              </div>
            ))}
          </div>
        </div>
      </div>

      <div className="grid-2">
        <div className="card">
          <h3 style={{ marginBottom: 12 }}>Monthly spend by country (USD)</h3>
          {countries.map((c, i) => (
            <div key={c.country} className="bar-row">
              <span className="bar-label">{c.country}</span>
              <div className="bar-track">
                <div
                  className="bar-fill"
                  style={{
                    width: `${(c.monthlySpendUsd / maxCountrySpend) * 100}%`,
                    background: PALETTE[i % PALETTE.length],
                  }}
                />
              </div>
              <span className="bar-value">{formatMoney(c.monthlySpendUsd)}</span>
            </div>
          ))}
        </div>

        <div className="card">
          <h3 style={{ marginBottom: 4 }}>Pay by department (annual USD)</h3>
          <p className="muted" style={{ marginTop: 0, fontSize: 12.5 }}>
            Purple = average · Green = median — a gap signals outlier skew
          </p>
          {departments.map((d) => (
            <div key={d.department} style={{ marginBottom: 8 }}>
              <div className="bar-row" style={{ padding: '2px 0' }}>
                <span className="bar-label">
                  {d.department} <span className="muted">({d.headcount.toLocaleString()})</span>
                </span>
                <div className="bar-track" style={{ height: 10 }}>
                  <div className="bar-fill" style={{ width: `${(d.avgAnnualUsd / maxDeptValue) * 100}%` }} />
                </div>
                <span className="bar-value">avg {formatMoney(d.avgAnnualUsd)}</span>
              </div>
              <div className="bar-row" style={{ padding: '2px 0' }}>
                <span className="bar-label" />
                <div className="bar-track" style={{ height: 10 }}>
                  <div className="bar-fill alt" style={{ width: `${(d.medianAnnualUsd / maxDeptValue) * 100}%` }} />
                </div>
                <span className="bar-value">median {formatMoney(d.medianAnnualUsd)}</span>
              </div>
            </div>
          ))}
        </div>
      </div>
    </div>
  )
}

const compactUsd = (v: number) => (v >= 1000 ? `$${Math.round(v / 1000)}k` : `$${v}`)

const bandLabel = (b: { bucketFloorUsd: number; bucketCeilingUsd: number }, overflow: boolean) =>
  overflow ? `${compactUsd(b.bucketFloorUsd)} and above` : `${compactUsd(b.bucketFloorUsd)}–${compactUsd(b.bucketCeilingUsd)}`
