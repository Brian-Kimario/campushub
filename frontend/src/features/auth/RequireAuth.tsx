import type { ReactNode } from 'react'
import { Navigate, useLocation } from 'react-router'
import { useAppSelector } from '../../app/hooks'

// UX only. The API enforces authentication on every request.
export function RequireAuth({ children }: { children: ReactNode }) {
  const user = useAppSelector((state) => state.auth.user)
  const location = useLocation()

  if (!user) {
    return <Navigate to="/login" replace state={{ from: location.pathname }} />
  }
  if (user.mustChangePassword && location.pathname !== '/change-password') {
    return <Navigate to="/change-password" replace />
  }
  return children
}
