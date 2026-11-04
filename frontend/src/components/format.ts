import type { ProjectStatus, Role, SubmissionStatus, TaskPriority, TaskStatus } from '../api/types'

// LocalDate values have no zone, so they are built from parts to avoid a one-day shift.
export function formatDate(localDate: string | undefined): string {
  if (!localDate) return '—'
  const [year, month, day] = localDate.split('-').map(Number)
  return new Date(year, month - 1, day).toLocaleDateString(undefined, { dateStyle: 'medium' })
}

// Instants come from the API in UTC and are shown in the browser's zone.
export function formatInstant(instant: string | undefined): string {
  if (!instant) return '—'
  return new Date(instant).toLocaleString(undefined, { dateStyle: 'medium', timeStyle: 'short' })
}

export function emptyToNull(value: string | undefined): string | null {
  const trimmed = value?.trim() ?? ''
  return trimmed === '' ? null : trimmed
}

export const ROLE_LABELS: Record<Role, string> = {
  STUDENT: 'Student',
  FACULTY: 'Faculty',
  ADMIN: 'Admin',
}

export const PROJECT_STATUS_LABELS: Record<ProjectStatus, string> = {
  ACTIVE: 'Active',
  ARCHIVED: 'Archived',
}

export const TASK_STATUS_LABELS: Record<TaskStatus, string> = {
  TODO: 'To do',
  IN_PROGRESS: 'In progress',
  IN_REVIEW: 'In review',
  BLOCKED: 'Blocked',
  DONE: 'Done',
}

export const TASK_PRIORITY_LABELS: Record<TaskPriority, string> = {
  LOW: 'Low',
  MEDIUM: 'Medium',
  HIGH: 'High',
}

export const SUBMISSION_STATUS_LABELS: Record<SubmissionStatus, string> = {
  SUBMITTED: 'Submitted',
  UNDER_REVIEW: 'Under review',
  CHANGES_REQUESTED: 'Changes requested',
  ACCEPTED: 'Accepted',
}

// Initials are the only avatar source the API provides. They fall back to "?" for an empty name.
export function initialsFor(displayName: string): string {
  const parts = displayName.trim().split(/\s+/).filter(Boolean)
  if (parts.length === 0) return '?'
  const first = parts[0][0]
  const last = parts.length > 1 ? parts[parts.length - 1][0] : ''
  return (first + last).toUpperCase()
}
