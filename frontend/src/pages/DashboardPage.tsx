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
 * pure CSS (conic-gradient donuts, flex column chart, gradient bars).
 * Country/department filters re-query everything. Chart cards resize via the
 * native CSS resize handle (drag the bottom-right corner).
 */
export default function DashboardPage() {
  const toast = useToast()
  const [summary, setSummary] = useState<AnalyticsSummary | null>(null)
  const [countries, setCountries] = useState<CountrySpend[] | null>(null)
  const [departments, setDepartments] = useState<DepartmentStats[] | null>(null)
  const [distribution, setDistribution] = useState<SalaryDistribution | null>(null)

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

  const maxCountrySpend = Math.max(...countries.map((c) => c.monthlySpendUsd), 1)
  const maxDeptValue = Math.max(...departments.map((d) => Math.max(d.avgAnnualUsd, d.medianAnnualUsd)), 1)

  const buckets = distribution.buckets
  const maxBucket = Math.max(...buckets.map((b) => b.count), 1)

  // donut #2: monthly spend share by department (avg annual x headcount / 12)
  const deptSpend = departments.map((d) => ({
    label: d.department,
    value: Math.round((d.avgAnnualUsd * d.headcount) / 12),
  }))

  // derived: average annual pay per employee by country
  const avgPayByCountry = countries
    .map((c) => ({ label: c.country, value: (c.monthlySpendUsd * 12) / Math.max(c.headcount, 1) }))
    .sort((a, b) => b.value - a.value)
  const maxAvgPay = Math.max(...avgPayByCountry.map((c) => c.value), 1)
  const maxDeptHeadcount = Math.max(...departments.map((d) => d.headcount), 1)

  return (
    <div className="stack">
      <div className="page-header">
        <div>
          <h2 className="page-title">Dashboard</h2>
          <div className="page-subtitle">
            How the organization pays people — USD-normalized at current rates · drag a card's corner to resize
          </div>
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

      {/* top row: two resizable donuts */}
      <div className="chart-row">
        <div className="card resizable-both">
          <h3 style={{ marginBottom: 16 }}>Headcount by country</h3>
          <div className="donut-scroll">
            <Donut
              data={countries.map((c) => ({ label: c.country, value: c.headcount }))}
              centerLabel={(total) => `${total.toLocaleString()}\nemployees`}
              valueLabel={(v, pct) => `${v.toLocaleString()} · ${pct.toFixed(1)}%`}
            />
          </div>
        </div>
        <div className="card resizable-both">
          <h3 style={{ marginBottom: 16 }}>Monthly spend share by department</h3>
          <div className="donut-scroll">
            <Donut
              data={deptSpend}
              centerLabel={(total) => `${compactMoney(total)}\nUSD / month`}
              valueLabel={(v, pct) => `${formatMoney(v)} · ${pct.toFixed(1)}%`}
            />
          </div>
        </div>
      </div>

      {/* middle: full-width salary distribution */}
      <div className="card">
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'baseline', flexWrap: 'wrap', gap: 8 }}>
          <div>
            <h3 style={{ marginBottom: 4 }}>Salary distribution (annual USD)</h3>
            <p className="muted" style={{ marginTop: 0, fontSize: 12.5 }}>
              Each column is one pay band — hover for the exact range
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
        <div className="col-chart-scroll">
          <div className="col-chart-inner" style={{ width: Math.max(buckets.length * 58, 600) }}>
            <div className="col-chart" style={{ height: 320 }}>
              {buckets.map((b) => (
                <div
                  key={b.bucketFloorUsd}
                  className="col"
                  data-tip={`${bandLabel(b)}: ${b.count.toLocaleString()} employees`}
                >
                  <span className="col-count">{b.count.toLocaleString()}</span>
                  <div className="col-bar" style={{ height: `${(b.count / maxBucket) * 100}%` }} />
                </div>
              ))}
            </div>
            <div className="col-label-row" style={{ display: 'flex', gap: 6 }}>
              {buckets.map((b) => (
                <div key={b.bucketFloorUsd} className="col">
                  <span className="col-label">{bandLabel(b)}</span>
                </div>
              ))}
            </div>
          </div>
        </div>
      </div>

      {/* bottom rows: four resizable breakdowns */}
      <div className="chart-row">
        {!country && (
        <div className="card resizable">
          <h3 style={{ marginBottom: 12 }}>Average annual pay by country (USD)</h3>
          {avgPayByCountry.map((c, i) => (
            <div key={c.label} className="bar-row">
              <span className="bar-label">{c.label}</span>
              <div className="bar-track">
                <div
                  className="bar-fill"
                  style={{ width: `${(c.value / maxAvgPay) * 100}%`, background: PALETTE[i % PALETTE.length] }}
                />
              </div>
              <span className="bar-value">{formatMoney(c.value)}</span>
            </div>
          ))}
        </div>
        )}
        <div className="card resizable">
          <h3 style={{ marginBottom: 12 }}>Headcount by department</h3>
          {[...departments].sort((a, b) => b.headcount - a.headcount).map((d) => (
            <div key={d.department} className="bar-row">
              <span className="bar-label">{d.department}</span>
              <div className="bar-track">
                <div className="bar-fill alt" style={{ width: `${(d.headcount / maxDeptHeadcount) * 100}%` }} />
              </div>
              <span className="bar-value">{d.headcount.toLocaleString()}</span>
            </div>
          ))}
        </div>
      </div>
      <div className="chart-row">
        <div className="card resizable">
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

        <div className="card resizable">
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

/** Reusable pure-CSS donut with legend; slices via conic-gradient. */
function Donut({
  data,
  centerLabel,
  valueLabel,
}: {
  data: { label: string; value: number }[]
  centerLabel: (total: number) => string
  valueLabel: (value: number, pct: number) => string
}) {
  const total = data.reduce((sum, d) => sum + d.value, 0) || 1
  let acc = 0
  const stops = data.map((d, i) => {
    const from = (acc / total) * 360
    acc += d.value
    const to = (acc / total) * 360
    return `${PALETTE[i % PALETTE.length]} ${from.toFixed(2)}deg ${to.toFixed(2)}deg`
  })
  return (
    <div className="donut-wrap">
      <div
        className="donut"
        style={{ background: `conic-gradient(${stops.join(', ')})` }}
        data-center={centerLabel(total)}
      />
      <div className="legend">
        {data.map((d, i) => (
          <div key={d.label} className="legend-row" title={valueLabel(d.value, (d.value / total) * 100)}>
            <span className="legend-swatch" style={{ background: PALETTE[i % PALETTE.length] }} />
            <span className="legend-label">{d.label}</span>
            <span className="legend-value">{((d.value / total) * 100).toFixed(1)}%</span>
          </div>
        ))}
      </div>
    </div>
  )
}

const compactUsd = (v: number) => (v >= 1000 ? `$${Math.round(v / 1000)}k` : `$${v}`)

const compactMoney = (v: number) =>
  new Intl.NumberFormat('en', { notation: 'compact', maximumFractionDigits: 1 }).format(v)

/** Range labels ("$0–50k") so a band floor never reads as a salary of $0. */
const bandLabel = (b: { bucketFloorUsd: number; bucketCeilingUsd: number }) =>
  `${compactUsd(b.bucketFloorUsd)}–${compactUsd(b.bucketCeilingUsd).slice(1)}`
