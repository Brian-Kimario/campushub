import type { FieldValues, Path, UseFormSetError } from 'react-hook-form'

// RFC 9457 problem details with errorCode, errors[] and requestId (API_CONTRACT §3).
export interface FieldIssue {
  field: string
  reason: string
}

export interface ApiFailure {
  status: number | 'NETWORK'
  code: string | null
  message: string
  fieldIssues: FieldIssue[]
  requestId: string | null
}

interface ProblemBody {
  errorCode?: string
  detail?: string
  errors?: FieldIssue[]
  requestId?: string
}

const MESSAGES: Record<string, string> = {
  VALIDATION_FAILED: 'Please fix the highlighted fields.',
  FIELD_NOT_EDITABLE: 'That field cannot be changed here.',
  UNAUTHENTICATED: 'Your session has ended. Sign in again.',
  INVALID_CREDENTIALS: 'Check your details and try again.',
  FORBIDDEN_ROLE: 'Your role cannot do this.',
  FORBIDDEN_STATE: 'This action is not allowed right now.',
  PASSWORD_CHANGE_REQUIRED: 'Change your temporary password before continuing.',
  NOT_FOUND: 'This item was not found, or you do not have access to it.',
  STALE_VERSION:
    'This item was changed by someone else. Reload to see the latest version. Your changes are kept below.',
  INVALID_TRANSITION: 'That change is not allowed from the current state.',
  PROJECT_ARCHIVED: 'This project is archived, so it cannot be changed.',
  SUPERVISOR_NOT_ELIGIBLE: 'That person cannot supervise projects.',
  REVIEW_EXISTS: 'This submission already has a decision.',
  FEEDBACK_REQUIRED: 'Feedback is required when requesting changes.',
  RATE_LIMITED: 'Too many attempts. Wait a moment and try again.',
  DEPENDENCY_UNAVAILABLE: 'The service is temporarily unavailable. Try again.',
  INTERNAL_ERROR: 'Something went wrong. Try again.',
}

const NETWORK_MESSAGE = 'Could not reach the server. Check your connection and try again.'

/** Turns an RTK Query error into a message and field issues. Unknown codes fall back to the server detail. */
export function describeError(error: unknown): ApiFailure {
  const failure = error as { status?: unknown; data?: unknown } | undefined
  if (!failure || typeof failure.status !== 'number') {
    return { status: 'NETWORK', code: null, message: NETWORK_MESSAGE, fieldIssues: [], requestId: null }
  }
  const body = (failure.data ?? {}) as ProblemBody
  const code = body.errorCode ?? null
  return {
    status: failure.status,
    code,
    message: (code && MESSAGES[code]) || body.detail || 'Something went wrong. Try again.',
    fieldIssues: Array.isArray(body.errors) ? body.errors : [],
    requestId: body.requestId ?? null,
  }
}

/** Attaches server field errors to their form fields. Returns the issues that match no field. */
export function applyFieldIssues<T extends FieldValues>(
  setError: UseFormSetError<T>,
  issues: FieldIssue[],
  knownFields: readonly string[],
): FieldIssue[] {
  const unmapped: FieldIssue[] = []
  for (const issue of issues) {
    if (knownFields.includes(issue.field)) {
      setError(issue.field as Path<T>, { type: 'server', message: issue.reason })
    } else {
      unmapped.push(issue)
    }
  }
  return unmapped
}
