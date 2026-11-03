import { useEffect, useId, useRef, type ReactNode } from 'react'

// Shared state blocks (UX_UI §4): loading, empty, error.

export function PageLoading() {
  return <p className="state" role="status">Loading…</p>
}

export function ErrorState({ message, onRetry }: { message: string; onRetry?: () => void }) {
  return (
    <div className="state state-error" role="alert">
      <p>{message}</p>
      {onRetry && (
        <button type="button" onClick={onRetry}>
          Try again
        </button>
      )}
    </div>
  )
}

export function EmptyState({ title, children }: { title: string; children?: ReactNode }) {
  return (
    <div className="state">
      <h2>{title}</h2>
      {children}
    </div>
  )
}

export function ErrorBanner({ message, requestId }: { message: string; requestId?: string | null }) {
  return (
    <div className="banner banner-error" role="alert">
      <p>{message}</p>
      {requestId && <p className="muted">Reference: {requestId}</p>}
    </div>
  )
}

export function Badge({
  children,
  tone = 'neutral',
}: {
  children: ReactNode
  tone?: 'neutral' | 'success' | 'warning' | 'danger'
}) {
  return <span className={`badge badge-${tone}`}>{children}</span>
}

// Shows a counter once the value reaches 80% of the limit (UX_UI §6).
export function CharCount({ value, max }: { value: string; max: number }) {
  const length = value.length
  if (length < max * 0.8) return null
  return (
    <span className={length > max ? 'char-count char-count-over' : 'char-count'} aria-live="polite">
      {length}/{max}
    </span>
  )
}

// Native dialog. Cancel has focus by default so Enter does not confirm a destructive action (UX_UI §7).
export function ConfirmDialog({
  open,
  title,
  message,
  confirmLabel,
  danger = false,
  busy = false,
  onConfirm,
  onCancel,
}: {
  open: boolean
  title: string
  message: string
  confirmLabel: string
  danger?: boolean
  busy?: boolean
  onConfirm: () => void
  onCancel: () => void
}) {
  const ref = useRef<HTMLDialogElement>(null)
  const titleId = useId()

  useEffect(() => {
    const dialog = ref.current
    if (!dialog) return
    if (open && !dialog.open) dialog.showModal()
    if (!open && dialog.open) dialog.close()
  }, [open])

  return (
    <dialog
      ref={ref}
      className="confirm"
      aria-labelledby={titleId}
      onCancel={(event) => {
        event.preventDefault()
        onCancel()
      }}
    >
      <h2 id={titleId}>{title}</h2>
      <p>{message}</p>
      <div className="actions">
        <button type="button" autoFocus onClick={onCancel} disabled={busy}>
          Cancel
        </button>
        <button type="button" className={danger ? 'danger' : 'primary'} onClick={onConfirm} disabled={busy}>
          {confirmLabel}
        </button>
      </div>
    </dialog>
  )
}
