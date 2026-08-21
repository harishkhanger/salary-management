import { useCallback, useEffect, useState } from 'react'
import { get, put } from '../api/client'
import { humanize } from '../api/errors'
import type { CurrencyRate, Settings } from '../api/types'
import { useToast } from '../components/Toaster'
import { Field, formatDateTime } from '../components/ui'

export default function SettingsPage() {
  const toast = useToast()

  // --- guardrail threshold ---
  const [threshold, setThreshold] = useState('')
  const [savingThreshold, setSavingThreshold] = useState(false)

  // --- currency rates ---
  const [currencies, setCurrencies] = useState<CurrencyRate[]>([])
  const [drafts, setDrafts] = useState<Record<string, string>>({})
  const [savingCode, setSavingCode] = useState<string | null>(null)

  const load = useCallback(() => {
    get<Settings>('/settings').then((s) => setThreshold(String(s.raiseThresholdPercent)))
    get<CurrencyRate[]>('/currencies').then((list) => {
      setCurrencies(list)
      setDrafts(Object.fromEntries(list.map((c) => [c.code, String(c.usdRate)])))
    })
  }, [])

  useEffect(load, [load])

  const saveThreshold = async () => {
    setSavingThreshold(true)
    try {
      const saved = await put<Settings>('/settings', { raiseThresholdPercent: Number(threshold) })
      setThreshold(String(saved.raiseThresholdPercent))
      toast.success(`Guardrail threshold is now ${saved.raiseThresholdPercent}% — applies to the next change`)
    } catch (e) {
      toast.error(humanize(e, 'Could not save'))
    } finally {
      setSavingThreshold(false)
    }
  }

  const saveRate = async (code: string) => {
    setSavingCode(code)
    try {
      const saved = await put<CurrencyRate>(`/currencies/${code}`, { usdRate: Number(drafts[code]) })
      setCurrencies((prev) => prev.map((c) => (c.code === code ? saved : c)))
      setDrafts((d) => ({ ...d, [code]: String(saved.usdRate) }))
      toast.success(`${code} rate updated — affects future credits and analytics only`)
    } catch (e) {
      toast.error(humanize(e, 'Could not save'))
    } finally {
      setSavingCode(null)
    }
  }

  return (
    <div className="stack">
      <div className="page-header">
        <div>
          <h2 className="page-title">Settings</h2>
          <div className="page-subtitle">Guardrail threshold and manually managed currency rates</div>
        </div>
      </div>

      <div className="card" style={{ maxWidth: 560 }}>
        <h3 style={{ marginBottom: 6 }}>Raise guardrail</h3>
        <p className="muted" style={{ marginTop: 0 }}>
          Any change pushing an employee's cumulative 12-month raise above this threshold is parked for review
          instead of applying.
        </p>
        <div className="toolbar">
          <Field label="Threshold (%)">
            <input
              className="input"
              style={{ width: 120 }}
              type="number"
              min="0.01"
              step="0.01"
              value={threshold}
              onChange={(e) => setThreshold(e.target.value)}
            />
          </Field>
          <div className="field">
            <span className="field-label">&nbsp;</span>
            <button className="btn btn-primary" onClick={saveThreshold} disabled={savingThreshold || !threshold}>
              {savingThreshold ? 'Saving…' : 'Save threshold'}
            </button>
          </div>
        </div>
      </div>

      <div className="card" style={{ maxWidth: 720 }}>
        <h3 style={{ marginBottom: 6 }}>Currency rates</h3>
        <p className="muted" style={{ marginTop: 0 }}>
          Local units per 1 USD. Edits affect future payroll credits and analytics — historical credits keep
          the rate snapshotted when they were created.
        </p>
        <div className="table-wrap">
          <table className="table">
            <thead>
              <tr>
                <th>Currency</th>
                <th className="num">Rate (per USD)</th>
                <th>Last updated</th>
                <th />
              </tr>
            </thead>
            <tbody>
              {currencies.length === 0 ? (
                <tr>
                  <td colSpan={4} className="table-empty">
                    No currencies configured
                  </td>
                </tr>
              ) : (
                currencies.map((c) => (
                  <tr key={c.code}>
                    <td>
                      <strong>{c.code}</strong> <span className="muted">· {c.name}</span>
                    </td>
                    <td className="num" style={{ width: 160 }}>
                      <input
                        className="input"
                        style={{ textAlign: 'right' }}
                        type="number"
                        min="0.000001"
                        step="0.000001"
                        value={drafts[c.code] ?? ''}
                        onChange={(e) => setDrafts({ ...drafts, [c.code]: e.target.value })}
                      />
                    </td>
                    <td className="muted">{formatDateTime(c.updatedAt)}</td>
                    <td style={{ width: 90 }}>
                      <button
                        className="btn btn-sm"
                        disabled={savingCode === c.code || drafts[c.code] === String(c.usdRate)}
                        onClick={() => saveRate(c.code)}
                      >
                        {savingCode === c.code ? 'Saving…' : 'Save'}
                      </button>
                    </td>
                  </tr>
                ))
              )}
            </tbody>
          </table>
        </div>
      </div>
    </div>
  )
}
