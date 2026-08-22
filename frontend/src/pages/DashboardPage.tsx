import { useEffect, useRef, useState } from 'react'
import { get } from '../api/client'
import { humanize } from '../api/errors'
import type { AnalyticsSummary, CountrySpend, DepartmentStats, SalaryDistribution } from '../api/types'
import { useToast } from '../components/Toaster'
import PayStatsSection from '../components/PayStatsSection'
import { Spinner, formatDateTime, formatMoney } from '../components/ui'

const MONTHS = ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec']

// swatch palette for donut slices + legends (pure CSS conic-gradient)
// Tailwind 600-weight hues: distinct without being loud
const PALETTE = [
  '#2563eb', '#059669', '#f59e0b', '#e11d48', '#7c3aed',
  '#0891b2', '#65a30d', '#c026d3', '#ea580c', '#4f46e5',
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
  // custom range ("who earns between 40k and 42k?"): drafts in the inputs, applied = what the chart shows
  const [rangeMin, setRangeMin] = useState('')
  const [rangeMax, setRangeMax] = useState('')
  const [rangeBand, setRangeBand] = useState('')
  const [range, setRange] = useState<{ min: number; max: number; band?: number } | null>(null)
  const requestSeq = useRef(0)

  const rangeDraftError =
    rangeMin === '' || rangeMax === ''
      ? null
      : Number(rangeMax) <= Number(rangeMin)
        ? 'Maximum must be greater than minimum'
        : rangeBand !== '' && Number(rangeBand) < 100
          ? 'Band width must be at least 100'
          : null
  const rangeDraftValid = rangeMin !== '' && rangeMax !== '' && rangeDraftError === null

  const applyRange = () => {
    if (!rangeDraftValid) return
    setRange({ min: Number(rangeMin), max: Number(rangeMax), band: rangeBand === '' ? undefined : Number(rangeBand) })
  }
  const clearRange = () => {
    setRange(null)
    setRangeMin('')
    setRangeMax('')
    setRangeBand('')
  }

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
      get<SalaryDistribution>(
        '/analytics/salary-distribution',
        range ? { ...params, minUsd: range.min, maxUsd: range.max, bucketUsd: range.band } : { ...params, bucketUsd },
      ),
    ])
      .then(([s, c, d, dist]) => {
        if (seq !== requestSeq.current) return
        setSummary(s)
        setCountries(c)
        setDepartments(d)
        setDistribution(dist)
      })
      .catch((e) => toast.error(humanize(e)))
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [country, department, bucketUsd, range])

  if (!summary || !countries || !departments || !distribution) return <Spinner />

  const maxCountrySpend = Math.max(...countries.map((c) => c.monthlySpendUsd), 1)

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
            <h3 style={{ marginBottom: 4 }}>
              {range
                ? `${distribution.total.toLocaleString()} employees earn between $${range.min.toLocaleString()} and $${range.max.toLocaleString()} a year`
                : 'Salary distribution (annual USD)'}
              {range && (country || department) && (
                <span className="muted" style={{ fontWeight: 400 }}>
                  {' '}
                  in {[country, department].filter(Boolean).join(' · ')}
                </span>
              )}
            </h3>
            <p className="muted" style={{ marginTop: 0, fontSize: 12.5 }}>
              {range
                ? `Bands of $${distribution.bucketUsd.toLocaleString()} — hover for the exact range`
                : 'Each column is one pay band — hover for the exact range'}
            </p>
          </div>
          {!range && (
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
          )}
        </div>
        <div className="filters-bar" style={{ justifyContent: 'flex-start', margin: '10px 0 4px' }}>
          <span className="muted" style={{ fontSize: 12.5 }}>
            Custom range (USD / year):
          </span>
          <input
            className="input"
            type="number"
            min="0"
            step="1000"
            placeholder="From, e.g. 40000"
            value={rangeMin}
            onChange={(e) => setRangeMin(e.target.value)}
            onKeyDown={(e) => e.key === 'Enter' && applyRange()}
            style={{ width: 150 }}
            aria-label="Minimum salary"
          />
          <input
            className="input"
            type="number"
            min="0"
            step="1000"
            placeholder="To, e.g. 42000"
            value={rangeMax}
            onChange={(e) => setRangeMax(e.target.value)}
            onKeyDown={(e) => e.key === 'Enter' && applyRange()}
            style={{ width: 150 }}
            aria-label="Maximum salary"
          />
          <input
            className="input"
            type="number"
            min="100"
            step="100"
            placeholder="Band width (auto)"
            title="Leave blank for about ten bands; set it to the size of the range for a single bar"
            value={rangeBand}
            onChange={(e) => setRangeBand(e.target.value)}
            onKeyDown={(e) => e.key === 'Enter' && applyRange()}
            style={{ width: 150 }}
            aria-label="Band width"
          />
          <button className="btn btn-primary btn-sm" disabled={!rangeDraftValid} onClick={applyRange}>
            Show
          </button>
          <button
            className="btn btn-sm"
            disabled={rangeMin === '' || rangeMax === '' || Number(rangeMax) <= Number(rangeMin)}
            title="One bar: just the count for the whole range"
            onClick={() => {
              const width = String(Number(rangeMax) - Number(rangeMin))
              setRangeBand(width)
              setRange({ min: Number(rangeMin), max: Number(rangeMax), band: Number(width) })
            }}
          >
            One bar
          </button>
          {range && (
            <button className="btn btn-sm" onClick={clearRange}>
              Back to full distribution
            </button>
          )}
          {rangeDraftError && <span className="field-error">{rangeDraftError}</span>}
        </div>
        <div className="col-chart-scroll">
          <div className="col-chart-inner" style={{ width: Math.max(buckets.length * 58, 600) }}>
            <div className="col-chart" style={{ height: 320 }}>
              {buckets.map((b) => (
                <div
                  key={b.bucketFloorUsd}
                  className="col"
                  data-tip={`${bandLabel(b, distribution.bucketUsd)}: ${b.count.toLocaleString()} employees`}
                >
                  <span className="col-count">{b.count.toLocaleString()}</span>
                  <div className="col-bar" style={{ height: `${(b.count / maxBucket) * 100}%` }} />
                </div>
              ))}
            </div>
            <div className="col-label-row" style={{ display: 'flex', gap: 6 }}>
              {buckets.map((b) => (
                <div key={b.bucketFloorUsd} className="col">
                  <span className="col-label">{bandLabel(b, distribution.bucketUsd)}</span>
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

      <PayStatsSection countryOptions={countryOptions} departmentOptions={departmentOptions} />
    </div>
  )
}

/**
 * Reusable donut with legend. Each slice is an SVG annular sector, so it is a
 * real element: hover (or hover the legend row) highlights it and the centre
 * names it with its value and share. No chart library.
 */
function Donut({
  data,
  centerLabel,
  valueLabel,
}: {
  data: { label: string; value: number }[]
  centerLabel: (total: number) => string
  valueLabel: (value: number, pct: number) => string
}) {
  const [hovered, setHovered] = useState<number | null>(null)
  const total = data.reduce((sum, d) => sum + d.value, 0) || 1
  let acc = 0
  const slices = data.map((d, i) => {
    const from = acc / total
    acc += d.value
    const to = acc / total
    return { ...d, i, path: annularSector(from, to), pct: (d.value / total) * 100 }
  })
  const active = hovered === null ? null : slices[hovered]
  const [line1, line2] = centerLabel(total).split('\n')

  return (
    <div className="donut-wrap">
      <div className={`donut${hovered !== null ? ' has-hover' : ''}`} onMouseLeave={() => setHovered(null)}>
        <svg viewBox="0 0 100 100" role="img" aria-label={active ? `${active.label}: ${valueLabel(active.value, active.pct)}` : line1}>
          {slices.map((sl) => (
            <path
              key={sl.label}
              d={sl.path}
              fill={PALETTE[sl.i % PALETTE.length]}
              className={`donut-slice${hovered === sl.i ? ' active' : ''}`}
              onMouseEnter={() => setHovered(sl.i)}
            >
              <title>{`${sl.label}: ${valueLabel(sl.value, sl.pct)}`}</title>
            </path>
          ))}
        </svg>
        <div className="donut-center">
          {active ? (
            <>
              <span>{active.label}</span>
              <span className="donut-center-sub">{valueLabel(active.value, active.pct)}</span>
            </>
          ) : (
            <>
              <span>{line1}</span>
              {line2 && <span className="donut-center-sub">{line2}</span>}
            </>
          )}
        </div>
      </div>
      <div className="legend">
        {slices.map((sl) => (
          <div
            key={sl.label}
            className={`legend-row${hovered === sl.i ? ' active' : ''}`}
            title={valueLabel(sl.value, sl.pct)}
            onMouseEnter={() => setHovered(sl.i)}
            onMouseLeave={() => setHovered(null)}
          >
            <span className="legend-swatch" style={{ background: PALETTE[sl.i % PALETTE.length] }} />
            <span className="legend-label">{sl.label}</span>
            <span className="legend-value">{sl.pct.toFixed(1)}%</span>
          </div>
        ))}
      </div>
    </div>
  )
}

/**
 * SVG path for the ring segment between two fractions of a turn (0..1),
 * clockwise from 12 o'clock, outer radius 50, inner radius 30 on a 100x100
 * box. A full circle is drawn as two halves — a single 360° arc collapses.
 */
function annularSector(from: number, to: number): string {
  const R = 50
  const r = 30
  const c = 50
  if (to - from >= 0.9999) {
    return `M${c} ${c - R} A${R} ${R} 0 1 1 ${c} ${c + R} A${R} ${R} 0 1 1 ${c} ${c - R} Z M${c} ${c - r} A${r} ${r} 0 1 0 ${c} ${c + r} A${r} ${r} 0 1 0 ${c} ${c - r} Z`
  }
  const point = (radius: number, t: number) => {
    const a = t * 2 * Math.PI - Math.PI / 2
    return `${(c + radius * Math.cos(a)).toFixed(3)} ${(c + radius * Math.sin(a)).toFixed(3)}`
  }
  const large = to - from > 0.5 ? 1 : 0
  return `M${point(R, from)} A${R} ${R} 0 ${large} 1 ${point(R, to)} L${point(r, to)} A${r} ${r} 0 ${large} 0 ${point(r, from)} Z`
}

const compactUsd = (v: number) => (v >= 1000 ? `$${Math.round(v / 1000)}k` : `$${v}`)

const compactMoney = (v: number) =>
  new Intl.NumberFormat('en', { notation: 'compact', maximumFractionDigits: 1 }).format(v)

/**
 * Range labels ("$0–50k") so a band floor never reads as a salary of $0.
 * Narrow bands (custom ranges) get exact figures: "$40,000–40,500", never a rounded "$41k".
 */
const bandLabel = (b: { bucketFloorUsd: number; bucketCeilingUsd: number }, bucketUsd: number) =>
  bucketUsd < 5000
    ? `$${b.bucketFloorUsd.toLocaleString()}–${b.bucketCeilingUsd.toLocaleString()}`
    : `${compactUsd(b.bucketFloorUsd)}–${compactUsd(b.bucketCeilingUsd).slice(1)}`
