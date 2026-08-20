import { useEffect, useState, type FormEvent } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import { ApiError, get, post, put } from '../api/client'
import type { CurrencyRate, Employee } from '../api/types'
import { useToast } from '../components/Toaster'
import { Field, Spinner } from '../components/ui'

/** One form, two modes: create (POST) and edit (PUT, profile fields only). */
export default function EmployeeFormPage() {
  const { id } = useParams()
  const editing = id !== undefined
  const navigate = useNavigate()
  const toast = useToast()

  const [currencies, setCurrencies] = useState<CurrencyRate[]>([])
  const [existing, setExisting] = useState<Employee | null>(null)
  const [loading, setLoading] = useState(editing)
  const [submitting, setSubmitting] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const [form, setForm] = useState({
    employeeCode: '',
    name: '',
    email: '',
    country: '',
    department: '',
    currencyCode: '',
    annualSalary: '',
    joinedOn: '',
  })

  useEffect(() => {
    get<CurrencyRate[]>('/currencies')
      .then((list) => {
        setCurrencies(list)
        if (!editing && list.length > 0) {
          setForm((f) => (f.currencyCode ? f : { ...f, currencyCode: list[0].code }))
        }
      })
      .catch((e) => toast.error(e.message))
    if (editing) {
      get<Employee>(`/employees/${id}`)
        .then((e) => {
          setExisting(e)
          setForm({
            employeeCode: e.employeeCode,
            name: e.name,
            email: e.email,
            country: e.country,
            department: e.department,
            currencyCode: e.currencyCode,
            annualSalary: String(e.annualSalary),
            joinedOn: e.joinedOn,
          })
        })
        .catch((e) => toast.error(e.message))
        .finally(() => setLoading(false))
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [id])

  const set = (key: keyof typeof form) => (e: { target: { value: string } }) =>
    setForm({ ...form, [key]: e.target.value })

  const onSubmit = async (e: FormEvent) => {
    e.preventDefault()
    setError(null)
    setSubmitting(true)
    try {
      if (editing && existing) {
        const updated = await put<Employee>(`/employees/${id}`, {
          name: form.name,
          email: form.email,
          country: form.country,
          department: form.department,
          currencyCode: form.currencyCode,
          joinedOn: form.joinedOn,
          version: existing.version,
        })
        toast.success('Profile updated')
        navigate(`/employees/${updated.id}`)
      } else {
        const created = await post<Employee>('/employees', {
          employeeCode: form.employeeCode || undefined,
          name: form.name,
          email: form.email,
          country: form.country,
          department: form.department,
          currencyCode: form.currencyCode,
          annualSalary: Number(form.annualSalary),
          joinedOn: form.joinedOn,
        })
        toast.success(`Employee ${created.employeeCode} created`)
        navigate(`/employees/${created.id}`)
      }
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Something went wrong')
    } finally {
      setSubmitting(false)
    }
  }

  if (loading) return <Spinner />

  return (
    <div style={{ maxWidth: 720 }}>
      <div className="page-header">
        <div>
          <h2 className="page-title">{editing ? `Edit ${existing?.name ?? ''}` : 'Add employee'}</h2>
          <div className="page-subtitle">
            {editing
              ? 'Profile fields only — salary and hold status have their own flows'
              : 'Leave the code blank to auto-generate one'}
          </div>
        </div>
      </div>
      <div className="card">
        {error && <div className="alert alert-error">{error}</div>}
        <form onSubmit={onSubmit} className="form-grid">
          {!editing && (
            <Field label="Employee code (optional)">
              <input
                className="input"
                value={form.employeeCode}
                onChange={set('employeeCode')}
                placeholder="Auto-generated if blank"
              />
            </Field>
          )}
          <Field label="Full name" className={editing ? 'span-2' : ''}>
            <input className="input" value={form.name} onChange={set('name')} required />
          </Field>
          <Field label="Email" className="span-2">
            <input className="input" type="email" value={form.email} onChange={set('email')} required />
          </Field>
          <Field label="Country">
            <input className="input" value={form.country} onChange={set('country')} required />
          </Field>
          <Field label="Department">
            <input className="input" value={form.department} onChange={set('department')} required />
          </Field>
          <Field label="Currency">
            <select className="select" value={form.currencyCode} onChange={set('currencyCode')} required>
              {currencies.map((c) => (
                <option key={c.code} value={c.code}>
                  {c.code} — {c.name}
                </option>
              ))}
            </select>
          </Field>
          {!editing && (
            <Field label="Annual salary">
              <input
                className="input"
                type="number"
                min="0"
                step="0.01"
                value={form.annualSalary}
                onChange={set('annualSalary')}
                required
              />
            </Field>
          )}
          <Field label="Joined on">
            <input className="input" type="date" value={form.joinedOn} onChange={set('joinedOn')} required />
          </Field>
          <div className="span-2" style={{ display: 'flex', gap: 10, marginTop: 4 }}>
            <button className="btn btn-primary" type="submit" disabled={submitting}>
              {submitting ? 'Saving…' : editing ? 'Save changes' : 'Create employee'}
            </button>
            <button className="btn" type="button" onClick={() => navigate(-1)}>
              Cancel
            </button>
          </div>
        </form>
      </div>
    </div>
  )
}
