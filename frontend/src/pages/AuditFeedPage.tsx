import { useCallback, useEffect, useRef, useState } from 'react'
import { Link } from 'react-router-dom'
import { get } from '../api/client'
import type { AuditFeed, AuditFeedItem } from '../api/types'
import { useToast } from '../components/Toaster'
import { Spinner, formatDateTime } from '../components/ui'

/**
 * Global audit feed: keyset-paginated; bulk runs appear as ONE collapsed
 * header row (approach b — headers are audit rows) that expands inline to
 * its item rows via the same endpoint filtered by runId + runType.
 */
const ACTIONS = [
  'CREATED',
  'PROFILE_UPDATED',
  'STATUS_CHANGED',
  'DELETED',
  'SALARY_CHANGED',
  'RAISE_PARKED',
  'RAISE_APPROVED',
  'RAISE_REJECTED',
  'SALARY_CREDITED',
  'RUN_COMPLETED',
  'RATE_UPDATED',
  'THRESHOLD_UPDATED',
]

const ENTITY_TYPES = ['EMPLOYEE', 'PAYROLL_RUN', 'BULK_RAISE_RUN', 'CURRENCY', 'SETTINGS']

export default function AuditFeedPage() {
  const toast = useToast()
  const [items, setItems] = useState<AuditFeedItem[]>([])
  const [cursor, setCursor] = useState<string | null>(null)
  const [loading, setLoading] = useState(false)
  const [initialised, setInitialised] = useState(false)
  const [action, setAction] = useState('')
  const [entityType, setEntityType] = useState('')
  const [actor, setActor] = useState('')
  const [from, setFrom] = useState('')
  const [to, setTo] = useState('')
  const requestSeq = useRef(0)

  const hasFilters = Boolean(action || entityType || actor || from || to)

  const loadPage = useCallback(
    (after: string | null) => {
      const seq = ++requestSeq.current
      setLoading(true)
      get<AuditFeed>('/audit', {
        limit: 25,
        cursor: after ?? undefined,
        action: action || undefined,
        entityType: entityType || undefined,
        actor: actor || undefined,
        from: from || undefined,
        to: to || undefined,
      })
        .then((feed) => {
          if (seq !== requestSeq.current) return
          setItems((prev) => (after ? [...prev, ...feed.items] : feed.items))
          setCursor(feed.nextCursor)
          setInitialised(true)
        })
        .catch((e) => {
          if (seq === requestSeq.current) toast.error(e.message)
        })
        .finally(() => {
          if (seq === requestSeq.current) setLoading(false)
        })
    },
    // eslint-disable-next-line react-hooks/exhaustive-deps
    [action, entityType, actor, from, to],
  )

  useEffect(() => loadPage(null), [loadPage])

  return (
    <div>
      <div className="page-header">
        <div>
          <h2 className="page-title">Audit feed</h2>
          <div className="page-subtitle">
            Every change in the system, newest first — bulk runs collapse into a single row
          </div>
        </div>
        <div className="filters-bar">
          <select className="select" value={action} onChange={(e) => setAction(e.target.value)}>
            <option value="">All actions</option>
            {ACTIONS.map((a) => (
              <option key={a} value={a}>
                {a.replaceAll('_', ' ')}
              </option>
            ))}
          </select>
          <select className="select" value={entityType} onChange={(e) => setEntityType(e.target.value)}>
            <option value="">All entities</option>
            {ENTITY_TYPES.map((t) => (
              <option key={t} value={t}>
                {t.replaceAll('_', ' ')}
              </option>
            ))}
          </select>
          <select className="select" value={actor} onChange={(e) => setActor(e.target.value)}>
            <option value="">All actors</option>
            <option value="hr">hr</option>
            <option value="system">system</option>
          </select>
          <input
            className="input"
            type="date"
            value={from}
            max={to || undefined}
            onChange={(e) => setFrom(e.target.value)}
            title="From date"
          />
          <input
            className="input"
            type="date"
            value={to}
            min={from || undefined}
            onChange={(e) => setTo(e.target.value)}
            title="To date"
          />
          {hasFilters && (
            <button
              className="btn btn-sm"
              onClick={() => {
                setAction('')
                setEntityType('')
                setActor('')
                setFrom('')
                setTo('')
              }}
            >
              Clear
            </button>
          )}
        </div>
      </div>

      <div className="card">
        {!initialised ? (
          <Spinner />
        ) : items.length === 0 ? (
          <div className="table-empty">{hasFilters ? 'No entries match these filters' : 'No activity yet'}</div>
        ) : (
          items.map((item) =>
            item.kind === 'RUN' ? <RunRow key={`run-${item.id}`} item={item} /> : <EntryRow key={item.id} item={item} />,
          )
        )}
        {cursor && (
          <div style={{ textAlign: 'center', marginTop: 14 }}>
            <button className="btn" disabled={loading} onClick={() => loadPage(cursor)}>
              {loading ? 'Loading…' : 'Load older entries'}
            </button>
          </div>
        )}
      </div>
    </div>
  )
}

