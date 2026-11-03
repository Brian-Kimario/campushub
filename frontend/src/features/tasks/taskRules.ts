import type { TaskStatus } from '../../api/types'

// Legal transitions from STATE_MACHINES §3.2. The API refuses anything else with 409 INVALID_TRANSITION.
export const ALLOWED_TRANSITIONS: Record<TaskStatus, TaskStatus[]> = {
  TODO: ['IN_PROGRESS', 'BLOCKED'],
  IN_PROGRESS: ['IN_REVIEW', 'BLOCKED', 'TODO'],
  IN_REVIEW: ['DONE', 'IN_PROGRESS'],
  BLOCKED: ['IN_PROGRESS', 'TODO'],
  DONE: ['IN_PROGRESS'],
}

export const TRANSITION_LABELS: Record<TaskStatus, string> = {
  TODO: 'Reset to to do',
  IN_PROGRESS: 'Start',
  IN_REVIEW: 'Request review',
  BLOCKED: 'Block',
  DONE: 'Mark done',
}

// Completing or resetting work is confirmed first (UX_UI §7).
export function needsConfirmation(to: TaskStatus): boolean {
  return to === 'DONE' || to === 'TODO'
}

export function taskTone(status: TaskStatus): 'neutral' | 'success' | 'warning' | 'danger' {
  switch (status) {
    case 'DONE':
      return 'success'
    case 'IN_REVIEW':
      return 'warning'
    case 'BLOCKED':
      return 'danger'
    default:
      return 'neutral'
  }
}
