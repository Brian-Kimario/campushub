import { useEffect, useMemo, useRef, useState } from 'react'
import { NavLink, Outlet, useLocation } from 'react-router'
import { Bell, Menu } from 'lucide-react'
import { baseApi } from '../api/baseApi'
import { useAppDispatch, useAppSelector } from '../app/hooks'
import { sessionCleared } from '../features/auth/authSlice'
import { useLogoutMutation } from '../features/auth/authApi'
import { useGetProjectQuery } from '../features/projects/projectsApi'
import { useUnreadNotificationCountQuery } from '../features/notifications/notificationsApi'
import { ProfileMenu } from './ProfileMenu'
import { Sidebar, SIDEBAR_ID } from './Sidebar'
import { navItemsFor, projectIdFromPath, routeMeta } from './navigation'
import './shell.css'

const SIDEBAR_STORAGE_KEY = 'campushub.sidebarCollapsed'
// Below this width the sidebar starts compact so the workspace keeps its reading width.
const COMPACT_QUERY = '(max-width: 1099px)'

// The stored choice wins. Storage can be blocked, so a failed read falls back to the viewport default.
function readInitialCollapsed(): boolean {
  try {
    const stored = window.localStorage.getItem(SIDEBAR_STORAGE_KEY)
    if (stored === '1') return true
    if (stored === '0') return false
  } catch {
    // Storage is unavailable (private mode, blocked site data); the viewport default applies.
  }
  return window.matchMedia(COMPACT_QUERY).matches
}

function storeCollapsed(collapsed: boolean) {
  try {
    window.localStorage.setItem(SIDEBAR_STORAGE_KEY, collapsed ? '1' : '0')
  } catch {
    // The preference only lasts for this page load when storage is unavailable.
  }
}

export function AppShell() {
  const user = useAppSelector((state) => state.auth.user)
  const dispatch = useAppDispatch()
  const location = useLocation()
  const [logout, { isLoading: signingOut }] = useLogoutMutation()
  // Polls once a minute: notifications are database rows, not pushed (outbox and Kafka are deferred).
  const { data: unread } = useUnreadNotificationCountQuery(undefined, { pollingInterval: 60_000 })
  const unreadCount = unread?.count ?? 0
  // Shares the cache entry the project page reads, so the breadcrumb adds no extra request.
  const detailProjectId = projectIdFromPath(location.pathname) ?? ''
  const { data: detailProject } = useGetProjectQuery(detailProjectId, { skip: !detailProjectId })

  const [collapsed, setCollapsed] = useState(readInitialCollapsed)
  const [drawerOpen, setDrawerOpen] = useState(false)
  const menuButtonRef = useRef<HTMLButtonElement>(null)
  const drawerCloseRef = useRef<HTMLButtonElement>(null)

  const groups = useMemo(() => navItemsFor(user?.role), [user?.role])
  const meta = routeMeta(location.pathname, detailProject?.title)

  function toggleCollapsed() {
    setCollapsed((current) => {
      storeCollapsed(!current)
      return !current
    })
  }

  function closeDrawer() {
    setDrawerOpen(false)
    menuButtonRef.current?.focus()
  }

  // The drawer is the mobile form of the sidebar. Focus moves in on open, Escape closes it, and the page behind is inert.
  useEffect(() => {
    if (!drawerOpen) return
    drawerCloseRef.current?.focus()
    const previousOverflow = document.body.style.overflow
    document.body.style.overflow = 'hidden'

    function handleEscape(event: KeyboardEvent) {
      if (event.key === 'Escape') closeDrawer()
    }
    document.addEventListener('keydown', handleEscape)
    return () => {
      document.body.style.overflow = previousOverflow
      document.removeEventListener('keydown', handleEscape)
    }
  }, [drawerOpen])

  async function handleSignOut() {
    try {
      await logout().unwrap()
    } catch {
      // The local session ends either way, so a failed call to the server is not shown to the user.
    } finally {
      dispatch(baseApi.util.resetApiState())
      dispatch(sessionCleared())
    }
  }

  return (
    <div className="app-shell" data-collapsed={collapsed ? 'true' : undefined}>
      <a className="skip-link" href="#main">
        Skip to content
      </a>

      <Sidebar
        groups={groups}
        unreadCount={unreadCount}
        collapsed={collapsed}
        drawerOpen={drawerOpen}
        closeButtonRef={drawerCloseRef}
        onToggleCollapsed={toggleCollapsed}
        onCloseDrawer={closeDrawer}
      />

      <div className="app-workspace" inert={drawerOpen}>
        <header className="topbar">
          <button
            type="button"
            ref={menuButtonRef}
            className="icon-button topbar-menu"
            aria-controls={SIDEBAR_ID}
            aria-expanded={drawerOpen}
            aria-label="Open navigation"
            onClick={() => setDrawerOpen(true)}
          >
            <Menu size={20} aria-hidden="true" />
          </button>

          <div className="topbar-title">
            {meta && <p className="topbar-section">{meta.section}</p>}
            {meta && <p className="topbar-page">{meta.title}</p>}
          </div>

          <div className="topbar-actions">
            <NavLink
              to="/notifications"
              className="icon-button notify-link"
              aria-label={unreadCount > 0 ? `Notifications, ${unreadCount} unread` : 'Notifications'}
            >
              <Bell size={19} aria-hidden="true" />
              {unreadCount > 0 && <span className="notify-dot" aria-hidden="true" />}
            </NavLink>
            {user && <ProfileMenu user={user} signingOut={signingOut} onSignOut={handleSignOut} />}
          </div>
        </header>

        <main id="main" className="app-main" tabIndex={-1}>
          <Outlet />
        </main>
      </div>

      {drawerOpen && <div className="drawer-backdrop" aria-hidden="true" onClick={closeDrawer} />}
    </div>
  )
}
