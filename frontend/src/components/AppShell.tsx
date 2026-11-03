import { NavLink, Outlet } from 'react-router'
import { baseApi } from '../api/baseApi'
import { useAppDispatch, useAppSelector } from '../app/hooks'
import { sessionCleared } from '../features/auth/authSlice'
import { useLogoutMutation } from '../features/auth/authApi'
import { ROLE_LABELS } from './format'

export function AppShell() {
  const user = useAppSelector((state) => state.auth.user)
  const dispatch = useAppDispatch()
  const [logout, { isLoading }] = useLogoutMutation()

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
    <div className="app-shell">
      <a className="skip-link" href="#main">
        Skip to content
      </a>
      <header className="app-header">
        <span className="brand">CampusHub</span>
        <nav aria-label="Main">
          <NavLink to="/projects">Projects</NavLink>
          <NavLink to="/change-password">Change password</NavLink>
        </nav>
        <div className="session">
          {user && (
            <span className="muted">
              {user.displayName} · {ROLE_LABELS[user.role]}
            </span>
          )}
          <button type="button" onClick={handleSignOut} disabled={isLoading}>
            Sign out
          </button>
        </div>
      </header>
      <main id="main" className="app-main" tabIndex={-1}>
        <Outlet />
      </main>
    </div>
  )
}
