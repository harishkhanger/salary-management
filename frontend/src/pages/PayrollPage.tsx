import { useCallback, useEffect, useRef, useState } from 'react'
import { get, post } from '../api/client'
import { humanize } from '../api/errors'
import type { Page, PayrollMonth, PayrollRun } from '../api/types'
import Modal from '../components/Modal'
import { useToast } from '../components/Toaster'
import { Pagination, Spinner, formatDate, formatDateTime } from '../components/ui'

const POLL_MS = 1500
const MONTHS_SHOWN = 13

export const MONTHS = [
  'January', 'February', 'March', 'April', 'May', 'June',
  'July', 'August', 'September', 'October', 'November', 'December',
]

export const monthLabel = (year: number, month: number) => `${MONTHS[month - 1]} ${year}`
export const plural = (n: number, noun: string) => `${n.toLocaleString()} ${noun}${n === 1 ? '' : 's'}`

/** The one sentence that tells you where a month stands. Shared with the home screen. */
export function payrollSentence(m: PayrollMonth): string {
  switch (m.state) {
    case 'PAID':
      return `${plural(m.creditedCount, 'employee')} credited${m.lastPaidAt ? ` · last payment ${formatDate(m.lastPaidAt)}` : ''}${
        m.heldCount > 0 ? ` · ${m.heldCount} on hold` : ''
      }`
    case 'PARTIAL':
      return `${m.creditedCount.toLocaleString()} paid${m.lastPaidAt ? ` on ${formatDate(m.lastPaidAt)}` : ''} · ${plural(
        m.unpaidCount,
        'employee',
      )} still unpaid (joined after the run, or hold released)`
    case 'DUE':
      return `Not paid yet · ${plural(m.unpaidCount, 'employee')} to pay${
        m.heldCount > 0 ? ` (${m.heldCount} on hold will be skipped)` : ''
      }`
    case 'OPENS_LATER':
      return `Can be paid from ${m.opensOn ? formatDate(m.opensOn) : 'the 25th'}`
    case 'PROCESSING':
      return 'Paying now…'
  }
}

/** What the button will do — or null when there is nothing to do. */
export function payrollAction(m: PayrollMonth): string | null {
  if (m.state === 'PARTIAL') return `Pay ${plural(m.unpaidCount, 'unpaid employee')}`
  if (m.state === 'DUE') return `Pay ${MONTHS[m.month - 1]} · ${plural(m.unpaidCount, 'employee')}`
  return null
}

function stateTag(state: PayrollMonth['state']) {
  switch (state) {
    case 'PAID':
      return <span className="tag tag-green">Paid</span>
    case 'PARTIAL':
      return <span className="tag tag-orange">Partially paid</span>
    case 'DUE':
      return <span className="tag tag-blue">Due</span>
    case 'OPENS_LATER':
      return <span className="tag tag-gray">Not yet</span>
    case 'PROCESSING':
      return <span className="tag tag-blue">Paying…</span>
  }
}

/** "April 2026: credited 198 employees. 9,603 already paid and 4 on hold were skipped." */
export function runOutcome(run: PayrollRun): string {
  const skipped: string[] = []
  if (run.alreadyProcessedCount > 0) skipped.push(`${run.alreadyProcessedCount.toLocaleString()} already paid`)
  if (run.skippedHeldCount > 0) skipped.push(`${run.skippedHeldCount.toLocaleString()} on hold`)
  const tail = skipped.length ? ` ${skipped.join(' and ')} ${skipped.length > 1 || !skipped[0].endsWith('paid') ? 'were' : 'were'} skipped.` : ''
  if (run.processedCount === 0) return `${monthLabel(run.year, run.month)}: nothing to credit.${tail}`
  return `${monthLabel(run.year, run.month)}: credited ${plural(run.processedCount, 'employee')}.${tail}`
}

