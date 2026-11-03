import { Suspense, type ReactNode } from 'react'
import { createBrowserRouter, Navigate } from 'react-router'
import { AppShell } from '../components/AppShell'
import { PageLoading } from '../components/ui'
import { RequireAuth } from '../features/auth/RequireAuth'
import {
  CertificationsPage,
  ChangePasswordPage,
  DashboardPage,
  LoginPage,
  ProjectDetailPage,
  ProjectFormPage,
  ProjectListPage,
  SkillsPage,
} from './lazyPages'

function lazyPage(page: ReactNode) {
  return <Suspense fallback={<PageLoading />}>{page}</Suspense>
}

// These guards are UX only; the API enforces every rule (ARCHITECTURE §9).
export const router = createBrowserRouter([
  { path: '/login', element: lazyPage(<LoginPage />) },
  {
    element: (
      <RequireAuth>
        <AppShell />
      </RequireAuth>
    ),
    children: [
      { index: true, element: <Navigate to="/projects" replace /> },
      { path: 'dashboard', element: lazyPage(<DashboardPage />) },
      { path: 'change-password', element: lazyPage(<ChangePasswordPage />) },
      { path: 'projects', element: lazyPage(<ProjectListPage />) },
      { path: 'projects/new', element: lazyPage(<ProjectFormPage />) },
      { path: 'projects/:projectId', element: lazyPage(<ProjectDetailPage />) },
      { path: 'projects/:projectId/edit', element: lazyPage(<ProjectFormPage />) },
      { path: 'skills', element: lazyPage(<SkillsPage />) },
      { path: 'certifications', element: lazyPage(<CertificationsPage />) },
      { path: '*', element: <Navigate to="/projects" replace /> },
    ],
  },
])
