import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { get } from '../api/client'
import { humanize } from '../api/errors'
import type {
  AnalyticsSummary,
  AuditFeedItem,
  BulkRaiseRun,
  CountrySpend,
  Page,
  PayrollMonth,
  PayrollRun,
  ReviewItem,
} from '../api/types'
import { useAuth } from '../auth/AuthContext'
import { useToast } from '../components/Toaster'
import { actionTagClass, auditSentence, timeAgo } from '../components/auditText'
import { Spinner, formatDateTime } from '../components/ui'
import { MONTHS, monthLabel, plural } from './PayrollPage'
import { raiseOutcome } from './BulkRaisesPage'

const REFRESH_MS = 5000

const people = (n: number) => `${n.toLocaleString()} ${n === 1 ? 'person' : 'people'}`
const compactMoney = (v: number) => new Intl.NumberFormat('en', { notation: 'compact', maximumFractionDigits: 1 }).format(v)

function greeting(now: Date): string {
  const h = now.getHours()
  return h < 12 ? 'Good morning' : h < 18 ? 'Good afternoon' : 'Good evening'
}

type Tone = 'warn' | 'ok' | 'accent' | 'busy'
interface BriefItem {
  tone: Tone
  text: string
  detail?: string
  action?: { label: string; to: string }
}

/** Everything that needs a decision or is worth knowing, one line each, most urgent first. */
function briefing(
  months: PayrollMonth[],
  pendingReviews: number,
  runningPayroll: PayrollRun[],
  runningRaises: BulkRaiseRun[],
): BriefItem[] {
  const items: BriefItem[] = []
  runningPayroll.forEach((r) =>
    items.push({
      tone: 'busy',
      text: `Paying ${monthLabel(r.year, r.month)}`,
      detail: `${plural(r.processedCount, 'employee')} credited so far — keeps going in the background`,
      action: { label: 'Watch', to: '/payroll' },
    }),
  )
  runningRaises.forEach((r) =>
    items.push({ tone: 'busy', text: 'Applying a bulk raise', detail: raiseOutcome(r), action: { label: 'Watch', to: '/bulk-raises' } }),
  )

  const current = months[0]
  const owed = months.slice(1).filter((m) => m.state === 'DUE' || m.state === 'PARTIAL')
  const owedPeople = owed.reduce((sum, m) => sum + m.unpaidCount, 0)
  if (owed.length > 0) {
    const oldest = owed[owed.length - 1]
    items.push({
      tone: 'warn',
      text: `${people(owedPeople)} are still waiting for their salary`,
      detail:
        owed.length === 1
          ? `for ${monthLabel(oldest.year, oldest.month)} — they joined after that payday, or a hold was released`
          : `across ${owed.length} earlier months, back to ${monthLabel(oldest.year, oldest.month)}`,
      action: { label: 'Finish paying', to: '/payroll' },
    })
  }
  if (pendingReviews > 0) {
    items.push({
      tone: 'warn',
      text: `${plural(pendingReviews, 'raise')} waiting for your decision`,
      detail: 'their raise would take them past the twelve-month limit, so it needs your approval',
      action: { label: 'Review', to: '/review-queue' },
    })
  }
  if (current) {
    const month = MONTHS[current.month - 1]
    if (current.state === 'DUE') {
      items.push({ tone: 'warn', text: `${month} payroll is due`, detail: `${plural(current.unpaidCount, 'employee')} to pay`, action: { label: `Pay ${month}`, to: '/payroll' } })
    } else if (current.state === 'PARTIAL') {
      items.push({ tone: 'warn', text: `${people(current.unpaidCount)} unpaid for ${month}`, action: { label: `Pay ${month}`, to: '/payroll' } })
    } else if (current.state === 'OPENS_LATER') {
      items.push({ tone: 'ok', text: `${month} payroll opens on ${current.opensOn ? new Date(current.opensOn).toLocaleDateString(undefined, { day: 'numeric', month: 'long' }) : 'the 25th'}`, detail: 'nothing to do until then' })
    } else if (current.state === 'PAID') {
      items.push({ tone: 'ok', text: `${month} payroll is done`, detail: `${plural(current.creditedCount, 'employee')} credited` })
    }
  }
  if (pendingReviews === 0) items.push({ tone: 'ok', text: 'No raises waiting for review' })
  return items
}

