import {
  Award,
  Bell,
  CalendarDays,
  FolderKanban,
  Layers,
  LayoutDashboard,
  UserPlus,
  Users,
  type LucideIcon,
} from 'lucide-react'
import type { Role } from '../api/types'

// The sidebar routes work; the header and profile menu handle everything about the account.
// Items a role cannot use are left out entirely (UX_UI §3.1). The API still enforces every rule.
export interface NavItem {
  to: string
  label: string
  icon: LucideIcon
  roles: readonly Role[]
  badge?: 'notifications'
}

export interface NavGroup {
  label: string
  items: NavItem[]
}

const EVERY_ROLE: readonly Role[] = ['STUDENT', 'FACULTY', 'ADMIN']

export const NAV_GROUPS: NavGroup[] = [
  {
    label: 'Workspace',
    items: [
      { to: '/dashboard', label: 'Dashboard', icon: LayoutDashboard, roles: EVERY_ROLE },
      { to: '/projects', label: 'Projects', icon: FolderKanban, roles: EVERY_ROLE },
      { to: '/calendar', label: 'Calendar', icon: CalendarDays, roles: EVERY_ROLE },
      { to: '/notifications', label: 'Notifications', icon: Bell, roles: EVERY_ROLE, badge: 'notifications' },
      { to: '/invitations', label: 'Invitations', icon: UserPlus, roles: ['STUDENT'] },
    ],
  },
  {
    label: 'My development',
    items: [
      { to: '/skills', label: 'Skills', icon: Layers, roles: EVERY_ROLE },
      { to: '/certifications', label: 'Certifications', icon: Award, roles: ['STUDENT'] },
    ],
  },
  {
    label: 'Administration',
    items: [{ to: '/admin/users', label: 'Users', icon: Users, roles: ['ADMIN'] }],
  },
]

// Titles for the header breadcrumb. Routes not listed here fall back to the page's own heading.
export interface RouteMeta {
  section: string
  title: string
}

const ROUTE_META: { match: (path: string) => boolean; meta: RouteMeta }[] = [
  { match: (path) => path === '/dashboard', meta: { section: 'Workspace', title: 'Dashboard' } },
  { match: (path) => path === '/projects/new', meta: { section: 'Projects', title: 'New project' } },
  { match: (path) => /^\/projects\/[^/]+\/edit$/.test(path), meta: { section: 'Projects', title: 'Edit project' } },
  { match: (path) => /^\/projects\/[^/]+$/.test(path), meta: { section: 'Projects', title: 'Project' } },
  { match: (path) => path === '/projects', meta: { section: 'Workspace', title: 'Projects' } },
  { match: (path) => path === '/calendar', meta: { section: 'Workspace', title: 'Calendar' } },
  { match: (path) => path === '/notifications', meta: { section: 'Workspace', title: 'Notifications' } },
  { match: (path) => path === '/invitations', meta: { section: 'Workspace', title: 'Invitations' } },
  { match: (path) => path === '/skills', meta: { section: 'My development', title: 'Skills' } },
  { match: (path) => path === '/certifications', meta: { section: 'My development', title: 'Certifications' } },
  { match: (path) => path === '/admin/users', meta: { section: 'Administration', title: 'Users' } },
  { match: (path) => path === '/profile', meta: { section: 'Account', title: 'My profile' } },
  { match: (path) => path === '/change-password', meta: { section: 'Account', title: 'Change password' } },
]

export function routeMeta(path: string): RouteMeta | null {
  return ROUTE_META.find((entry) => entry.match(path))?.meta ?? null
}

export function navItemsFor(role: Role | undefined): NavGroup[] {
  if (!role) return []
  return NAV_GROUPS.map((group) => ({
    label: group.label,
    items: group.items.filter((item) => item.roles.includes(role)),
  })).filter((group) => group.items.length > 0)
}
