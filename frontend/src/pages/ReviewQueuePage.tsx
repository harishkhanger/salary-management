import { useCallback, useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { get, post } from '../api/client'
import { errorCode, humanize } from '../api/errors'
import type { Page, ReviewItem, ReviewStatus } from '../api/types'
import Modal from '../components/Modal'
import { useToast } from '../components/Toaster'
import { Pagination, formatDateTime, formatMoney } from '../components/ui'

const STATUS_TABS: { key: ReviewStatus; label: string }[] = [
  { key: 'PENDING', label: 'Pending' },
  { key: 'APPROVED', label: 'Approved' },
  { key: 'REJECTED', label: 'Rejected' },
]

export default function ReviewQueuePage() {
  const toast = useToast()
  const [status, setStatus] = useState<ReviewStatus>('PENDING')
  const [page, setPage] = useState(0)
  const [data, setData] = useState<Page<ReviewItem> | null>(null)
  const [confirm, setConfirm] = useState<{ item: ReviewItem; action: 'approve' | 'reject' } | null>(null)
  const [acting, setActing] = useState(false)

  const load = useCallback(() => {
    get<Page<ReviewItem>>('/review-queue', { status, page, size: 15 }).then(setData)
  }, [status, page])

  useEffect(load, [load])

  const resolve = async () => {
    if (!confirm) return
    setActing(true)
    try {
      if (confirm.action === 'approve') {
        await post(`/review-queue/${confirm.item.id}/approve`)
        toast.success(
          `Approved — ${confirm.item.name ?? 'employee'} now at ${formatMoney(confirm.item.proposedNew)}`,
        )
      } else {
        await post(`/review-queue/${confirm.item.id}/reject`)
        toast.info('Proposal rejected — salary unchanged')
      }
      load()
    } catch (e) {
      toast.error(humanize(e, 'Action failed'))
      // the queue has moved on under us: show the current truth
      if (errorCode(e) === 'ALREADY_RESOLVED' || errorCode(e) === 'STALE_PROPOSAL') load()
    } finally {
      setActing(false)
      setConfirm(null)
    }
  }

  return (
    <div>
      <div className="page-header">
        <div>
          <h2 className="page-title">Review queue</h2>
          <div className="page-subtitle">Raises the guardrail parked — approve to apply exactly what was proposed</div>
        </div>
      </div>

      <div className="card">
        <div className="tabs">
          {STATUS_TABS.map((t) => (
            <button
              key={t.key}
              className={`tab${status === t.key ? ' active' : ''}`}
              onClick={() => {
                setStatus(t.key)
                setPage(0)
              }}
            >
              {t.label}
            </button>
          ))}
        </div>

        <div className="table-wrap">
          <table className="table">
            <thead>
              <tr>
                <th>Employee</th>
                <th className="num">Current → Proposed</th>
                <th className="num">Delta</th>
                <th>Reason</th>
                <th>Parked</th>
                {status === 'PENDING' ? <th /> : <th>Resolved</th>}
              </tr>
            </thead>
            <tbody>
              {!data || data.content.length === 0 ? (
                <tr>
                  <td colSpan={6} className="table-empty">
                    {status === 'PENDING' ? 'Nothing waiting for review' : `No ${status.toLowerCase()} items`}
                  </td>
                </tr>
              ) : (
                data.content.map((item) => {
                  const delta = item.proposedNew - item.proposedOld
                  return (
                    <tr key={item.id}>
                      <td>
                        <Link to={`/employees/${item.employeeId}`} style={{ fontWeight: 600 }}>
                          {item.name ?? `#${item.employeeId}`}
                        </Link>
                        <div className="muted" style={{ fontSize: 12 }}>
                          {item.employeeCode}
                          {item.bulkRaiseRunId ? ` · run #${item.bulkRaiseRunId}` : ' · individual'}
                        </div>
                      </td>
                      <td className="num">
                        {formatMoney(item.proposedOld)} → <strong>{formatMoney(item.proposedNew)}</strong>
                      </td>
                      <td className="num" style={{ color: delta >= 0 ? 'var(--success)' : 'var(--danger)' }}>
                        {delta >= 0 ? '+' : ''}
                        {formatMoney(delta)}
                      </td>
                      <td className="muted" style={{ maxWidth: 320, fontSize: 12.5 }}>
                        {item.reason}
                      </td>
                      <td className="muted" style={{ whiteSpace: 'nowrap' }}>
                        {formatDateTime(item.createdAt)}
                      </td>
                      {status === 'PENDING' ? (
                        <td style={{ whiteSpace: 'nowrap' }}>
                          <button
                            className="btn btn-sm btn-primary"
                            style={{ marginRight: 6 }}
                            onClick={() => setConfirm({ item, action: 'approve' })}
                          >
                            Approve
                          </button>
                          <button
                            className="btn btn-sm btn-ghost-danger"
                            onClick={() => setConfirm({ item, action: 'reject' })}
                          >
                            Reject
                          </button>
                        </td>
                      ) : (
                        <td className="muted" style={{ whiteSpace: 'nowrap' }}>
                          {item.resolvedAt ? formatDateTime(item.resolvedAt) : '—'}
                        </td>
                      )}
                    </tr>
                  )
                })
              )}
            </tbody>
          </table>
        </div>
        {data && (
          <Pagination
            page={page}
            totalPages={data.totalPages}
            totalElements={data.totalElements}
            noun="items"
            onChange={setPage}
          />
        )}
      </div>

      <Modal
        title={confirm?.action === 'approve' ? 'Approve raise?' : 'Reject proposal?'}
        open={confirm !== null}
        onClose={() => setConfirm(null)}
        footer={
          <>
            <button className="btn" onClick={() => setConfirm(null)}>
              Cancel
            </button>
            <button
              className={`btn ${confirm?.action === 'approve' ? 'btn-primary' : 'btn-danger'}`}
              onClick={resolve}
              disabled={acting}
            >
              {acting ? 'Working…' : confirm?.action === 'approve' ? 'Approve & apply' : 'Reject'}
            </button>
          </>
        }
      >
        {confirm && (
          <p>
            {confirm.action === 'approve' ? (
              <>
                <strong>{confirm.item.name ?? 'This employee'}</strong> will move from{' '}
                {formatMoney(confirm.item.proposedOld)} to <strong>{formatMoney(confirm.item.proposedNew)}</strong>{' '}
                immediately. Validators are skipped — you are the review.
              </>
            ) : (
              <>The proposal is discarded and the salary stays unchanged. This cannot be re-opened — a new raise would need to be issued.</>
            )}
          </p>
        )}
      </Modal>
    </div>
  )
}