function EntryRow({ item, compact }: { item: AuditFeedItem; compact?: boolean }) {
  return (
    <div className="feed-item">
      <span className="feed-when">{formatDateTime(item.createdAt)}</span>
      <span className={`tag ${actionTagClass(item.action)}`}>{item.action.replaceAll('_', ' ')}</span>
      <span style={{ flex: 1 }}>
        {describe(item)}
        {!compact && item.entityType === 'EMPLOYEE' && (
          <>
            {' '}
            <Link to={`/employees/${item.entityId}`} style={{ fontSize: 12.5 }}>
              view employee
            </Link>
          </>
        )}
      </span>
      <span className="muted" style={{ fontSize: 12.5 }}>
        {item.actor}
      </span>
    </div>
  )
}

function RunRow({ item }: { item: AuditFeedItem }) {
  const [open, setOpen] = useState(false)
  const [runItems, setRunItems] = useState<AuditFeedItem[] | null>(null)
  const [runCursor, setRunCursor] = useState<string | null>(null)
  const [loading, setLoading] = useState(false)

  const runType = item.entityType === 'PAYROLL_RUN' ? 'PAYROLL' : 'BULK_RAISE'

  const loadItems = (after: string | null) => {
    setLoading(true)
    get<AuditFeed>('/audit', { runId: item.runId, runType, limit: 20, cursor: after ?? undefined })
      .then((feed) => {
        setRunItems((prev) => (after && prev ? [...prev, ...feed.items] : feed.items))
        setRunCursor(feed.nextCursor)
      })
      .finally(() => setLoading(false))
  }

  const toggle = () => {
    const next = !open
    setOpen(next)
    if (next && runItems === null) loadItems(null)
  }

  const summary = item.runSummary ?? {}
  const counts =
    runType === 'PAYROLL'
      ? `${summary.processedCount ?? '?'} paid · ${summary.skippedHeldCount ?? 0} held · ${summary.alreadyProcessedCount ?? 0} already processed`
      : `${summary.appliedCount ?? '?'} applied · ${summary.reviewCount ?? 0} parked · ${summary.excludedCount ?? 0} excluded`

  const title =
    runType === 'PAYROLL'
      ? `Payroll run #${item.runId} — ${summary.month ?? '?'}/${summary.year ?? '?'}`
      : `Bulk raise #${item.runId} — ${summary.raiseType === 'PERCENT' ? `+${summary.raiseValue}%` : `+${summary.raiseValue}`}`

  return (
    <div className="feed-run">
      <div className="feed-run-head" onClick={toggle}>
        <span className={`chevron${open ? ' open' : ''}`}>▶</span>
        <span className="feed-when">{formatDateTime(item.createdAt)}</span>
        <span className="feed-run-title">{title}</span>
        <span className="feed-run-counts">{counts}</span>
        <span className="muted" style={{ marginLeft: 'auto', fontSize: 12.5 }}>
          {item.actor}
        </span>
      </div>
      {open && (
        <div className="feed-run-items">
          {runItems === null ? (
            <Spinner />
          ) : runItems.length === 0 ? (
            <div className="muted" style={{ padding: '8px 0' }}>
              No item rows for this run
            </div>
          ) : (
            runItems.map((ri) => <EntryRow key={ri.id} item={ri} compact />)
          )}
          {runCursor && (
            <div style={{ textAlign: 'center', marginTop: 8 }}>
              <button className="btn btn-sm" disabled={loading} onClick={() => loadItems(runCursor)}>
                {loading ? 'Loading…' : 'Load more items'}
              </button>
            </div>
          )}
        </div>
      )}
    </div>
  )
}

function actionTagClass(action: string): string {
  if (action === 'RAISE_PARKED' || action === 'STATUS_CHANGED') return 'tag-orange'
  if (action === 'DELETED' || action === 'RAISE_REJECTED') return 'tag-red'
  if (action === 'SALARY_CHANGED' || action === 'RAISE_APPROVED' || action === 'SALARY_CREDITED') return 'tag-green'
  if (action === 'CREATED') return 'tag-blue'
  return 'tag-gray'
}

function describe(item: AuditFeedItem): string {
  const subject = item.entityType === 'EMPLOYEE' ? `Employee #${item.entityId}` : item.entityType.replaceAll('_', ' ')
  if (item.changedFields) {
    const fields = Object.entries(item.changedFields)
      .map(([field, change]) => {
        if (change && typeof change === 'object' && 'old' in (change as object)) {
          const c = change as { old: unknown; new: unknown }
          return `${field}: ${c.old} → ${c.new}`
        }
        return `${field}: ${String(change)}`
      })
      .join(' · ')
    return `${subject} — ${fields}`
  }
  if (item.refTable && item.refId) {
    return `${subject} — ${item.refTable.replaceAll('_', ' ')} #${item.refId}`
  }
  return subject
}
