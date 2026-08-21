import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { get } from '../api/client'
import { humanize } from '../api/errors'
import type { AnalyticsSummary, AuditFeedItem, BulkRaiseRun, Page, PayrollMonth, PayrollRun, ReviewItem } from '../api/types'
import { useToast } from '../components/Toaster'
import { actionTagClass, describeAudit } from '../components/auditText'
import { Spinner, formatDateTime, formatMoney } from '../components/ui'
import { monthLabel, payrollAction, payrollSentence, plural } from './PayrollPage'
import { raiseOutcome } from './BulkRaisesPage'

/**
 * The home screen answers "what needs me, what is happening, what just
 * happened" without the user reconstructing it from the audit feed.
 */
export default function HomePage() {
  const toast = useToast()
  const [months, setMonths] = useState<PayrollMonth[] | null>(null)
  const [pendingReviews, setPendingReviews] = useState<number | null>(null)
  const [runningRaises, setRunningRaises] = useState<BulkRaiseRun[]>([])
  const [runningPayroll, setRunningPayroll] = useState<PayrollRun[]>([])
  const [summary, setSummary] = useState<AnalyticsSummary | null>(null)
  const [recent, setRecent] = useState<AuditFeedItem[] | null>(null)

  useEffect(() => {
    const load = () =>
      Promise.all([
        get<PayrollMonth[]>('/payroll/months', { months: 6 }),
        get<Page<ReviewItem>>('/review-queue', { status: 'PENDING', page: 0, size: 1 }),
        get<Page<BulkRaiseRun>>('/bulk-raises', { page: 0, size: 10 }),
        get<Page<PayrollRun>>('/payroll/runs', { page: 0, size: 10 }),
        get<AnalyticsSummary>('/analytics/summary'),
        get<Page<AuditFeedItem>>('/audit', { page: 0, size: 6 }),
      ])
        .then(([m, reviews, raises, payroll, s, audit]) => {
          setMonths(m)
          setPendingReviews(reviews.totalElements)
          setRunningRaises(raises.content.filter((r) => r.status !== 'COMPLETED'))
          setRunningPayroll(payroll.content.filter((r) => r.status !== 'COMPLETED'))
          setSummary(s)
          setRecent(audit.content)
        })
        .catch((e) => toast.error(humanize(e)))
    load()
    // anything in flight finishes within seconds: keep the screen honest while it is open
    const timer = setInterval(load, 5000)
    return () => clearInterval(timer)
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  const current = months?.[0]
  const needsAttention = months?.filter((m, i) => i > 0 && (m.state === 'DUE' || m.state === 'PARTIAL')) ?? []
  const busy = runningRaises.length + runningPayroll.length

  return (
    <div className="stack">
      <div className="page-header">
        <div>
          <h2 className="page-title">Home</h2>
          <div className="page-subtitle">What needs you, what is running, and what just happened</div>
        </div>
      </div>

      <div className="home-grid">
        {/* payroll */}
        <div className="card">
          <div className="stat-label">Payroll</div>
          {!current ? (
            <Spinner />
          ) : (
            <>
              <div className="home-big">{monthLabel(current.year, current.month)}</div>
              <p className="muted" style={{ margin: 0 }}>
                {payrollSentence(current)}
              </p>
              {needsAttention.map((m) => (
                <p key={`${m.year}-${m.month}`} style={{ margin: '8px 0 0', color: 'var(--warn)', fontSize: 13 }}>
                  {monthLabel(m.year, m.month)}: {payrollSentence(m)}
                </p>
              ))}
              <div className="card-actions">
                <Link to="/payroll" className={`btn${payrollAction(current) || needsAttention.length ? ' btn-primary' : ''}`}>
                  {payrollAction(current) ?? (needsAttention.length ? 'Finish paying earlier months' : 'Open payroll')}
                </Link>
              </div>
            </>
          )}
        </div>

        {/* review queue */}
        <div className="card">
          <div className="stat-label">Raises awaiting your review</div>
          {pendingReviews === null ? (
            <Spinner />
          ) : (
            <>
              <div className="home-big" style={{ color: pendingReviews > 0 ? 'var(--warn)' : undefined }}>
                {pendingReviews.toLocaleString()}
              </div>
              <p className="muted" style={{ margin: 0 }}>
                {pendingReviews === 0
                  ? 'Nothing waiting — every raise has been decided.'
                  : `${plural(pendingReviews, 'proposed raise')} went past the twelve-month guardrail and need a yes or no from you.`}
              </p>
              <div className="card-actions">
                <Link to="/review-queue" className={`btn${pendingReviews > 0 ? ' btn-primary' : ''}`}>
                  {pendingReviews > 0 ? 'Review them' : 'Open review queue'}
                </Link>
              </div>
            </>
          )}
        </div>

        {/* in progress */}
        <div className="card">
          <div className="stat-label">Running now</div>
          <div className="home-big">{busy === 0 ? 'Nothing' : plural(busy, 'job')}</div>
          {busy === 0 ? (
            <p className="muted" style={{ margin: 0 }}>
              No raise or payroll is being applied at the moment.
            </p>
          ) : (
            <ul className="home-list">
              {runningPayroll.map((r) => (
                <li key={`p${r.id}`}>
                  <span className="tag tag-blue">Payroll</span>
                  <span>
                    Paying {monthLabel(r.year, r.month)} — {plural(r.processedCount, 'employee')} credited so far
                  </span>
                </li>
              ))}
              {runningRaises.map((r) => (
                <li key={`r${r.id}`}>
                  <span className="tag tag-blue">Raise</span>
                  <span>{raiseOutcome(r)}</span>
                </li>
              ))}
            </ul>
          )}
        </div>

        {/* people */}
        <div className="card">
          <div className="stat-label">People</div>
          {!summary ? (
            <Spinner />
          ) : (
            <>
              <div className="home-big">{summary.headcount.toLocaleString()} employees</div>
              <p className="muted" style={{ margin: 0 }}>
                {summary.onHoldCount.toLocaleString()} on salary hold · about ${formatMoney(summary.totalMonthlySpendUsd)} a month
                in pay
              </p>
              <div className="card-actions">
                <Link to="/employees" className="btn">
                  Employees
                </Link>
                <Link to="/analytics" className="btn">
                  Analytics
                </Link>
              </div>
            </>
          )}
        </div>
      </div>

      {/* recent activity */}
      <div className="card">
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'baseline', marginBottom: 8 }}>
          <h3>What just happened</h3>
          <Link to="/audit" style={{ fontSize: 13 }}>
            Full audit feed →
          </Link>
        </div>
        {!recent ? (
          <Spinner />
        ) : recent.length === 0 ? (
          <div className="table-empty">No activity yet</div>
        ) : (
          <ul className="home-list">
            {recent.map((item) => (
              <li key={item.id}>
                <span className="muted" style={{ whiteSpace: 'nowrap', minWidth: 150 }}>
                  {formatDateTime(item.createdAt)}
                </span>
                <span className={`tag ${actionTagClass(item.action)}`}>{item.action.replaceAll('_', ' ')}</span>
                <span style={{ flex: 1 }}>
                  {item.kind === 'RUN'
                    ? item.entityType === 'PAYROLL_RUN'
                      ? `Payroll paid for ${item.runSummary?.month}/${item.runSummary?.year} — ${item.runSummary?.processedCount} credited`
                      : `Bulk raise applied — ${item.runSummary?.appliedCount} changed, ${item.runSummary?.reviewCount} to review`
                    : describeAudit(item)}
                </span>
                <span className="muted">{item.actor}</span>
              </li>
            ))}
          </ul>
        )}
      </div>
    </div>
  )
}
