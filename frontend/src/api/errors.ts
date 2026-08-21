import { ApiError } from './client'

/**
 * Error codes → sentences. The backend guarantees machine-readable codes; this
 * is the ONE place they become words a person can act on. VALIDATION messages
 * pass through untouched because the server already phrases those specifically
 * ("Payroll for 2026-08 opens on day 25").
 */
const SENTENCES: Record<string, string> = {
  NETWORK: "Can't reach the server — check your connection and try again.",
  UNAUTHENTICATED: 'Your session has ended — please sign in again.',
  NOT_FOUND: "That record no longer exists — it may have been deleted since you opened it.",
  DUPLICATE_CODE: 'That employee code is already taken — codes are never reused, even for deleted employees.',
  UNKNOWN_CURRENCY: "That currency isn't set up yet — add it under Settings first.",
  STALE_VERSION: 'Someone changed this record while you were editing — reload to see the latest, then try again.',
  CONCURRENT_MODIFICATION: 'Another operation changed this record at the same moment — reload and try again.',
  STALE_PROPOSAL: "This proposal was based on an older salary and can't be applied as-is — reject it and raise again if still wanted.",
  ALREADY_RESOLVED: 'This proposal has already been approved or rejected.',
  INTERNAL: 'Something went wrong on our side — nothing was changed. Please try again in a moment.',
}

export function humanize(error: unknown, fallback = 'Something went wrong — please try again.'): string {
  if (error instanceof ApiError) {
    if (error.code === 'VALIDATION') return error.message
    return SENTENCES[error.code] ?? error.message ?? fallback
  }
  return fallback
}

export function errorCode(error: unknown): string | null {
  return error instanceof ApiError ? error.code : null
}
