import { useCallback, useEffect, useRef, useState } from 'react'
import { Link, useLocation } from 'react-router-dom'
import { get, post } from '../api/client'
import { humanize } from '../api/errors'
import type {
  BulkRaisePreview,
  BulkRaiseRun,
  CountrySpend,
  DepartmentStats,
  Employee,
  JobStatus,
  Page,
  PickedEmployee,
  RaiseType,
  Settings,
} from '../api/types'
import { useToast } from '../components/Toaster'
import { Pagination, Spinner, formatDateTime, formatMoney } from '../components/ui'

const POLL_MS = 1500
const EXCLUDE_PAGE_SIZE = 25

type Step = 1 | 2 | 3 | 4
const STEP_TITLES = ['Who', 'What', 'Review', 'Confirm']

const plural = (n: number, noun: string) => `${n.toLocaleString()} ${noun}${n === 1 ? '' : 's'}`

/** "5% raise" / "flat 25,000.00 raise" */
function raiseLabel(type: RaiseType | null, value: string | number): string {
  if (!type) return 'raise'
  return type === 'PERCENT' ? `${value}% raise` : `flat ${formatMoney(Number(value))} raise`
}

function scopeLabel(country: string, department: string, picked: PickedEmployee[] = []): string {
  if (picked.length > 0) {
    // name them: "— Aarav Al-Farsi (EMP-04659), Priya Nair (EMP-00012) and 3 more"
    const names = picked.slice(0, 3).map((p) => `${p.name} (${p.employeeCode})`).join(', ')
    return `— ${names}${picked.length > 3 ? ` and ${(picked.length - 3).toLocaleString()} more` : ''}`
  }
  const parts = [country, department].filter(Boolean)
  return parts.length ? `in ${parts.join(' · ')}` : 'across the whole organisation'
}

/** Plain-language outcome of a finished (or running) raise. */
export function raiseOutcome(run: BulkRaiseRun): string {
  if (run.status !== 'COMPLETED') return `Applying… ${plural(run.appliedCount, 'employee')} done so far`
  const bits = [`${plural(run.appliedCount, 'employee')} got the raise`]
  if (run.reviewCount > 0) bits.push(`${run.reviewCount.toLocaleString()} went to review`)
  if (run.excludedCount > 0) bits.push(`${run.excludedCount.toLocaleString()} excluded`)
  return bits.join(' · ')
}

