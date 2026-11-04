import { useEffect, useId, useRef, useState, type KeyboardEvent } from 'react'
import { Link } from 'react-router'
import { ChevronDown, KeyRound, LogOut, UserRound } from 'lucide-react'
import type { UserSummary } from '../api/types'
import { initialsFor, ROLE_LABELS } from './format'

interface ProfileMenuProps {
  user: UserSummary
  signingOut: boolean
  onSignOut: () => void
}

// Account actions live here, not in the sidebar. Follows the ARIA menu pattern: arrow keys move
// between items, Escape and outside clicks close the menu, and focus returns to the trigger.
export function ProfileMenu({ user, signingOut, onSignOut }: ProfileMenuProps) {
  const [open, setOpen] = useState(false)
  const rootRef = useRef<HTMLDivElement>(null)
  const triggerRef = useRef<HTMLButtonElement>(null)
  const menuRef = useRef<HTMLDivElement>(null)
  const menuId = useId()

  useEffect(() => {
    if (!open) return

    function handlePointerDown(event: PointerEvent) {
      if (!rootRef.current?.contains(event.target as Node)) setOpen(false)
    }
    function handleEscape(event: globalThis.KeyboardEvent) {
      if (event.key !== 'Escape') return
      setOpen(false)
      triggerRef.current?.focus()
    }

    document.addEventListener('pointerdown', handlePointerDown)
    document.addEventListener('keydown', handleEscape)
    return () => {
      document.removeEventListener('pointerdown', handlePointerDown)
      document.removeEventListener('keydown', handleEscape)
    }
  }, [open])

  // Focus moves into the menu when it opens, so keyboard users land on the first action.
  useEffect(() => {
    if (open) menuRef.current?.querySelector<HTMLElement>('[role="menuitem"]')?.focus()
  }, [open])

  function items(): HTMLElement[] {
    return Array.from(menuRef.current?.querySelectorAll<HTMLElement>('[role="menuitem"]') ?? [])
  }

  function handleMenuKeyDown(event: KeyboardEvent<HTMLDivElement>) {
    const list = items()
    const index = list.indexOf(document.activeElement as HTMLElement)
    if (event.key === 'ArrowDown') {
      event.preventDefault()
      list[(index + 1) % list.length]?.focus()
    } else if (event.key === 'ArrowUp') {
      event.preventDefault()
      list[(index - 1 + list.length) % list.length]?.focus()
    } else if (event.key === 'Home') {
      event.preventDefault()
      list[0]?.focus()
    } else if (event.key === 'End') {
      event.preventDefault()
      list[list.length - 1]?.focus()
    } else if (event.key === 'Tab') {
      // Tab leaves the menu, so it closes rather than trapping focus.
      setOpen(false)
    }
  }

  function handleTriggerKeyDown(event: KeyboardEvent<HTMLButtonElement>) {
    if (event.key === 'ArrowDown') {
      event.preventDefault()
      setOpen(true)
    }
  }

  return (
    <div className="profile-menu" ref={rootRef}>
      <button
        type="button"
        ref={triggerRef}
        className="profile-trigger"
        aria-haspopup="menu"
        aria-expanded={open}
        aria-controls={menuId}
        aria-label={`Account menu for ${user.displayName}`}
        onClick={() => setOpen((current) => !current)}
        onKeyDown={handleTriggerKeyDown}
      >
        <span className="avatar" aria-hidden="true">
          {initialsFor(user.displayName)}
        </span>
        <ChevronDown className="profile-chevron" size={16} aria-hidden="true" />
      </button>

      {open && (
        <div
          id={menuId}
          ref={menuRef}
          className="profile-dropdown"
          role="menu"
          aria-label="Account"
          onKeyDown={handleMenuKeyDown}
        >
          <div className="profile-summary" role="none">
            <span className="profile-name">{user.displayName}</span>
            <span className="muted">{ROLE_LABELS[user.role]}</span>
          </div>
          <div className="profile-items" role="none">
            <Link role="menuitem" to="/profile" onClick={() => setOpen(false)}>
              <UserRound size={16} aria-hidden="true" />
              My profile
            </Link>
            <Link role="menuitem" to="/change-password" onClick={() => setOpen(false)}>
              <KeyRound size={16} aria-hidden="true" />
              Change password
            </Link>
          </div>
          <div className="profile-items" role="none">
            <button
              type="button"
              role="menuitem"
              className="menu-signout"
              disabled={signingOut}
              onClick={() => {
                setOpen(false)
                onSignOut()
              }}
            >
              <LogOut size={16} aria-hidden="true" />
              Sign out
            </button>
          </div>
        </div>
      )}
    </div>
  )
}
