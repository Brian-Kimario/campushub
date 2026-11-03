import { lazy } from 'react'

// Route-level code splitting (ARCHITECTURE §9): each page loads on first visit.
// Kept apart from router.tsx so that file exports only the router.
export const LoginPage = lazy(() => import('../features/auth/LoginPage'))
export const ChangePasswordPage = lazy(() => import('../features/auth/ChangePasswordPage'))
export const ProjectListPage = lazy(() => import('../features/projects/ProjectListPage'))
export const ProjectDetailPage = lazy(() => import('../features/projects/ProjectDetailPage'))
export const ProjectFormPage = lazy(() => import('../features/projects/ProjectFormPage'))
export const SkillsPage = lazy(() => import('../features/skills/SkillsPage'))
