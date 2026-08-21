import { useEffect, useRef, useState } from 'react'
import { Link } from 'react-router-dom'
import { get } from '../api/client'
import type { AuditFeedItem, Page } from '../api/types'
import { useToast } from '../components/Toaster'
import { Pagination, Spinner, formatDateTime } from '../components/ui'

/**
 * Global audit feed: numbered pages like every other list; bulk runs appear
 * as ONE collapsed header row (approach b — headers are audit rows) that
 * expands inline to its own paged item rows via the same endpoint filtered
 * by runId + runType.
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
  const [data, setData] = useState<Page<AuditFeedItem> | null>(null)
  const [page, setPage] = useState(0)
  const [action, setAction] = useState('')
  const [entityType, setEntityType] = useState('')
  const [actor, setActor] = useState('')
  const [from, setFrom] = useState('')
  const [to, setTo] = useState('')
  const requestSeq = useRef(0)

  const hasFilters = Boolean(action || entityType || actor || from || to)

  // any filter change restarts from the first page
  const setFilter = (setter: (v: string) => void) => (value: string) => {
    setter(value)
    setPage(0)
  }

  useEffect(() => {
    const seq = ++requestSeq.current
    get<Page<AuditFeedItem>>('/audit', {
      page,
      size: 25,
      action: action || undefined,
      entityType: entityType || undefined,
      actor: actor || undefined,
      from: from || undefined,
      to: to || undefined,
    })
      .then((result) => {
        if (seq === requestSeq.current) setData(result)
      })
      .catch((e) => {
        if (seq === requestSeq.current) toast.error(e.message)
      })
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [page, action, entityType, actor, from, to])

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
          <select className="select" value={action} onChange={(e) => setFilter(setAction)(e.target.value)}>
            <option value="">All actions</option>
            {ACTIONS.map((a) => (
              <option key={a} value={a}>
                {a.replaceAll('_', ' ')}
              </option>
            ))}
          </select>
          <select className="select" value={entityType} onChange={(e) => setFilter(setEntityType)(e.target.value)}>
            <option value="">All entities</option>
            {ENTITY_TYPES.map((t) => (
              <option key={t} value={t}>
                {t.replaceAll('_', ' ')}
              </option>
            ))}
          </select>
          <select className="select" value={actor} onChange={(e) => setFilter(setActor)(e.target.value)}>
            <option value="">All actors</option>
            <option value="hr">hr</option>
            <option value="system">system</option>
          </select>
          <input
            className="input"
            type="date"
            value={from}
            max={to || undefined}
            onChange={(e) => setFilter(setFrom)(e.target.value)}
            title="From date"
          />
          <input
            className="input"
            type="date"
            value={to}
            min={from || undefined}
            onChange={(e) => setFilter(setTo)(e.target.value)}
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
                setPage(0)
              }}
            >
              Clear
            </button>
          )}
        </div>
      </div>

      <div className="card">
        {!data ? (
          <Spinner />
        ) : data.content.length === 0 ? (
          <div className="table-empty">{hasFilters ? 'No entries match these filters' : 'No activity yet'}</div>
        ) : (
          data.content.map((item) =>
            item.kind === 'RUN' ? <RunRow key={`run-${item.id}`} item={item} /> : <EntryRow key={item.id} item={item} />,
          )
        )}
        {data && data.totalElements > 0 && (
          <Pagination
            page={page}
            totalPages={data.totalPages}
            totalElements={data.totalElements}
            noun="entries"
            onChange={setPage}
          />
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
  const [runItems, setRunItems] = useState<Page<AuditFeedItem> | null>(null)
  const [runPage, setRunPage] = useState(0)

  const runType = item.entityType === 'PAYROLL_RUN' ? 'PAYROLL' : 'BULK_RAISE'

  useEffect(() => {
    if (!open) return
    get<Page<AuditFeedItem>>('/audit', { runId: item.runId, runType, page: runPage, size: 20 }).then(setRunItems)
  }, [open, runPage, item.runId, runType])

  const toggle = () => setOpen((prev) => !prev)

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
          ) : runItems.content.length === 0 ? (
            <div className="muted" style={{ padding: '8px 0' }}>
              No item rows for this run
            </div>
          ) : (
            runItems.content.map((ri) => <EntryRow key={ri.id} item={ri} compact />)
          )}
          {runItems && runItems.totalPages > 1 && (
            <Pagination
              page={runPage}
              totalPages={runItems.totalPages}
              totalElements={runItems.totalElements}
              noun="items"
              onChange={setRunPage}
            />
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
