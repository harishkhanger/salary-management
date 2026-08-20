import type { ReactNode } from 'react'
import type { EmployeeStatus } from '../api/types'

export const Spinner = () => <div className="spinner" aria-label="Loading" />

export function StatusTag({ status }: { status: EmployeeStatus }) {
  return (
    <span className={`tag ${status === 'ACTIVE' ? 'tag-green' : 'tag-orange'}`}>
      {status === 'ACTIVE' ? 'Active' : 'On hold'}
    </span>
  )
}

export function Field({
  label,
  error,
  children,
  className,
}: {
  label: string
  error?: string
  children: ReactNode
  className?: string
}) {
  return (
    <label className={`field${className ? ` ${className}` : ''}`}>
      <span className="field-label">{label}</span>
      {children}
      {error && <span className="field-error">{error}</span>}
    </label>
  )
}

export function Pagination({
  page,
  totalPages,
  totalElements,
  noun,
  onChange,
}: {
  page: number
  totalPages: number
  totalElements: number
  noun: string
  onChange: (page: number) => void
}) {
  return (
    <div className="pagination">
      <span className="pagination-info">
        {totalElements.toLocaleString()} {noun} · page {Math.min(page + 1, Math.max(totalPages, 1))} of{' '}
        {Math.max(totalPages, 1)}
      </span>
      <div className="pagination-controls">
        <button className="btn btn-sm" disabled={page === 0} onClick={() => onChange(page - 1)}>
          ← Prev
        </button>
        <button
          className="btn btn-sm"
          disabled={page >= totalPages - 1}
          onClick={() => onChange(page + 1)}
        >
          Next →
        </button>
      </div>
    </div>
  )
}

export const formatMoney = (value: number, currency?: string) =>
  `${currency ? currency + ' ' : ''}${Number(value).toLocaleString(undefined, {
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
  })}`

export const formatDateTime = (iso: string) =>
  new Date(iso).toLocaleString(undefined, {
    year: 'numeric',
    month: 'short',
    day: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
  })

export const formatDate = (iso: string) =>
  new Date(iso).toLocaleDateString(undefined, { year: 'numeric', month: 'short', day: 'numeric' })
