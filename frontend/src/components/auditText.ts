import type { AuditFeedItem } from '../api/types'

/** Tag colour per audit action — shared by the feed and the home screen. */
export function actionTagClass(action: string): string {
  if (action === 'RAISE_PARKED' || action === 'STATUS_CHANGED') return 'tag-orange'
  if (action === 'DELETED' || action === 'RAISE_REJECTED') return 'tag-red'
  if (action === 'SALARY_CHANGED' || action === 'RAISE_APPROVED' || action === 'SALARY_CREDITED') return 'tag-green'
  if (action === 'CREATED') return 'tag-blue'
  return 'tag-gray'
}

/** One audit row as a sentence fragment: who/what, with old → new where we have it. */
export function describeAudit(item: AuditFeedItem): string {
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
