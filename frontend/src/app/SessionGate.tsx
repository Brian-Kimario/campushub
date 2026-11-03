import { useEffect, type ReactNode } from 'react'
import { refreshSession } from '../api/session'
import { PageLoading } from '../components/ui'
import { sessionChecked, sessionStarted } from '../features/auth/authSlice'
import { useAppDispatch, useAppSelector } from './hooks'

// Restores the session once per page load, before any route guard runs, so a reload keeps the user signed in.
export function SessionGate({ children }: { children: ReactNode }) {
  const initialized = useAppSelector((state) => state.auth.initialized)
  const dispatch = useAppDispatch()

  useEffect(() => {
    refreshSession().then((session) => {
      if (session) {
        dispatch(sessionStarted({ accessToken: session.accessToken, user: session.user }))
      }
      dispatch(sessionChecked())
    })
  }, [dispatch])

  if (!initialized) {
    return <PageLoading />
  }
  return children
}