export default function BulkRaisesPage() {
  const toast = useToast()
  const location = useLocation()
  const handedOver = (location.state as { employees?: PickedEmployee[] } | null)?.employees ?? []

  // --- wizard ---
  const [step, setStep] = useState<Step>(1)
  // who: a filter cohort, or specific people (from the directory or searched here)
  const [mode, setMode] = useState<'filters' | 'people'>(handedOver.length ? 'people' : 'filters')
  const [picked, setPicked] = useState<PickedEmployee[]>(handedOver)
  const [personSearch, setPersonSearch] = useState('')
  const [personHits, setPersonHits] = useState<Employee[]>([])
  const [country, setCountry] = useState('')
  const [department, setDepartment] = useState('')
  const [countryOptions, setCountryOptions] = useState<string[]>([])
  const [departmentOptions, setDepartmentOptions] = useState<string[]>([])
  const [headcount, setHeadcount] = useState<number | null>(null)
  const [raiseType, setRaiseType] = useState<RaiseType | null>(null)
  const [value, setValue] = useState('')
  const [threshold, setThreshold] = useState<number | null>(null)

  // --- preview & exclusions ---
  const [preview, setPreview] = useState<BulkRaisePreview | null>(null)
  const [previewing, setPreviewing] = useState(false)
  const [excluded, setExcluded] = useState<Set<number>>(new Set())
  const [excludePage, setExcludePage] = useState(0)
  const [excludeSearch, setExcludeSearch] = useState('')

  // --- execution ---
  const [submitting, setSubmitting] = useState(false)
  const [activeRun, setActiveRun] = useState<BulkRaiseRun | null>(null)
  const pollTimer = useRef<ReturnType<typeof setInterval> | null>(null)

  // --- history ---
  const [runs, setRuns] = useState<Page<BulkRaiseRun> | null>(null)
  const [runsPage, setRunsPage] = useState(0)

  useEffect(() => {
    Promise.all([
      get<CountrySpend[]>('/analytics/by-country'),
      get<DepartmentStats[]>('/analytics/by-department'),
      get<Settings>('/settings'),
    ]).then(([countries, departments, settings]) => {
      setCountryOptions(countries.map((c) => c.country).sort())
      setDepartmentOptions(departments.map((d) => d.department).sort())
      setThreshold(settings.raiseThresholdPercent)
    })
  }, [])

  // live headcount for the chosen scope (held employees included: holds block payout, not raises)
  useEffect(() => {
    if (mode === 'people') {
      setHeadcount(picked.length)
      return
    }
    setHeadcount(null)
    get<Page<Employee>>('/employees', {
      page: 0,
      size: 1,
      country: country || undefined,
      department: department || undefined,
    }).then((p) => setHeadcount(p.totalElements))
  }, [country, department, mode, picked.length])

  // people search for the hand-picked mode (debounced, top 8)
  useEffect(() => {
    if (mode !== 'people' || personSearch.trim().length < 2) {
      setPersonHits([])
      return
    }
    const t = setTimeout(() => {
      get<Page<Employee>>('/employees', { page: 0, size: 8, search: personSearch.trim() }).then((p) => setPersonHits(p.content))
    }, 250)
    return () => clearTimeout(t)
  }, [mode, personSearch])

  const addPerson = (e: Employee) => {
    if (!picked.some((p) => p.id === e.id)) setPicked([...picked, { id: e.id, name: e.name, employeeCode: e.employeeCode }])
    setPersonSearch('')
  }
  const pickedForRequest = mode === 'people' ? picked : []

  const loadRuns = useCallback(() => {
    get<Page<BulkRaiseRun>>('/bulk-raises', { page: runsPage, size: 10 }).then(setRuns)
  }, [runsPage])

  useEffect(loadRuns, [loadRuns])

  useEffect(() => {
    if (!activeRun || activeRun.status === 'COMPLETED') return
    pollTimer.current = setInterval(() => {
      get<BulkRaiseRun>(`/bulk-raises/${activeRun.id}`).then((run) => {
        setActiveRun(run)
        if (run.status === 'COMPLETED') {
          toast.success(raiseOutcome(run))
          loadRuns()
        }
      })
    }, POLL_MS)
    return () => {
      if (pollTimer.current) clearInterval(pollTimer.current)
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [activeRun?.id, activeRun?.status])

  const amount = Number(value)
  const amountError =
    value === ''
      ? null
      : !(amount > 0)
        ? 'Enter an amount greater than zero'
        : raiseType === 'PERCENT' && amount > 100
          ? 'A raise above 100% is almost certainly a typo'
          : null
  const whatValid = raiseType !== null && value !== '' && amountError === null

  const requestBody = () => ({
    raiseType,
    value: amount,
    filterCountry: mode === 'filters' && country ? country : undefined,
    filterDepartment: mode === 'filters' && department ? department : undefined,
    employeeIds: pickedForRequest.length ? pickedForRequest.map((p) => p.id) : undefined,
  })

  const goToReview = async () => {
    setPreviewing(true)
    setPreview(null)
    setExcluded(new Set())
    setExcludePage(0)
    setExcludeSearch('')
    try {
      setPreview(await post<BulkRaisePreview>('/bulk-raises/preview', requestBody()))
      setStep(3)
    } catch (e) {
      toast.error(humanize(e, "Couldn't work out the impact of this raise"))
    } finally {
      setPreviewing(false)
    }
  }

  const apply = async () => {
    setSubmitting(true)
    try {
      const run = await post<BulkRaiseRun>('/bulk-raises', { ...requestBody(), excludedEmployeeIds: [...excluded] })
      setActiveRun(run)
      loadRuns()
    } catch (e) {
      toast.error(humanize(e, "Couldn't start the raise"))
    } finally {
      setSubmitting(false)
    }
  }

  const startOver = () => {
    setActiveRun(null)
    setPreview(null)
    setExcluded(new Set())
    setRaiseType(null)
    setValue('')
    setPicked([])
    setMode('filters')
    setStep(1)
  }

  // --- exclusion list helpers ---
  const toggleExcluded = (id: number) => {
    const next = new Set(excluded)
    if (next.has(id)) next.delete(id)
    else next.add(id)
    setExcluded(next)
  }
  const excludeAll = (ids: number[]) => setExcluded(new Set([...excluded, ...ids]))
  const needle = excludeSearch.trim().toLowerCase()
  const overThresholdMatches = (preview?.overThreshold ?? []).filter(
    (r) => !needle || r.name.toLowerCase().includes(needle) || r.employeeCode.toLowerCase().includes(needle),
  )
  const excludeTotalPages = Math.max(1, Math.ceil(overThresholdMatches.length / EXCLUDE_PAGE_SIZE))
  const visibleOverThreshold = overThresholdMatches.slice(
    excludePage * EXCLUDE_PAGE_SIZE,
    (excludePage + 1) * EXCLUDE_PAGE_SIZE,
  )
  const visibleAllExcluded =
    visibleOverThreshold.length > 0 && visibleOverThreshold.every((r) => excluded.has(r.employeeId))

  const willApplyTo = preview ? preview.affectedCount - excluded.size : 0

  return (
    <div className="stack">
      <div className="page-header">
        <div>
          <h2 className="page-title">Bulk raises</h2>
          <div className="page-subtitle">Give a raise to a group of employees in four steps — nothing changes until you confirm</div>
        </div>
      </div>

      {/* ---------- running / finished ---------- */}
      {activeRun ? (
        <div className="card">
          {activeRun.status !== 'COMPLETED' ? (
            <>
              <h3 style={{ marginBottom: 10 }}>Applying the {raiseLabel(activeRun.raiseType, activeRun.raiseValue)}…</h3>
              <div className="progress-track" style={{ marginBottom: 12 }}>
                <div
                  className="progress-fill"
                  style={{
                    width: `${
                      preview && preview.affectedCount > 0
                        ? Math.min(98, ((activeRun.appliedCount + activeRun.reviewCount) / Math.max(1, willApplyTo)) * 100)
                        : 10
                    }%`,
                  }}
                />
              </div>
              <p className="lead" style={{ margin: 0 }}>
                {raiseOutcome(activeRun)}
                {preview && ` of ${willApplyTo.toLocaleString()}`}. You can leave this page — it keeps going in the
                background.
              </p>
            </>
          ) : (
            <>
              <h3 style={{ marginBottom: 10 }}>Done</h3>
              <p className="lead">
                <strong>{plural(activeRun.appliedCount, 'employee')}</strong> got the{' '}
                {raiseLabel(activeRun.raiseType, activeRun.raiseValue)}.
                {activeRun.reviewCount > 0 && (
                  <>
                    {' '}
                    <strong>{activeRun.reviewCount.toLocaleString()}</strong> went to review because the raise would push
                    them past the {threshold ?? 30}% twelve-month guardrail —{' '}
                    <Link to="/review-queue">review them now</Link>.
                  </>
                )}
                {activeRun.excludedCount > 0 && <> {activeRun.excludedCount.toLocaleString()} were excluded as you asked.</>}
              </p>
              <div className="card-actions">
                <button className="btn btn-primary" onClick={startOver}>
                  Start another raise
                </button>
                {activeRun.reviewCount > 0 && (
                  <Link to="/review-queue" className="btn">
                    Go to review queue
                  </Link>
                )}
              </div>
            </>
          )}
        </div>
      ) : (
        <div className="card">
          <div className="steps">
            {STEP_TITLES.map((title, i) => {
              const n = (i + 1) as Step
              return (
                <span key={title} className={`step${n === step ? ' active' : n < step ? ' done' : ''}`}>
                  <span className="step-num">{n < step ? '✓' : n}</span>
                  {title}
                </span>
              )
            })}
          </div>

          {/* ---------- step 1: who ---------- */}
          {step === 1 && (
            <>
              <h3 style={{ marginBottom: 6 }}>Who gets the raise?</h3>
              <div className="choice-grid" style={{ marginBottom: 14 }}>
                <button type="button" className={`choice${mode === 'filters' ? ' selected' : ''}`} onClick={() => setMode('filters')}>
                  <div className="choice-title">A group</div>
                  <div className="choice-desc">Everyone, or a country and/or a department</div>
                </button>
                <button type="button" className={`choice${mode === 'people' ? ' selected' : ''}`} onClick={() => setMode('people')}>
                  <div className="choice-title">Specific people</div>
                  <div className="choice-desc">One employee or a hand-picked list — search here, or tick them in the directory</div>
                </button>
              </div>
              {mode === 'filters' ? (
                <>
                  <p className="muted" style={{ marginTop: 0 }}>
                    Leave both blank for everyone. Employees on salary hold are included — a hold stops payout, not pay changes.
                  </p>
                  <div className="toolbar" style={{ marginBottom: 16 }}>
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
                  </div>
                </>
              ) : (
                <div style={{ marginBottom: 16 }}>
                  <div style={{ position: 'relative', maxWidth: 360 }}>
                    <input
                      className="input"
                      placeholder="Search by name or code to add someone…"
                      value={personSearch}
                      onChange={(e) => setPersonSearch(e.target.value)}
                      autoFocus
                    />
                    {personHits.length > 0 && (
                      <ul className="suggest">
                        {personHits.map((e) => (
                          <li key={e.id}>
                            <button type="button" onClick={() => addPerson(e)} disabled={picked.some((p) => p.id === e.id)}>
                              <strong>{e.name}</strong> <span className="muted">{e.employeeCode} · {e.department} · {e.country}</span>
                            </button>
                          </li>
                        ))}
                      </ul>
                    )}
                  </div>
                  <div className="chips" style={{ marginTop: 10 }}>
                    {picked.map((p) => (
                      <span key={p.id} className="chip on">
                        {p.name} <span className="muted">{p.employeeCode}</span>
                        <button type="button" className="chip-x" aria-label={`Remove ${p.name}`} onClick={() => setPicked(picked.filter((x) => x.id !== p.id))}>
                          ×
                        </button>
                      </span>
                    ))}
                    {picked.length === 0 && <span className="muted">Nobody picked yet.</span>}
                  </div>
                  <p className="muted" style={{ fontSize: 12.5 }}>
                    Tip: in the <Link to="/employees">employee directory</Link> you can tick people across pages and press “Give a raise”.
                  </p>
                </div>
              )}
              <p className="lead">
                {headcount === null ? (
                  'Counting…'
                ) : (
                  <>
                    This raise will apply to <strong>{plural(headcount, 'employee')}</strong> {scopeLabel(country, department, pickedForRequest)}.
                  </>
                )}
              </p>
              <div className="card-actions">
                <button className="btn btn-primary" disabled={!headcount} onClick={() => setStep(2)}>
                  Next: what raise →
                </button>
              </div>
            </>
          )}

          {/* ---------- step 2: what ---------- */}
          {step === 2 && (
            <>
              <h3 style={{ marginBottom: 12 }}>What raise?</h3>
              <div className="choice-grid" style={{ marginBottom: 16 }}>
                <button
                  type="button"
                  className={`choice${raiseType === 'PERCENT' ? ' selected' : ''}`}
                  onClick={() => setRaiseType('PERCENT')}
                >
                  <div className="choice-title">Percentage of current salary</div>
                  <div className="choice-desc">Everyone's pay grows by the same share — e.g. 5% for all</div>
                </button>
                <button
                  type="button"
                  className={`choice${raiseType === 'AMOUNT' ? ' selected' : ''}`}
                  onClick={() => setRaiseType('AMOUNT')}
                >
                  <div className="choice-title">Flat amount</div>
                  <div className="choice-desc">The same figure added to each annual salary, in each person's own currency</div>
                </button>
              </div>
              {raiseType && (
                <label className="field" style={{ maxWidth: 260 }}>
                  <span className="field-label">{raiseType === 'PERCENT' ? 'Percent' : 'Amount added to annual salary'}</span>
                  <input
                    className="input"
                    type="number"
                    min="0.01"
                    step="0.01"
                    autoFocus
                    value={value}
                    onChange={(e) => setValue(e.target.value)}
                    placeholder={raiseType === 'PERCENT' ? 'e.g. 5' : 'e.g. 25000'}
                  />
                  {amountError && <span className="field-error">{amountError}</span>}
                </label>
              )}
              {whatValid && (
                <p className="lead" style={{ marginTop: 12 }}>
                  A <strong>{raiseLabel(raiseType, value)}</strong> for {plural(headcount ?? 0, 'employee')}{' '}
                  {scopeLabel(country, department, pickedForRequest)}.
                </p>
              )}
              <div className="card-actions">
                <button className="btn" onClick={() => setStep(1)}>
                  ← Back
                </button>
                <button className="btn btn-primary" disabled={!whatValid || previewing} onClick={goToReview}>
                  {previewing ? 'Working out the impact…' : 'Next: review impact →'}
                </button>
              </div>
            </>
          )}

          {/* ---------- step 3: review ---------- */}
          {step === 3 && preview && (
            <>
              <h3 style={{ marginBottom: 6 }}>Review the impact</h3>
              <p className="lead">
                A <strong>{raiseLabel(raiseType, value)}</strong> for <strong>{plural(preview.affectedCount, 'employee')}</strong>{' '}
                {scopeLabel(country, department, pickedForRequest)} adds about <strong>${formatMoney(preview.costImpactUsdDelta)}</strong> a
                year to payroll.
              </p>
              <div className="stat-row" style={{ marginBottom: 14 }}>
                <div className="stat">
                  <div className="stat-label">Annual payroll now (USD)</div>
                  <div className="stat-value">${formatMoney(preview.costImpactUsdCurrent)}</div>
                </div>
                <div className="stat">
                  <div className="stat-label">This raise adds</div>
                  <div className="stat-value" style={{ color: 'var(--success)' }}>
                    +${formatMoney(preview.costImpactUsdDelta)}
                  </div>
                </div>
                <div className="stat">
                  <div className="stat-label">Annual payroll after (USD)</div>
                  <div className="stat-value">${formatMoney(preview.costImpactUsdProposed)}</div>
                </div>
              </div>
              <div className="table-wrap">
                <table className="table">
                  <thead>
                    <tr>
                      <th>Currency</th>
                      <th className="num">Annual payroll now</th>
                      <th className="num">After the raise</th>
                      <th className="num">Increase</th>
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

              {preview.overThreshold.length > 0 && (
                <div style={{ marginTop: 16 }}>
                  <div className="alert alert-warn">
                    {preview.overThreshold.length.toLocaleString()} of these employees have already had raises totalling more
                    than {threshold ?? 30}% since they joined. Tick anyone who should sit this one out — or leave them in.
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
                            ? setExcluded(
                                new Set([...excluded].filter((id) => !visibleOverThreshold.some((r) => r.employeeId === id))),
                              )
                            : excludeAll(visibleOverThreshold.map((r) => r.employeeId))
                        }
                      />
                      <span className="muted">This page</span>
                    </label>
                    <button className="btn btn-sm" onClick={() => excludeAll(overThresholdMatches.map((r) => r.employeeId))}>
                      Exclude all {overThresholdMatches.length.toLocaleString()}
                      {needle ? ' matching' : ''}
                    </button>
                    {excluded.size > 0 && (
                      <button className="btn btn-sm" onClick={() => setExcluded(new Set())}>
                        Clear ({excluded.size.toLocaleString()} excluded)
                      </button>
                    )}
                  </div>
                  {visibleOverThreshold.length === 0 ? (
                    <div className="table-empty">Nobody on this list matches “{excludeSearch}”</div>
                  ) : (
                    visibleOverThreshold.map((r) => (
                      <label key={r.employeeId} className="check-row">
                        <input type="checkbox" checked={excluded.has(r.employeeId)} onChange={() => toggleExcluded(r.employeeId)} />
                        <strong>{r.name}</strong>
                        <span className="muted">
                          {r.employeeCode} · raises so far +{r.totalRaisePercent}% · last one {formatDateTime(r.lastRaiseAt)}
                        </span>
                      </label>
                    ))
                  )}
                  <Pagination
                    page={excludePage}
                    totalPages={excludeTotalPages}
                    totalElements={overThresholdMatches.length}
                    noun="flagged"
                    onChange={setExcludePage}
                  />
                </div>
              )}

              <div className="card-actions">
                <button className="btn" onClick={() => setStep(2)}>
                  ← Back
                </button>
                <button className="btn btn-primary" onClick={() => setStep(4)}>
                  Next: confirm →
                </button>
              </div>
            </>
          )}

          {/* ---------- step 4: confirm ---------- */}
          {step === 4 && preview && (
            <>
              <h3 style={{ marginBottom: 6 }}>Confirm</h3>
              <p className="lead">
                Apply a <strong>{raiseLabel(raiseType, value)}</strong> to <strong>{plural(willApplyTo, 'employee')}</strong>{' '}
                {scopeLabel(country, department, pickedForRequest)}
                {excluded.size > 0 && <> ({excluded.size.toLocaleString()} excluded)</>}, adding about $
                {formatMoney(preview.costImpactUsdDelta)} a year — this group's annual payroll goes from $
                {formatMoney(preview.costImpactUsdCurrent)} to <strong>${formatMoney(preview.costImpactUsdProposed)}</strong>.
              </p>
              <p className="muted">
                Anyone whose total raises in the last twelve months would go past {threshold ?? 30}% is not changed — their
                raise waits for you in the review queue instead. Every change is recorded in the audit feed.
              </p>
              <div className="card-actions">
                <button className="btn" onClick={() => setStep(3)} disabled={submitting}>
                  ← Back
                </button>
                <button className="btn btn-primary" onClick={apply} disabled={submitting}>
                  {submitting ? 'Starting…' : `Apply raise to ${plural(willApplyTo, 'employee')}`}
                </button>
              </div>
            </>
          )}

          {previewing && step !== 2 && <Spinner />}
        </div>
      )}

      {/* ---------- history ---------- */}
      <div className="card">
        <h3 style={{ marginBottom: 14 }}>Past raises</h3>
        <div className="table-wrap">
          <table className="table">
            <thead>
              <tr>
                <th>When</th>
                <th>Raise</th>
                <th>Who</th>
                <th>Outcome</th>
                <th>By</th>
              </tr>
            </thead>
            <tbody>
              {!runs || runs.content.length === 0 ? (
                <tr>
                  <td colSpan={5} className="table-empty">
                    No raises given yet
                  </td>
                </tr>
              ) : (
                runs.content.map((run) => (
                  <tr key={run.id}>
                    <td className="muted" style={{ whiteSpace: 'nowrap' }}>
                      {formatDateTime(run.createdAt)}
                    </td>
                    <td style={{ fontWeight: 600 }}>
                      {run.raiseType === 'PERCENT' ? `${run.raiseValue}%` : `+${formatMoney(run.raiseValue)}`}
                    </td>
                    <td className="muted">
                      {run.selectedCount > 0
                        ? `${run.selectedCount.toLocaleString()} hand-picked ${run.selectedCount === 1 ? 'person' : 'people'}`
                        : [run.filterCountry, run.filterDepartment].filter(Boolean).join(' · ') || 'Whole organisation'}
                    </td>
                    <td>{raiseOutcome(run)}</td>
                    <td>{run.initiatedBy}</td>
                  </tr>
                ))
              )}
            </tbody>
          </table>
        </div>
        {runs && runs.totalElements > 0 && (
          <Pagination page={runsPage} totalPages={runs.totalPages} totalElements={runs.totalElements} noun="raises" onChange={setRunsPage} />
        )}
      </div>
    </div>
  )
}

export function RunStatusTag({ status }: { status: JobStatus }) {
  const cls = status === 'COMPLETED' ? 'tag-green' : status === 'RUNNING' ? 'tag-blue' : 'tag-gray'
  return <span className={`tag ${cls}`}>{status}</span>
}
