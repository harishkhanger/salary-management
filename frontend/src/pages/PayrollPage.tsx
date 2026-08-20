import { useCallback, useEffect, useRef, useState } from 'react'
import { ApiError, get, post } from '../api/client'
import type { Page, PayrollRun } from '../api/types'
import { useToast } from '../components/Toaster'
import { Field, Pagination, formatDateTime } from '../components/ui'
import { RunStatusTag } from './BulkRaisesPage'

const POLL_MS = 1500
const MONTHS = [
  'January', 'February', 'March', 'April', 'May', 'June',
  'July', 'August', 'September', 'October', 'November', 'December',
]

/** Defaults to the previous month — always processable per the month rule. */
function previousMonth(): { year: number; month: number } {
  const now = new Date()
  const month = now.getMonth() === 0 ? 12 : now.getMonth()
  const year = now.getMonth() === 0 ? now.getFullYear() - 1 : now.getFullYear()
  return { year, month }
}

export default function PayrollPage() {
  const toast = useToast()
  const defaults = previousMonth()
  const [year, setYear] = useState(defaults.year)
  const [month, setMonth] = useState(defaults.month)
  const [queueing, setQueueing] = useState(false)
  const [activeRun, setActiveRun] = useState<PayrollRun | null>(null)
  const pollTimer = useRef<ReturnType<typeof setInterval> | null>(null)

  const [runs, setRuns] = useState<Page<PayrollRun> | null>(null)
  const [runsPage, setRunsPage] = useState(0)

  const loadRuns = useCallback(() => {
    get<Page<PayrollRun>>('/payroll/runs', { page: runsPage, size: 10 }).then(setRuns)
  }, [runsPage])

  useEffect(loadRuns, [loadRuns])

  useEffect(() => {
    if (!activeRun || activeRun.status === 'COMPLETED') return
    pollTimer.current = setInterval(() => {
      get<PayrollRun>(`/payroll/runs/${activeRun.id}`).then((run) => {
        setActiveRun(run)
        if (run.status === 'COMPLETED') {
          toast.success(
            `Run #${run.id} complete: ${run.processedCount} paid, ${run.skippedHeldCount} on hold, ${run.alreadyProcessedCount} already processed`,
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

  const process = async () => {
    setQueueing(true)
    try {
      const run = await post<PayrollRun>('/payroll/runs', { year, month })
      setActiveRun(run)
      loadRuns()
      toast.info(`Run #${run.id} queued — processing in the background`)
    } catch (e) {
      toast.error(e instanceof ApiError ? e.message : 'Could not queue the run')
    } finally {
      setQueueing(false)
    }
  }

  const years = Array.from({ length: 4 }, (_, i) => new Date().getFullYear() - i)

  return (
    <div className="stack">
      <div className="page-header">
        <div>
          <h2 className="page-title">Payroll</h2>
          <div className="page-subtitle">
            Process a month for the whole org — idempotent, so re-running never double-pays
          </div>
        </div>
      </div>

      <div className="card">
        <h3 style={{ marginBottom: 14 }}>Process a month</h3>
        <div className="toolbar">
          <Field label="Month">
            <select className="select" style={{ width: 150 }} value={month} onChange={(e) => setMonth(Number(e.target.value))}>
              {MONTHS.map((m, i) => (
                <option key={m} value={i + 1}>
                  {m}
                </option>
              ))}
            </select>
          </Field>
          <Field label="Year">
            <select className="select" style={{ width: 110 }} value={year} onChange={(e) => setYear(Number(e.target.value))}>
              {years.map((y) => (
                <option key={y} value={y}>
                  {y}
                </option>
              ))}
            </select>
          </Field>
          <div className="field">
            <span className="field-label">&nbsp;</span>
            <button className="btn btn-primary" onClick={process} disabled={queueing}>
              {queueing ? 'Queueing…' : 'Process payroll'}
            </button>
          </div>
        </div>
        <p className="muted" style={{ margin: 0 }}>
          Past months are always processable. The current month opens on the 25th. Held employees are skipped.
        </p>
      </div>

      {activeRun && (
        <div className="card">
          <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: 10 }}>
            <h3>
              Run #{activeRun.id} — {MONTHS[activeRun.month - 1]} {activeRun.year}{' '}
              <RunStatusTag status={activeRun.status} />
            </h3>
            {activeRun.status === 'COMPLETED' && (
              <button className="btn btn-sm" onClick={() => setActiveRun(null)}>
                Dismiss
              </button>
            )}
          </div>
          <div
            className="progress-track"
            style={{ marginBottom: 14, opacity: activeRun.status === 'QUEUED' ? 0.6 : 1 }}
          >
            <div
              className="progress-fill"
              style={{
                width:
                  activeRun.status === 'COMPLETED'
                    ? '100%'
                    : `${Math.min(96, 5 + (activeRun.processedCount + activeRun.alreadyProcessedCount) * 0.5)}%`,
              }}
            />
          </div>
          <div className="stat-row">
            <div className="stat">
              <div className="stat-label">Credits created</div>
              <div className="stat-value" style={{ color: 'var(--success)' }}>
                {activeRun.processedCount.toLocaleString()}
              </div>
            </div>
            <div className="stat">
              <div className="stat-label">Skipped (on hold)</div>
              <div className="stat-value" style={{ color: 'var(--warn)' }}>
                {activeRun.skippedHeldCount.toLocaleString()}
              </div>
            </div>
            <div className="stat">
              <div className="stat-label">Already processed</div>
              <div className="stat-value">{activeRun.alreadyProcessedCount.toLocaleString()}</div>
            </div>
          </div>
        </div>
      )}

      <div className="card">
        <h3 style={{ marginBottom: 14 }}>Run history</h3>
        <div className="table-wrap">
          <table className="table">
            <thead>
              <tr>
                <th>Run</th>
                <th>Period</th>
                <th>Scope</th>
                <th>Status</th>
                <th className="num">Paid</th>
                <th className="num">Held</th>
                <th className="num">Already done</th>
                <th>By</th>
                <th>Started</th>
              </tr>
            </thead>
            <tbody>
              {!runs || runs.content.length === 0 ? (
                <tr>
                  <td colSpan={9} className="table-empty">
                    No payroll runs yet
                  </td>
                </tr>
              ) : (
                runs.content.map((run) => (
                  <tr key={run.id}>
                    <td style={{ fontWeight: 600 }}>#{run.id}</td>
                    <td>
                      {MONTHS[run.month - 1]} {run.year}
                    </td>
                    <td className="muted">{run.employeeId ? `Employee #${run.employeeId}` : 'Whole org'}</td>
                    <td>
                      <RunStatusTag status={run.status} />
                    </td>
                    <td className="num">{run.processedCount}</td>
                    <td className="num">{run.skippedHeldCount}</td>
                    <td className="num">{run.alreadyProcessedCount}</td>
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
