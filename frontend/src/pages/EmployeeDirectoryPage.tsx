import { useCallback, useEffect, useRef, useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { get } from '../api/client'
import type { Employee, Page } from '../api/types'
import { useToast } from '../components/Toaster'
import { Pagination, StatusTag, formatMoney } from '../components/ui'

export default function EmployeeDirectoryPage() {
  const navigate = useNavigate()
  const toast = useToast()
  const [data, setData] = useState<Page<Employee> | null>(null)
  const [loading, setLoading] = useState(false)
  const [page, setPage] = useState(0)
  const [search, setSearch] = useState('')
  const [country, setCountry] = useState('')
  const [department, setDepartment] = useState('')
  const [status, setStatus] = useState('')
  // guards against out-of-order responses painting stale results
  const requestSeq = useRef(0)

  // draft filter inputs apply on Enter / Apply, never per keystroke
  const [draft, setDraft] = useState({ search: '', country: '', department: '' })

  const applyFilters = () => {
    setPage(0)
    setSearch(draft.search)
    setCountry(draft.country)
    setDepartment(draft.department)
  }

  const load = useCallback(() => {
    const seq = ++requestSeq.current
    setLoading(true)
    get<Page<Employee>>('/employees', {
      page,
      size: 20,
      search: search || undefined,
      country: country || undefined,
      department: department || undefined,
      status: status || undefined,
    })
      .then((result) => {
        if (seq === requestSeq.current) setData(result)
      })
      .catch((e) => toast.error(e.message))
      .finally(() => {
        if (seq === requestSeq.current) setLoading(false)
      })
    // toast identity is stable; deliberately not a dependency
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [page, search, country, department, status])

  useEffect(load, [load])

  return (
    <div>
      <div className="page-header">
        <div>
          <h2 className="page-title">Employees</h2>
          <div className="page-subtitle">Directory of all active employees</div>
        </div>
        <button className="btn btn-primary" onClick={() => navigate('/employees/new')}>
          + Add employee
        </button>
      </div>

      <div className="card">
        <form
          className="toolbar"
          style={{ marginBottom: 14 }}
          onSubmit={(e) => {
            e.preventDefault()
            applyFilters()
          }}
        >
          <input
            className="input"
            style={{ width: 220 }}
            placeholder="Search name or code…"
            value={draft.search}
            onChange={(e) => setDraft({ ...draft, search: e.target.value })}
          />
          <input
            className="input"
            style={{ width: 150 }}
            placeholder="Country"
            value={draft.country}
            onChange={(e) => setDraft({ ...draft, country: e.target.value })}
          />
          <input
            className="input"
            style={{ width: 160 }}
            placeholder="Department"
            value={draft.department}
            onChange={(e) => setDraft({ ...draft, department: e.target.value })}
          />
          <select
            className="select"
            style={{ width: 130 }}
            value={status}
            onChange={(e) => {
              setPage(0)
              setStatus(e.target.value)
            }}
          >
            <option value="">All statuses</option>
            <option value="ACTIVE">Active</option>
            <option value="ON_HOLD">On hold</option>
          </select>
          <button className="btn" type="submit">
            Apply
          </button>
        </form>

        <div className="table-wrap">
          <table className="table">
            <thead>
              <tr>
                <th>Code</th>
                <th>Name</th>
                <th>Email</th>
                <th>Country</th>
                <th>Department</th>
                <th className="num">Annual salary</th>
                <th>Status</th>
              </tr>
            </thead>
            <tbody>
              {loading && !data ? (
                <tr>
                  <td colSpan={7} className="table-empty">
                    Loading…
                  </td>
                </tr>
              ) : data && data.content.length === 0 ? (
                <tr>
                  <td colSpan={7} className="table-empty">
                    No employees match these filters
                  </td>
                </tr>
              ) : (
                data?.content.map((e) => (
                  <tr key={e.id}>
                    <td>
                      <Link to={`/employees/${e.id}`}>{e.employeeCode}</Link>
                    </td>
                    <td style={{ fontWeight: 550 }}>{e.name}</td>
                    <td className="muted">{e.email}</td>
                    <td>{e.country}</td>
                    <td>{e.department}</td>
                    <td className="num">{formatMoney(e.annualSalary, e.currencyCode)}</td>
                    <td>
                      <StatusTag status={e.status} />
                    </td>
                  </tr>
                ))
              )}
            </tbody>
          </table>
        </div>
        {data && (
          <Pagination
            page={page}
            totalPages={data.totalPages}
            totalElements={data.totalElements}
            noun="employees"
            onChange={setPage}
          />
        )}
      </div>
    </div>
  )
}
