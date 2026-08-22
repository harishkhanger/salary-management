import { useEffect, useRef, useState } from 'react'
import { get } from '../api/client'
import { humanize } from '../api/errors'
import type { PayStats } from '../api/types'
import { useToast } from './Toaster'
import { Spinner, formatMoney } from './ui'

type GroupBy = 'country' | 'department'

const compact = (v: number) => (v >= 1000 ? `$${(v / 1000).toFixed(v >= 100_000 ? 0 : 1)}k` : `$${Math.round(v)}`)

/**
 * Min / median / average / max annual pay (USD) for any set of countries or
 * for departments, drawn as a range plot: one bar per group from its lowest
 * to its highest salary on a shared axis, with markers for median and average.
 * A wide bar = wide pay spread; median far left of average = a few high earners.
 */
export default function PayStatsSection({
  countryOptions,
  departmentOptions,
}: {
  countryOptions: string[]
  departmentOptions: string[]
}) {
  const toast = useToast()
  const [groupBy, setGroupBy] = useState<GroupBy>('country')
  const [countries, setCountries] = useState<Set<string>>(new Set())
  const [department, setDepartment] = useState('')
  const [rows, setRows] = useState<PayStats[] | null>(null)
  const requestSeq = useRef(0)

  useEffect(() => {
    const seq = ++requestSeq.current
    get<PayStats[]>('/analytics/pay-stats', {
      groupBy,
      countries: countries.size ? [...countries].join(',') : undefined,
      department: department || undefined,
    })
      .then((r) => {
        if (seq === requestSeq.current) setRows(r)
      })
      .catch((e) => toast.error(humanize(e)))
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [groupBy, countries, department])

  const toggleCountry = (c: string) => {
    const next = new Set(countries)
    if (next.has(c)) next.delete(c)
    else next.add(c)
    setCountries(next)
  }

  const gmin = rows && rows.length ? Math.min(...rows.map((r) => r.minUsd)) : 0
  const gmax = rows && rows.length ? Math.max(...rows.map((r) => r.maxUsd)) : 1
  const span = Math.max(gmax - gmin, 1)
  const pct = (v: number) => `${((v - gmin) / span) * 100}%`

  const scopeText = [
    countries.size ? [...countries].join(', ') : 'all countries',
    department || 'all departments',
  ].join(' · ')

  return (
    <div className="card">
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'baseline', flexWrap: 'wrap', gap: 8 }}>
        <div>
          <h3 style={{ marginBottom: 4 }}>What people earn (annual USD)</h3>
          <p className="muted" style={{ marginTop: 0, fontSize: 12.5 }}>
            Lowest to highest salary per {groupBy}, with the median and the average — for {scopeText}
          </p>
        </div>
        <div className="band-picker">
          <button className={`band-option${groupBy === 'country' ? ' active' : ''}`} onClick={() => setGroupBy('country')}>
            By country
          </button>
          <button
            className={`band-option${groupBy === 'department' ? ' active' : ''}`}
            onClick={() => setGroupBy('department')}
          >
            By department
          </button>
        </div>
      </div>

      <div className="filters-bar" style={{ justifyContent: 'flex-start', margin: '12px 0 6px' }}>
        <div className="chips">
          {countryOptions.map((c) => (
            <label key={c} className={`chip${countries.has(c) ? ' on' : ''}`}>
              <input type="checkbox" checked={countries.has(c)} onChange={() => toggleCountry(c)} />
              {c}
            </label>
          ))}
          {countries.size > 0 && (
            <button className="btn btn-sm" onClick={() => setCountries(new Set())}>
              All countries
            </button>
          )}
        </div>
        <select className="select" value={department} onChange={(e) => setDepartment(e.target.value)}>
          <option value="">All departments</option>
          {departmentOptions.map((d) => (
            <option key={d} value={d}>
              {d}
            </option>
          ))}
        </select>
      </div>

      {!rows ? (
        <Spinner />
      ) : rows.length === 0 ? (
        <div className="table-empty">Nobody matches that selection</div>
      ) : (
        <>
          {rows.length === 1 && (
            <p className="lead" style={{ margin: '6px 0 10px' }}>
              <strong>{rows[0].label}</strong> · {rows[0].headcount.toLocaleString()} people · lowest{' '}
              <strong>{formatMoney(rows[0].minUsd)}</strong> · median <strong>{formatMoney(rows[0].medianUsd)}</strong> · average{' '}
              <strong>{formatMoney(rows[0].avgUsd)}</strong> · highest <strong>{formatMoney(rows[0].maxUsd)}</strong>
            </p>
          )}
          <div className="range-plot">
            <div className="range-row range-head">
              <span />
              <span className="range-axis">
                <span>{compact(gmin)}</span>
                <span>{compact(gmin + span / 2)}</span>
                <span>{compact(gmax)}</span>
              </span>
              <span className="range-nums head">
                <span>min</span>
                <span>median</span>
                <span>avg</span>
                <span>max</span>
              </span>
            </div>
            {rows.map((r) => (
              <div
                key={r.label}
                className="range-row"
                data-tip={`${r.label} · ${r.headcount.toLocaleString()} people — lowest ${formatMoney(r.minUsd)} · median ${formatMoney(
                  r.medianUsd,
                )} · average ${formatMoney(r.avgUsd)} · highest ${formatMoney(r.maxUsd)}`}
              >
                <span className="range-label">
                  {r.label} <span className="muted">({r.headcount.toLocaleString()})</span>
                </span>
                <span className="range-track">
                  <span className="range-span" style={{ left: pct(r.minUsd), width: pct(r.maxUsd - r.minUsd + gmin) }} />
                  <span className="range-marker median" style={{ left: pct(r.medianUsd) }} />
                  <span className="range-marker avg" style={{ left: pct(r.avgUsd) }} />
                </span>
                <span className="range-nums">
                  <span>{compact(r.minUsd)}</span>
                  <span className="median-text">{compact(r.medianUsd)}</span>
                  <span className="avg-text">{compact(r.avgUsd)}</span>
                  <span>{compact(r.maxUsd)}</span>
                </span>
              </div>
            ))}
          </div>
          <p className="muted" style={{ fontSize: 12, margin: '10px 0 0' }}>
            <span className="range-legend-span" /> lowest → highest &nbsp; <span className="range-legend median" /> median &nbsp;{' '}
            <span className="range-legend avg" /> average &nbsp;·&nbsp; a median well left of the average means a few high earners pull
            the average up
          </p>
        </>
      )}
    </div>
  )
}
