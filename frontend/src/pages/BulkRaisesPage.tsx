import { useCallback, useEffect, useRef, useState } from 'react'
import { ApiError, get, post } from '../api/client'
import type { BulkRaisePreview, BulkRaiseRun, JobStatus, Page, RaiseType } from '../api/types'
import { useToast } from '../components/Toaster'
import { Field, Pagination, formatDateTime, formatMoney } from '../components/ui'

const POLL_MS = 1500

export default function BulkRaisesPage() {
  const toast = useToast()

  // --- raise definition ---
  const [raiseType, setRaiseType] = useState<RaiseType>('PERCENT')
  const [value, setValue] = useState('')
  const [filterCountry, setFilterCountry] = useState('')
  const [filterDepartment, setFilterDepartment] = useState('')

  // --- preview & execution state ---
  const [preview, setPreview] = useState<BulkRaisePreview | null>(null)
  const [excluded, setExcluded] = useState<Set<number>>(new Set())
  // the recently-raised list can run to thousands: paged + searchable client-side
  const [excludePage, setExcludePage] = useState(0)
  const [excludeSearch, setExcludeSearch] = useState('')
  const [previewing, setPreviewing] = useState(false)
  const [activeRun, setActiveRun] = useState<BulkRaiseRun | null>(null)
  const pollTimer = useRef<ReturnType<typeof setInterval> | null>(null)

  // --- run history ---
  const [runs, setRuns] = useState<Page<BulkRaiseRun> | null>(null)
  const [runsPage, setRunsPage] = useState(0)

  const loadRuns = useCallback(() => {
    get<Page<BulkRaiseRun>>('/bulk-raises', { page: runsPage, size: 10 }).then(setRuns)
  }, [runsPage])

  useEffect(loadRuns, [loadRuns])

  // poll the active run until it completes
  useEffect(() => {
    if (!activeRun || activeRun.status === 'COMPLETED') return
    pollTimer.current = setInterval(() => {
      get<BulkRaiseRun>(`/bulk-raises/${activeRun.id}`).then((run) => {
        setActiveRun(run)
        if (run.status === 'COMPLETED') {
          toast.success(
            `Run #${run.id} complete: ${run.appliedCount} applied, ${run.reviewCount} parked for review`,
          )
          loadRuns()
        }
      })
    }, POLL_MS)
    return () => {
      if (pollTimer.current) clearInterval(pollTimer.current)
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [activeRun?.id, activeRun?.status])

  const requestBody = () => ({
    raiseType,
    value: Number(value),
    filterCountry: filterCountry || undefined,
    filterDepartment: filterDepartment || undefined,
  })

  const runPreview = async () => {
    setPreviewing(true)
    setPreview(null)
    setExcluded(new Set())
    setExcludePage(0)
    setExcludeSearch('')
    try {
      setPreview(await post<BulkRaisePreview>('/bulk-raises/preview', requestBody()))
    } catch (e) {
      toast.error(e instanceof ApiError ? e.message : 'Preview failed')
    } finally {
      setPreviewing(false)
    }
  }

  const execute = async () => {
    try {
      const run = await post<BulkRaiseRun>('/bulk-raises', {
        ...requestBody(),
        excludedEmployeeIds: [...excluded],
      })
      setPreview(null)
      setActiveRun(run)
      loadRuns()
      toast.info(`Run #${run.id} queued — processing in the background`)
    } catch (e) {
      toast.error(e instanceof ApiError ? e.message : 'Execution failed')
    }
  }

  const toggleExcluded = (id: number) => {
    const next = new Set(excluded)
    if (next.has(id)) next.delete(id)
    else next.add(id)
    setExcluded(next)
  }

  const excludeAll = (ids: number[]) => setExcluded(new Set([...excluded, ...ids]))
  const clearExcluded = () => setExcluded(new Set())

  const EXCLUDE_PAGE_SIZE = 25
  const needle = excludeSearch.trim().toLowerCase()
  const recentlyRaisedMatches = (preview?.recentlyRaised ?? []).filter(
    (r) => !needle || r.name.toLowerCase().includes(needle) || r.employeeCode.toLowerCase().includes(needle),
  )
  const excludeTotalPages = Math.max(1, Math.ceil(recentlyRaisedMatches.length / EXCLUDE_PAGE_SIZE))
  const visibleRecentlyRaised = recentlyRaisedMatches.slice(
    excludePage * EXCLUDE_PAGE_SIZE,
    (excludePage + 1) * EXCLUDE_PAGE_SIZE,
  )
  const visibleAllExcluded =
    visibleRecentlyRaised.length > 0 && visibleRecentlyRaised.every((r) => excluded.has(r.employeeId))

  const progressPct = (run: BulkRaiseRun) => {
    if (run.status === 'COMPLETED') return 100
    if (run.status === 'QUEUED') return 3
    const done = run.appliedCount + run.reviewCount + run.excludedCount
    // affected count is unknown mid-run; show movement against the preview when we have it
    return Math.min(96, 8 + done * 0.5)
  }

  return (
    <div className="stack">
      <div className="page-header">
        <div>
          <h2 className="page-title">Bulk raises</h2>
          <div className="page-subtitle">Preview the impact, exclude recently-raised employees, then execute</div>
        </div>
      </div>

      {/* step 1: define + preview */}
      <div className="card">
        <h3 style={{ marginBottom: 14 }}>Define the raise</h3>
        <div className="toolbar">
          <Field label="Type">
            <select
              className="select"
              style={{ width: 170 }}
              value={raiseType}
              onChange={(e) => setRaiseType(e.target.value as RaiseType)}
            >
              <option value="PERCENT">Percent raise</option>
              <option value="AMOUNT">Flat amount</option>
            </select>
          </Field>
          <Field label={raiseType === 'PERCENT' ? 'Percent' : 'Amount (local currency)'}>
            <input
              className="input"
              style={{ width: 140 }}
              type="number"
              min="0.01"
              step="0.01"
              value={value}
              onChange={(e) => setValue(e.target.value)}
              placeholder={raiseType === 'PERCENT' ? 'e.g. 5' : 'e.g. 25000'}
            />
          </Field>
          <Field label="Country (optional)">
            <input
              className="input"
              style={{ width: 150 }}
              value={filterCountry}
              onChange={(e) => setFilterCountry(e.target.value)}
              placeholder="All countries"
            />
          </Field>
          <Field label="Department (optional)">
            <input
              className="input"
              style={{ width: 160 }}
              value={filterDepartment}
              onChange={(e) => setFilterDepartment(e.target.value)}
              placeholder="All departments"
            />
          </Field>
          <div className="field" style={{ justifyContent: 'flex-end' }}>
            <span className="field-label">&nbsp;</span>
            <button className="btn btn-primary" onClick={runPreview} disabled={previewing || !value}>
              {previewing ? 'Previewing…' : 'Preview impact'}
            </button>
          </div>
        </div>
      </div>

      {/* step 2: preview results */}
      {preview && (
        <div className="card">
          <h3 style={{ marginBottom: 14 }}>Preview</h3>
          <div className="stat-row" style={{ marginBottom: 16 }}>
            <div className="stat">
              <div className="stat-label">Employees affected</div>
              <div className="stat-value">{preview.affectedCount.toLocaleString()}</div>
            </div>
            <div className="stat">
              <div className="stat-label">Cost impact (USD / year)</div>
              <div className="stat-value">+{formatMoney(preview.costImpactUsdDelta)}</div>
            </div>
            <div className="stat">
              <div className="stat-label">Recently raised</div>
              <div className="stat-value">{preview.recentlyRaised.length}</div>
            </div>
          </div>

          <div className="table-wrap">
            <table className="table">
              <thead>
                <tr>
                  <th>Currency</th>
                  <th className="num">Current total</th>
                  <th className="num">Proposed total</th>
                  <th className="num">Delta</th>
                </tr>
              </thead>
              <tbody>
                {preview.costImpact.map((c) => (
                  <tr key={c.currencyCode}>
                    <td style={{ fontWeight: 600 }}>{c.currencyCode}</td>
                    <td className="num">{formatMoney(c.current)}</td>
                    <td className="num">{formatMoney(c.proposed)}</td>
                    <td className="num" style={{ color: 'var(--success)' }}>
                      +{formatMoney(c.delta)}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>

          {preview.recentlyRaised.length > 0 && (
            <div style={{ marginTop: 16 }}>
              <div className="alert alert-warn">
                {preview.recentlyRaised.length.toLocaleString()} of these employees already received a raise
                recently — tick to exclude them from this run.
              </div>
              <div className="filters-bar" style={{ marginTop: 10, justifyContent: 'flex-start' }}>
                <input
                  className="input"
                  placeholder="Search name or code"
                  value={excludeSearch}
                  onChange={(e) => {
                    setExcludeSearch(e.target.value)
                    setExcludePage(0)
                  }}
                  style={{ width: 220 }}
                />
                <label className="check-row" style={{ padding: 0, border: 0 }}>
                  <input
                    type="checkbox"
                    checked={visibleAllExcluded}
                    onChange={() =>
                      visibleAllExcluded
                        ? setExcluded(new Set([...excluded].filter((id) => !visibleRecentlyRaised.some((r) => r.employeeId === id))))
                        : excludeAll(visibleRecentlyRaised.map((r) => r.employeeId))
                    }
                  />
                  <span className="muted">This page</span>
                </label>
                <button
                  className="btn btn-sm"
                  onClick={() => excludeAll(recentlyRaisedMatches.map((r) => r.employeeId))}
                >
                  Exclude all {recentlyRaisedMatches.length.toLocaleString()}
                  {needle ? ' matching' : ''}
                </button>
                {excluded.size > 0 && (
                  <button className="btn btn-sm" onClick={clearExcluded}>
                    Clear ({excluded.size.toLocaleString()} excluded)
                  </button>
                )}
              </div>
              {visibleRecentlyRaised.length === 0 ? (
                <div className="table-empty">No recently-raised employees match “{excludeSearch}”</div>
              ) : (
                visibleRecentlyRaised.map((r) => (
                  <label key={r.employeeId} className="check-row">
                    <input
                      type="checkbox"
                      checked={excluded.has(r.employeeId)}
                      onChange={() => toggleExcluded(r.employeeId)}
                    />
                    <strong>{r.name}</strong>
                    <span className="muted">
                      {r.employeeCode} · last raise {formatDateTime(r.lastRaiseAt)}
                    </span>
                  </label>
                ))
              )}
              <Pagination
                page={excludePage}
                totalPages={excludeTotalPages}
                totalElements={recentlyRaisedMatches.length}
                noun="recently raised"
                onChange={setExcludePage}
              />
            </div>
          )}

          <div style={{ marginTop: 18, display: 'flex', gap: 10 }}>
            <button className="btn btn-primary" onClick={execute}>
              Execute raise{excluded.size > 0 ? ` (excluding ${excluded.size.toLocaleString()})` : ''}
            </button>
            <button className="btn" onClick={() => setPreview(null)}>
              Discard
            </button>
          </div>
        </div>
      )}

      {/* active run progress */}
      {activeRun && (
        <div className="card">
          <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: 10 }}>
            <h3>
              Run #{activeRun.id} <RunStatusTag status={activeRun.status} />
            </h3>
            {activeRun.status === 'COMPLETED' && (
              <button className="btn btn-sm" onClick={() => setActiveRun(null)}>
                Dismiss
              </button>
            )}
          </div>
          <div className="progress-track" style={{ marginBottom: 14 }}>
            <div className="progress-fill" style={{ width: `${progressPct(activeRun)}%` }} />
          </div>
          <div className="stat-row">
            <div className="stat">
              <div className="stat-label">Applied</div>
              <div className="stat-value" style={{ color: 'var(--success)' }}>
                {activeRun.appliedCount.toLocaleString()}
              </div>
            </div>
            <div className="stat">
              <div className="stat-label">Parked for review</div>
              <div className="stat-value" style={{ color: 'var(--warn)' }}>
                {activeRun.reviewCount.toLocaleString()}
              </div>
            </div>
            <div className="stat">
              <div className="stat-label">Excluded</div>
              <div className="stat-value">{activeRun.excludedCount.toLocaleString()}</div>
            </div>
          </div>
        </div>
      )}

      {/* run history */}
      <div className="card">
        <h3 style={{ marginBottom: 14 }}>Past runs</h3>
        <div className="table-wrap">
          <table className="table">
            <thead>
              <tr>
                <th>Run</th>
                <th>Raise</th>
                <th>Scope</th>
                <th>Status</th>
                <th className="num">Applied</th>
                <th className="num">Review</th>
                <th className="num">Excluded</th>
                <th>By</th>
                <th>Started</th>
              </tr>
            </thead>
            <tbody>
              {!runs || runs.content.length === 0 ? (
                <tr>
                  <td colSpan={9} className="table-empty">
                    No bulk raises yet
                  </td>
                </tr>
              ) : (
                runs.content.map((run) => (
                  <tr key={run.id}>
                    <td style={{ fontWeight: 600 }}>#{run.id}</td>
                    <td>
                      {run.raiseType === 'PERCENT'
                        ? `+${run.raiseValue}%`
                        : `+${formatMoney(run.raiseValue)}`}
                    </td>
                    <td className="muted">
                      {[run.filterCountry, run.filterDepartment].filter(Boolean).join(' · ') || 'Whole org'}
                    </td>
                    <td>
                      <RunStatusTag status={run.status} />
                    </td>
                    <td className="num">{run.appliedCount}</td>
                    <td className="num">{run.reviewCount}</td>
                    <td className="num">{run.excludedCount}</td>
                    <td>{run.initiatedBy}</td>
                    <td className="muted">{formatDateTime(run.createdAt)}</td>
                  </tr>
                ))
              )}
            </tbody>
          </table>
        </div>
        {runs && (
          <Pagination
            page={runsPage}
            totalPages={runs.totalPages}
            totalElements={runs.totalElements}
            noun="runs"
            onChange={setRunsPage}
          />
        )}
      </div>
    </div>
  )
}

export function RunStatusTag({ status }: { status: JobStatus }) {
  const cls = status === 'COMPLETED' ? 'tag-green' : status === 'RUNNING' ? 'tag-blue' : 'tag-gray'
  return <span className={`tag ${cls}`}>{status}</span>
}