export default function PayrollPage() {
  const toast = useToast()
  const [months, setMonths] = useState<PayrollMonth[] | null>(null)
  const [confirm, setConfirm] = useState<PayrollMonth | null>(null)
  const [queueing, setQueueing] = useState(false)
  const [activeRun, setActiveRun] = useState<PayrollRun | null>(null)
  const [result, setResult] = useState<PayrollRun | null>(null)
  const pollTimer = useRef<ReturnType<typeof setInterval> | null>(null)

  const [history, setHistory] = useState<Page<PayrollRun> | null>(null)
  const [historyPage, setHistoryPage] = useState(0)

  const loadMonths = useCallback(() => {
    get<PayrollMonth[]>('/payroll/months', { months: MONTHS_SHOWN })
      .then((rows) => {
        setMonths(rows)
        // a run already in flight (e.g. after a page reload) is picked up and followed
        const inFlight = rows.find((m) => m.activeRunId)
        if (inFlight?.activeRunId) {
          setActiveRun((prev) => prev ?? ({ id: inFlight.activeRunId, status: 'QUEUED' } as PayrollRun))
        }
      })
      .catch((e) => toast.error(humanize(e)))
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  const loadHistory = useCallback(() => {
    get<Page<PayrollRun>>('/payroll/runs', { page: historyPage, size: 10 }).then(setHistory)
  }, [historyPage])

  useEffect(loadMonths, [loadMonths])
  useEffect(loadHistory, [loadHistory])

  useEffect(() => {
    if (!activeRun || activeRun.status === 'COMPLETED') return
    pollTimer.current = setInterval(() => {
      get<PayrollRun>(`/payroll/runs/${activeRun.id}`).then((run) => {
        setActiveRun(run)
        if (run.status === 'COMPLETED') {
          setResult(run)
          toast.success(runOutcome(run))
          loadMonths()
          loadHistory()
        }
      })
    }, POLL_MS)
    return () => {
      if (pollTimer.current) clearInterval(pollTimer.current)
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [activeRun?.id, activeRun?.status])

  const pay = async (m: PayrollMonth) => {
    setQueueing(true)
    try {
      const run = await post<PayrollRun>('/payroll/runs', { year: m.year, month: m.month })
      setResult(null)
      setActiveRun(run)
      setConfirm(null)
      loadMonths()
    } catch (e) {
      toast.error(humanize(e, "Couldn't start paying this month"))
    } finally {
      setQueueing(false)
    }
  }

  const now = new Date()

  return (
    <div className="stack">
      <div className="page-header">
        <div>
          <h2 className="page-title">Payroll</h2>
          <div className="page-subtitle">
            Pay the organisation month by month. Paying a month twice is safe — anyone already paid is skipped.
          </div>
        </div>
      </div>

      {result && (
        <div className="alert alert-info" style={{ display: 'flex', justifyContent: 'space-between', gap: 12 }}>
          <span>{runOutcome(result)}</span>
          <button className="btn btn-sm" onClick={() => setResult(null)}>
            Dismiss
          </button>
        </div>
      )}

      <div className="card">
        {!months ? (
          <Spinner />
        ) : (
          months.map((m) => {
            const isCurrent = m.year === now.getFullYear() && m.month === now.getMonth() + 1
            const running = activeRun && activeRun.status !== 'COMPLETED' && activeRun.year === m.year && activeRun.month === m.month
            const action = payrollAction(m)
            return (
              <div key={`${m.year}-${m.month}`} className={`month-row${isCurrent ? ' current' : ''}`}>
                <span className="month-name">{monthLabel(m.year, m.month)}</span>
                <span>{stateTag(m.state)}</span>
                <span className="month-sentence">
                  {running ? (
                    <>
                      <div className="progress-track" style={{ marginBottom: 6, maxWidth: 320 }}>
                        <div
                          className="progress-fill"
                          style={{
                            width: `${Math.min(96, 5 + (activeRun.processedCount + activeRun.alreadyProcessedCount) * 0.5)}%`,
                          }}
                        />
                      </div>
                      Paying now — {plural(activeRun.processedCount, 'employee')} credited so far
                    </>
                  ) : (
                    payrollSentence(m)
                  )}
                </span>
                <span>
                  {action && !running && (
                    <button className="btn btn-primary btn-sm month-action" onClick={() => setConfirm(m)}>
                      {action}
                    </button>
                  )}
                </span>
              </div>
            )
          })
        )}
      </div>

      <div className="card">
        <h3 style={{ marginBottom: 14 }}>What happened</h3>
        <div className="table-wrap">
          <table className="table">
            <thead>
              <tr>
                <th>When</th>
                <th>Month</th>
                <th>Outcome</th>
                <th>By</th>
              </tr>
            </thead>
            <tbody>
              {!history || history.content.length === 0 ? (
                <tr>
                  <td colSpan={4} className="table-empty">
                    No payroll has been paid yet
                  </td>
                </tr>
              ) : (
                history.content.map((run) => (
                  <tr key={run.id}>
                    <td className="muted" style={{ whiteSpace: 'nowrap' }}>
                      {formatDateTime(run.createdAt)}
                    </td>
                    <td style={{ fontWeight: 600 }}>
                      {monthLabel(run.year, run.month)}
                      {run.employeeId && <span className="muted"> · one employee</span>}
                    </td>
                    <td>
                      {run.status !== 'COMPLETED'
                        ? 'Paying now…'
                        : `${plural(run.processedCount, 'employee')} credited · ${run.skippedHeldCount} on hold · ${run.alreadyProcessedCount.toLocaleString()} already paid`}
                    </td>
                    <td>{run.initiatedBy}</td>
                  </tr>
                ))
              )}
            </tbody>
          </table>
        </div>
        {history && history.totalElements > 0 && (
          <Pagination
            page={historyPage}
            totalPages={history.totalPages}
            totalElements={history.totalElements}
            noun="payments"
            onChange={setHistoryPage}
          />
        )}
      </div>

      <Modal
        title={confirm ? `Pay ${monthLabel(confirm.year, confirm.month)}` : ''}
        open={confirm !== null}
        onClose={() => setConfirm(null)}
        footer={
          confirm && (
            <>
              <button className="btn" onClick={() => setConfirm(null)} disabled={queueing}>
                Cancel
              </button>
              <button className="btn btn-primary" onClick={() => pay(confirm)} disabled={queueing}>
                {queueing ? 'Starting…' : payrollAction(confirm)}
              </button>
            </>
          )
        }
      >
        {confirm && (
          <p className="lead">
            This will credit <strong>{plural(confirm.unpaidCount, 'employee')}</strong> for{' '}
            {monthLabel(confirm.year, confirm.month)} — one month of each person's annual salary, in their own
            currency at today's exchange rate.
            {confirm.creditedCount > 0 && (
              <>
                {' '}
                The {confirm.creditedCount.toLocaleString()} already paid will not be paid again.
              </>
            )}
            {confirm.heldCount > 0 && <> Anyone on salary hold is skipped.</>}
          </p>
        )}
      </Modal>
    </div>
  )
}