/** "Today" / "Yesterday" / "Earlier" buckets for the timeline. */
function dayGroup(iso: string, now: Date): string {
  const d = new Date(iso)
  const startOfToday = new Date(now.getFullYear(), now.getMonth(), now.getDate()).getTime()
  if (d.getTime() >= startOfToday) return 'Today'
  if (d.getTime() >= startOfToday - 86_400_000) return 'Yesterday'
  return 'Earlier'
}

export default function HomePage() {
  const toast = useToast()
  const { user } = useAuth()
  const [months, setMonths] = useState<PayrollMonth[] | null>(null)
  const [pendingReviews, setPendingReviews] = useState<number | null>(null)
  const [runningRaises, setRunningRaises] = useState<BulkRaiseRun[]>([])
  const [runningPayroll, setRunningPayroll] = useState<PayrollRun[]>([])
  const [summary, setSummary] = useState<AnalyticsSummary | null>(null)
  const [countries, setCountries] = useState<CountrySpend[] | null>(null)
  const [recent, setRecent] = useState<AuditFeedItem[] | null>(null)

  useEffect(() => {
    const load = () =>
      Promise.all([
        get<PayrollMonth[]>('/payroll/months', { months: 13 }),
        get<Page<ReviewItem>>('/review-queue', { status: 'PENDING', page: 0, size: 1 }),
        get<Page<BulkRaiseRun>>('/bulk-raises', { page: 0, size: 10 }),
        get<Page<PayrollRun>>('/payroll/runs', { page: 0, size: 10 }),
        get<AnalyticsSummary>('/analytics/summary'),
        get<CountrySpend[]>('/analytics/by-country'),
        get<Page<AuditFeedItem>>('/audit', { page: 0, size: 10 }),
      ])
        .then(([m, reviews, raises, payroll, s, c, audit]) => {
          setMonths(m)
          setPendingReviews(reviews.totalElements)
          setRunningRaises(raises.content.filter((r) => r.status !== 'COMPLETED'))
          setRunningPayroll(payroll.content.filter((r) => r.status !== 'COMPLETED'))
          setSummary(s)
          setCountries(c)
          setRecent(audit.content)
        })
        .catch((e) => toast.error(humanize(e)))
    load()
    const timer = setInterval(load, REFRESH_MS)
    return () => clearInterval(timer)
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  const now = new Date()
  const loaded = months !== null && pendingReviews !== null
  const items = loaded ? briefing(months, pendingReviews, runningPayroll, runningRaises) : []
  const needsYou = items.filter((i) => i.tone === 'warn' || i.tone === 'busy').length
  const paidThrough = months?.find((m) => m.state === 'PAID') ?? null

  let lastGroup = ''

  return (
    <div className="home">
      <header className="home-head">
        <div>
          <h2 className="greeting">
            {greeting(now)}, {user?.name ?? 'there'}
          </h2>
          <div className="greeting-sub">
            {now.toLocaleDateString(undefined, { weekday: 'long', day: 'numeric', month: 'long' })}
            {loaded && (
              <>
                {' · '}
                {needsYou === 0 ? 'nothing needs you right now' : `${needsYou} ${needsYou === 1 ? 'thing needs' : 'things need'} you`}
              </>
            )}
          </div>
        </div>
        <nav className="quick-actions">
          <Link to="/analytics" className="btn btn-primary">
            Analytics
          </Link>
          <Link to="/employees" className="btn btn-primary">
            Employee directory
          </Link>
          <Link to="/employees/new" className="btn btn-primary">
            Add employee
          </Link>
        </nav>
      </header>

      <div className="home-columns">
        <section className="home-main">
          <div className="panel">
            <h3 className="panel-title">Needs you</h3>
            {!loaded ? (
              <Spinner />
            ) : (
              <ul className="brief">
                {items.map((item, i) => (
                  <li key={i} className={`brief-row tone-${item.tone}`}>
                    <span className={`brief-dot tone-${item.tone}`} />
                    <span className="brief-text">
                      <span className="brief-main">{item.text}</span>
                      {item.detail && <span className="brief-detail"> — {item.detail}</span>}
                    </span>
                    {item.action && (
                      <Link to={item.action.to} className={`brief-action${item.tone === 'warn' ? ' strong' : ''}`}>
                        {item.action.label} →
                      </Link>
                    )}
                  </li>
                ))}
              </ul>
            )}
          </div>

          <div className="panel">
            <div className="panel-head">
              <h3 className="panel-title">Recent activity</h3>
              <Link to="/audit" className="panel-link">
                Full audit feed →
              </Link>
            </div>
            {!recent ? (
              <Spinner />
            ) : recent.length === 0 ? (
              <div className="table-empty">Nothing has happened yet</div>
            ) : (
              <ul className="timeline">
                {recent.map((item) => {
                  const group = dayGroup(item.createdAt, now)
                  const showGroup = group !== lastGroup
                  lastGroup = group
                  return (
                    <li key={item.id} className={showGroup ? 'group-start' : ''}>
                      {showGroup && <div className="timeline-group">{group}</div>}
                      <span className={`timeline-dot ${actionTagClass(item.action)}`} />
                      <span className="timeline-when" title={formatDateTime(item.createdAt)}>
                        {timeAgo(item.createdAt, now)}
                      </span>
                      <span className="timeline-text">
                        {item.entityType === 'EMPLOYEE' ? (
                          <Link to={`/employees/${item.entityId}`} className="timeline-link">
                            {auditSentence(item)}
                          </Link>
                        ) : (
                          auditSentence(item)
                        )}
                      </span>
                    </li>
                  )
                })}
              </ul>
            )}
          </div>
        </section>

        <aside className="home-side">
          <div className="panel">
            <h3 className="panel-title">Organisation</h3>
            {!summary ? (
              <Spinner />
            ) : (
              <dl className="facts">
                <div>
                  <dt>People</dt>
                  <dd>{summary.headcount.toLocaleString()}</dd>
                </div>
                <div>
                  <dt>On salary hold</dt>
                  <dd>{summary.onHoldCount.toLocaleString()}</dd>
                </div>
                <div>
                  <dt>Monthly pay</dt>
                  <dd>${compactMoney(summary.totalMonthlySpendUsd)}</dd>
                </div>
                <div>
                  <dt>Countries</dt>
                  <dd>{countries ? countries.length : '…'}</dd>
                </div>
                <div>
                  <dt>Paid through</dt>
                  <dd>{paidThrough ? monthLabel(paidThrough.year, paidThrough.month) : '—'}</dd>
                </div>
              </dl>
            )}
            <div className="side-links">
              <Link to="/employees">Employee directory →</Link>
              <Link to="/analytics">Analytics →</Link>
              <Link to="/settings">Exchange rates & guardrail →</Link>
            </div>
          </div>

          {countries && countries.length > 0 && (
            <div className="panel">
              <h3 className="panel-title">Where people are</h3>
              <ul className="mini-bars">
                {[...countries]
                  .sort((a, b) => b.headcount - a.headcount)
                  .slice(0, 6)
                  .map((c) => {
                    const max = Math.max(...countries.map((x) => x.headcount), 1)
                    return (
                      <li key={c.country}>
                        <span className="mini-label">{c.country}</span>
                        <span className="mini-track">
                          <span className="mini-fill" style={{ width: `${(c.headcount / max) * 100}%` }} />
                        </span>
                        <span className="mini-value">{c.headcount.toLocaleString()}</span>
                      </li>
                    )
                  })}
              </ul>
            </div>
          )}
        </aside>
      </div>
    </div>
  )
}
