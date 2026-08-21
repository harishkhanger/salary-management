import { useCallback, useEffect, useState } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import { del, get, post, put } from '../api/client'
import { humanize } from '../api/errors'
import type {
  AuditFeedItem,
  ChangeType,
  Employee,
  Page,
  SalaryChange,
  SalaryChangeOutcome,
  SalaryCredit,
} from '../api/types'
import Modal from '../components/Modal'
import { useToast } from '../components/Toaster'
import { Field, Pagination, Spinner, StatusTag, formatDate, formatDateTime, formatMoney } from '../components/ui'

const MONTHS = ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec']

export default function EmployeeDetailPage() {
  const { id } = useParams()
  const navigate = useNavigate()
  const toast = useToast()

  const [employee, setEmployee] = useState<Employee | null>(null)
  const [tab, setTab] = useState<'changes' | 'credits' | 'activity'>('changes')
  const [changeModal, setChangeModal] = useState(false)
  const [confirmModal, setConfirmModal] = useState<'delete' | 'hold' | null>(null)
  const [refreshKey, setRefreshKey] = useState(0)

  const reload = useCallback(() => {
    get<Employee>(`/employees/${id}`)
      .then(setEmployee)
      .catch((e) => {
        toast.error(e.message)
        navigate('/employees')
      })
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [id])

  useEffect(reload, [reload])

  if (!employee) return <Spinner />

  const holdVerb = employee.status === 'ACTIVE' ? 'Put on hold' : 'Release hold'

  const changeStatus = async () => {
    try {
      const updated = await put<Employee>(`/employees/${employee.id}/status`, {
        status: employee.status === 'ACTIVE' ? 'ON_HOLD' : 'ACTIVE',
        version: employee.version,
      })
      setEmployee(updated)
      toast.success(updated.status === 'ON_HOLD' ? 'Salary on hold — payroll will skip this employee' : 'Hold released')
      setRefreshKey((k) => k + 1)
    } catch (e) {
      toast.error(humanize(e, 'Failed'))
    } finally {
      setConfirmModal(null)
    }
  }

  const deleteEmployee = async () => {
    try {
      await del(`/employees/${employee.id}`)
      toast.success('Employee deleted (history preserved)')
      navigate('/employees')
    } catch (e) {
      toast.error(humanize(e, 'Failed'))
    }
  }

  return (
    <div className="stack">
      <div className="page-header">
        <div>
          <h2 className="page-title">
            {employee.name} <span className="muted" style={{ fontWeight: 400 }}>· {employee.employeeCode}</span>
          </h2>
          <div className="page-subtitle">
            {employee.department} · {employee.country}
          </div>
        </div>
        <div className="toolbar">
          <button className="btn" onClick={() => navigate(`/employees/${employee.id}/edit`)}>
            Edit profile
          </button>
          <button className="btn" onClick={() => setConfirmModal('hold')}>
            {holdVerb}
          </button>
          <button className="btn btn-primary" onClick={() => setChangeModal(true)}>
            Change salary
          </button>
          <button className="btn btn-ghost-danger" onClick={() => setConfirmModal('delete')}>
            Delete
          </button>
        </div>
      </div>

      <div className="card">
        <div className="desc-grid">
          <div className="desc-item">
            <div className="desc-label">Annual salary</div>
            <div className="salary-figure">{formatMoney(employee.annualSalary, employee.currencyCode)}</div>
          </div>
          <div className="desc-item">
            <div className="desc-label">Status</div>
            <div className="desc-value">
              <StatusTag status={employee.status} />
            </div>
          </div>
          <div className="desc-item">
            <div className="desc-label">Email</div>
            <div className="desc-value">{employee.email}</div>
          </div>
          <div className="desc-item">
            <div className="desc-label">Joined</div>
            <div className="desc-value">{formatDate(employee.joinedOn)}</div>
          </div>
        </div>
      </div>

      <div className="card">
        <div className="tabs">
          <button className={`tab${tab === 'changes' ? ' active' : ''}`} onClick={() => setTab('changes')}>
            Salary changes
          </button>
          <button className={`tab${tab === 'credits' ? ' active' : ''}`} onClick={() => setTab('credits')}>
            Payroll credits
          </button>
          <button className={`tab${tab === 'activity' ? ' active' : ''}`} onClick={() => setTab('activity')}>
            Activity
          </button>
        </div>
        {tab === 'changes' && <ChangesPanel employeeId={employee.id} refreshKey={refreshKey} />}
        {tab === 'credits' && <CreditsPanel employeeId={employee.id} refreshKey={refreshKey} />}
        {tab === 'activity' && <ActivityPanel employeeId={employee.id} refreshKey={refreshKey} />}
      </div>

      <SalaryChangeModal
        open={changeModal}
        employee={employee}
        onClose={() => setChangeModal(false)}
        onDone={(updated) => {
          setChangeModal(false)
          if (updated) setEmployee(updated)
          else reload()
          setRefreshKey((k) => k + 1)
        }}
      />

      <Modal
        title={confirmModal === 'delete' ? 'Delete employee?' : holdVerb + '?'}
        open={confirmModal !== null}
        onClose={() => setConfirmModal(null)}
        footer={
          <>
            <button className="btn" onClick={() => setConfirmModal(null)}>
              Cancel
            </button>
            {confirmModal === 'delete' ? (
              <button className="btn btn-danger" onClick={deleteEmployee}>
                Delete permanently
              </button>
            ) : (
              <button className="btn btn-primary" onClick={changeStatus}>
                Confirm
              </button>
            )}
          </>
        }
      >
        {confirmModal === 'delete' ? (
          <p>
            <strong>{employee.name}</strong> will be removed from the directory. Payroll history is preserved,
            but deletion is final — there is no restore.
          </p>
        ) : employee.status === 'ACTIVE' ? (
          <p>Payroll processing will skip this employee until the hold is released. Compensation changes stay possible.</p>
        ) : (
          <p>This employee will be included in payroll processing again.</p>
        )}
      </Modal>
    </div>
  )
}

/* ---------------- salary change modal ---------------- */

function SalaryChangeModal({
  open,
  employee,
  onClose,
  onDone,
}: {
  open: boolean
  employee: Employee
  onClose: () => void
  onDone: (updated: Employee | null) => void
}) {
  const toast = useToast()
  const [changeType, setChangeType] = useState<ChangeType>('PERCENT')
  const [value, setValue] = useState('')
  const [submitting, setSubmitting] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const hints: Record<ChangeType, string> = {
    PERCENT: 'Raise by percent — e.g. 10 adds 10% to the current salary',
    AMOUNT: 'Raise by a fixed amount in ' + employee.currencyCode,
    CORRECTION: 'Set the absolute new annual salary (can be lower than current)',
  }

  const submit = async () => {
    setSubmitting(true)
    setError(null)
    try {
      const outcome = await post<SalaryChangeOutcome>(`/employees/${employee.id}/salary-changes`, {
        changeType,
        value: Number(value),
        version: employee.version,
      })
      if (outcome.status === 'APPLIED') {
        toast.success(`Salary updated to ${formatMoney(outcome.change!.newSalary, employee.currencyCode)}`)
        onDone(outcome.employee)
      } else {
        toast.info(`Parked for review: ${outcome.reason}`)
        onDone(null)
      }
      setValue('')
    } catch (e) {
      setError(humanize(e, 'Failed'))
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <Modal
      title={`Change salary — ${employee.name}`}
      open={open}
      onClose={onClose}
      footer={
        <>
          <button className="btn" onClick={onClose}>
            Cancel
          </button>
          <button className="btn btn-primary" onClick={submit} disabled={submitting || !value}>
            {submitting ? 'Applying…' : 'Apply change'}
          </button>
        </>
      }
    >
      {error && <div className="alert alert-error">{error}</div>}
      <div className="alert alert-info">
        Current salary: <strong>{formatMoney(employee.annualSalary, employee.currencyCode)}</strong>. Changes above
        the guardrail threshold are parked for review instead of applying.
      </div>
      <Field label="Change type">
        <select className="select" value={changeType} onChange={(e) => setChangeType(e.target.value as ChangeType)}>
          <option value="PERCENT">Raise by percent</option>
          <option value="AMOUNT">Raise by amount</option>
          <option value="CORRECTION">Correction (set absolute)</option>
        </select>
      </Field>
      <Field label="Value">
        <input
          className="input"
          type="number"
          min="0.01"
          step="0.01"
          value={value}
          onChange={(e) => setValue(e.target.value)}
          placeholder={changeType === 'PERCENT' ? 'e.g. 10' : 'e.g. 50000'}
        />
      </Field>
      <p className="muted" style={{ marginTop: -6 }}>{hints[changeType]}</p>
    </Modal>
  )
}

/* ---------------- history panels ---------------- */

function ChangesPanel({ employeeId, refreshKey }: { employeeId: number; refreshKey: number }) {
  const [data, setData] = useState<Page<SalaryChange> | null>(null)
  const [page, setPage] = useState(0)

  useEffect(() => {
    get<Page<SalaryChange>>(`/employees/${employeeId}/salary-changes`, { page, size: 10 }).then(setData)
  }, [employeeId, page, refreshKey])

  if (!data) return <Spinner />
  return (
    <div className="table-wrap">
      <table className="table">
        <thead>
          <tr>
            <th>When</th>
            <th>Type</th>
            <th className="num">Old salary</th>
            <th className="num">New salary</th>
            <th className="num">Delta</th>
            <th>Actor</th>
            <th>Run</th>
          </tr>
        </thead>
        <tbody>
          {data.content.length === 0 ? (
            <tr>
              <td colSpan={7} className="table-empty">
                No salary changes yet
              </td>
            </tr>
          ) : (
            data.content.map((c) => {
              const delta = c.newSalary - c.oldSalary
              return (
                <tr key={c.id}>
                  <td className="muted">{formatDateTime(c.createdAt)}</td>
                  <td>
                    <span className={`tag ${c.changeType === 'CORRECTION' ? 'tag-blue' : 'tag-gray'}`}>
                      {c.changeType === 'PERCENT' ? `+${c.percentValue}%` : c.changeType}
                    </span>
                  </td>
                  <td className="num">{formatMoney(c.oldSalary)}</td>
                  <td className="num" style={{ fontWeight: 600 }}>
                    {formatMoney(c.newSalary)}
                  </td>
                  <td className="num" style={{ color: delta >= 0 ? 'var(--success)' : 'var(--danger)' }}>
                    {delta >= 0 ? '+' : ''}
                    {formatMoney(delta)}
                  </td>
                  <td>{c.actor}</td>
                  <td className="muted">{c.bulkRaiseRunId ? `#${c.bulkRaiseRunId}` : '—'}</td>
                </tr>
              )
            })
          )}
        </tbody>
      </table>
      <Pagination
        page={page}
        totalPages={data.totalPages}
        totalElements={data.totalElements}
        noun="changes"
        onChange={setPage}
      />
    </div>
  )
}

function CreditsPanel({ employeeId, refreshKey }: { employeeId: number; refreshKey: number }) {
  const [data, setData] = useState<Page<SalaryCredit> | null>(null)
  const [page, setPage] = useState(0)

  useEffect(() => {
    get<Page<SalaryCredit>>(`/employees/${employeeId}/credits`, { page, size: 12 }).then(setData)
  }, [employeeId, page, refreshKey])

  if (!data) return <Spinner />
  return (
    <div className="table-wrap">
      <table className="table">
        <thead>
          <tr>
            <th>Period</th>
            <th className="num">Amount</th>
            <th className="num">USD rate at credit</th>
            <th className="num">USD value</th>
            <th>Run</th>
            <th>Credited at</th>
          </tr>
        </thead>
        <tbody>
          {data.content.length === 0 ? (
            <tr>
              <td colSpan={6} className="table-empty">
                No payroll credits yet
              </td>
            </tr>
          ) : (
            data.content.map((c) => (
              <tr key={c.id}>
                <td style={{ fontWeight: 550 }}>
                  {MONTHS[c.month - 1]} {c.year}
                </td>
                <td className="num">{formatMoney(c.amount, c.currencyCode)}</td>
                <td className="num muted">{c.usdRate}</td>
                <td className="num">{formatMoney(c.amount / c.usdRate, 'USD')}</td>
                <td className="muted">#{c.payrollRunId}</td>
                <td className="muted">{formatDateTime(c.createdAt)}</td>
              </tr>
            ))
          )}
        </tbody>
      </table>
      <Pagination
        page={page}
        totalPages={data.totalPages}
        totalElements={data.totalElements}
        noun="credits"
        onChange={setPage}
      />
    </div>
  )
}

function ActivityPanel({ employeeId, refreshKey }: { employeeId: number; refreshKey: number }) {
  const [data, setData] = useState<Page<AuditFeedItem> | null>(null)
  const [page, setPage] = useState(0)

  // a fresh mutation lands on page 0 — jump back so it is visible
  useEffect(() => setPage(0), [refreshKey])

  useEffect(() => {
    get<Page<AuditFeedItem>>('/audit', { entityType: 'EMPLOYEE', entityId: employeeId, page, size: 15 }).then(setData)
  }, [employeeId, page, refreshKey])

  const items = data?.content ?? []

  return (
    <div>
      <table className="table">
        <thead>
          <tr>
            <th>When</th>
            <th>Action</th>
            <th>Actor</th>
            <th>Details</th>
          </tr>
        </thead>
        <tbody>
          {data && items.length === 0 ? (
            <tr>
              <td colSpan={4} className="table-empty">
                No activity recorded
              </td>
            </tr>
          ) : (
            items.map((item) => (
              <tr key={item.id}>
                <td className="muted" style={{ whiteSpace: 'nowrap' }}>
                  {formatDateTime(item.createdAt)}
                </td>
                <td>
                  <span className="tag tag-gray">{item.action.replaceAll('_', ' ')}</span>
                </td>
                <td>{item.actor}</td>
                <td className="muted" style={{ fontSize: 12.5 }}>
                  {describeAudit(item)}
                </td>
              </tr>
            ))
          )}
        </tbody>
      </table>
      {data && data.totalElements > 0 && (
        <Pagination
          page={page}
          totalPages={data.totalPages}
          totalElements={data.totalElements}
          noun="events"
          onChange={setPage}
        />
      )}
    </div>
  )
}

function describeAudit(item: AuditFeedItem): string {
  if (item.changedFields) {
    return Object.entries(item.changedFields)
      .map(([field, change]) => {
        if (change && typeof change === 'object' && 'old' in (change as object)) {
          const c = change as { old: unknown; new: unknown }
          return `${field}: ${c.old} → ${c.new}`
        }
        return `${field}: ${String(change)}`
      })
      .join(' · ')
  }
  if (item.refTable && item.refId) {
    return `${item.refTable} #${item.refId}${item.runId ? ` (run #${item.runId})` : ''}`
  }
  return '—'
}
