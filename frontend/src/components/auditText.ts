import type { AuditFeedItem } from '../api/types'

/** Tag colour per audit action — shared by the feed and the home screen. */
export function actionTagClass(action: string): string {
  if (action === 'RAISE_PARKED' || action === 'STATUS_CHANGED') return 'tag-orange'
  if (action === 'DELETED' || action === 'RAISE_REJECTED') return 'tag-red'
  if (action === 'SALARY_CHANGED' || action === 'RAISE_APPROVED' || action === 'SALARY_CREDITED') return 'tag-green'
  if (action === 'CREATED') return 'tag-blue'
  return 'tag-gray'
}

/** Short human label for an action, used where a tag is still wanted (the audit feed filters on it). */
export function actionLabel(action: string): string {
  const labels: Record<string, string> = {
    CREATED: 'Added',
    PROFILE_UPDATED: 'Profile edited',
    STATUS_CHANGED: 'Hold changed',
    DELETED: 'Removed',
    SALARY_CHANGED: 'Salary changed',
    RAISE_PARKED: 'Sent to review',
    RAISE_APPROVED: 'Raise approved',
    RAISE_REJECTED: 'Raise rejected',
    SALARY_CREDITED: 'Paid',
    RUN_COMPLETED: 'Completed',
    RATE_UPDATED: 'Rate updated',
    THRESHOLD_UPDATED: 'Guardrail changed',
  }
  return labels[action] ?? action.replaceAll('_', ' ').toLowerCase()
}

const MONTHS = ['January', 'February', 'March', 'April', 'May', 'June', 'July', 'August', 'September', 'October', 'November', 'December']

type Change = { old: unknown; new: unknown }
const isChange = (v: unknown): v is Change => !!v && typeof v === 'object' && 'old' in (v as object)

/** "name: Aarav → Arshaw · email: a@x → b@x" */
function fieldChanges(fields: Record<string, unknown>): string {
  return Object.entries(fields)
    .map(([field, change]) => (isChange(change) ? `${field}: ${change.old} → ${change.new}` : `${field}: ${String(change)}`))
    .join(' · ')
}

/**
 * One audit row as a sentence a person would say. Names come from the feed
 * (entityName, deleted employees included); ids never leak into the text.
 */
export function auditSentence(item: AuditFeedItem): string {
  const who = item.entityName ?? `Employee #${item.entityId}`
  const f = item.changedFields ?? {}
  const s = item.runSummary ?? {}
  switch (item.action) {
    case 'RUN_COMPLETED':
      if (item.entityType === 'PAYROLL_RUN') {
        const m = Number(s.month)
        return `Paid ${MONTHS[m - 1] ?? m} ${s.year} · ${Number(s.processedCount ?? 0).toLocaleString()} people credited${
          Number(s.alreadyProcessedCount ?? 0) > 0 ? `, ${Number(s.alreadyProcessedCount).toLocaleString()} already paid` : ''
        }`
      }
      return `Bulk raise of ${s.raiseType === 'PERCENT' ? `${s.raiseValue}%` : `+${Number(s.raiseValue).toLocaleString()}`} applied · ${Number(
        s.appliedCount ?? 0,
      ).toLocaleString()} people changed${Number(s.reviewCount ?? 0) > 0 ? `, ${s.reviewCount} sent to review` : ''}`
    case 'CREATED':
      return `${who} was added`
    case 'PROFILE_UPDATED':
      return `${who}'s details changed — ${fieldChanges(f)}`
    case 'STATUS_CHANGED': {
      const status = isChange(f.status) ? f.status.new : undefined
      return status === 'ON_HOLD' ? `${who} put on salary hold` : `${who}'s salary hold released`
    }
    case 'DELETED':
      return `${who} was removed (history kept)`
    case 'SALARY_CHANGED':
      return `${who}'s salary changed`
    case 'RAISE_PARKED':
      return `${who}'s raise sent to review — over the guardrail`
    case 'RAISE_APPROVED':
      return `${who}'s raise approved`
    case 'RAISE_REJECTED':
      return `${who}'s raise rejected`
    case 'SALARY_CREDITED':
      return `${who} paid`
    case 'RATE_UPDATED':
      return `Exchange rate for ${String(f.code ?? '')} changed${isChange(f.usdRate) ? ` ${f.usdRate.old} → ${f.usdRate.new}` : ''}`
    case 'THRESHOLD_UPDATED':
      return `Raise guardrail changed${isChange(f.raiseThresholdPercent) ? ` ${f.raiseThresholdPercent.old}% → ${f.raiseThresholdPercent.new}%` : ''}`
    default:
      return `${who} — ${actionLabel(item.action)}`
  }
}

/** Kept for the per-employee activity panel, which shows the raw field diff. */
export function describeAudit(item: AuditFeedItem): string {
  return auditSentence(item)
}

/** "just now", "12 min ago", "3 h ago", "yesterday", else the date. */
export function timeAgo(iso: string, now = new Date()): string {
  const then = new Date(iso)
  const mins = Math.round((now.getTime() - then.getTime()) / 60000)
  if (mins < 1) return 'just now'
  if (mins < 60) return `${mins} min ago`
  const hours = Math.round(mins / 60)
  if (hours < 24) return `${hours} h ago`
  const days = Math.round(hours / 24)
  if (days === 1) return 'yesterday'
  if (days < 7) return `${days} days ago`
  return then.toLocaleDateString(undefined, { day: 'numeric', month: 'short' })
}
