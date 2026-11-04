import type { RefObject } from 'react'
import { NavLink } from 'react-router'
import { ChevronsLeft, ChevronsRight, X } from 'lucide-react'
import type { NavGroup } from './navigation'

export const SIDEBAR_ID = 'app-sidebar'

interface SidebarProps {
  groups: NavGroup[]
  unreadCount: number
  collapsed: boolean
  drawerOpen: boolean
  closeButtonRef: RefObject<HTMLButtonElement | null>
  onToggleCollapsed: () => void
  onCloseDrawer: () => void
}

export function Sidebar({
  groups,
  unreadCount,
  collapsed,
  drawerOpen,
  closeButtonRef,
  onToggleCollapsed,
  onCloseDrawer,
}: SidebarProps) {
  return (
    <aside id={SIDEBAR_ID} className="sidebar" data-open={drawerOpen ? 'true' : undefined} aria-label="Primary">
      <div className="sidebar-head">
        <span className="brand-mark" aria-hidden="true">
          CH
        </span>
        <span className="brand-name">CampusHub</span>
        <button
          type="button"
          ref={closeButtonRef}
          className="icon-button drawer-close"
          onClick={onCloseDrawer}
          aria-label="Close navigation"
        >
          <X size={18} aria-hidden="true" />
        </button>
      </div>

      <nav className="sidebar-nav" aria-label="Main">
        {groups.map((group) => (
          <div className="nav-group" key={group.label}>
            <h2 className="nav-group-label">{group.label}</h2>
            <ul>
              {group.items.map((item) => {
                const Icon = item.icon
                const showCount = item.badge === 'notifications' && unreadCount > 0
                return (
                  <li key={item.to}>
                    <NavLink to={item.to} className="nav-link" onClick={onCloseDrawer}>
                      <Icon className="nav-icon" size={18} strokeWidth={1.75} aria-hidden="true" />
                      <span className="nav-label">{item.label}</span>
                      {showCount && (
                        <span className="nav-count">
                          <span className="sr-only">{unreadCount} unread, </span>
                          {unreadCount > 99 ? '99+' : unreadCount}
                        </span>
                      )}
                      {collapsed && (
                        <span className="nav-tooltip" aria-hidden="true">
                          {item.label}
                          {showCount && ` · ${unreadCount} unread`}
                        </span>
                      )}
                    </NavLink>
                  </li>
                )
              })}
            </ul>
          </div>
        ))}
      </nav>

      <div className="sidebar-foot">
        <button
          type="button"
          className="collapse-toggle"
          onClick={onToggleCollapsed}
          aria-controls={SIDEBAR_ID}
          aria-expanded={!collapsed}
          aria-label={collapsed ? 'Expand sidebar' : 'Collapse sidebar'}
        >
          {collapsed ? <ChevronsRight size={18} aria-hidden="true" /> : <ChevronsLeft size={18} aria-hidden="true" />}
          <span className="collapse-label">{collapsed ? 'Expand' : 'Collapse'}</span>
        </button>
      </div>
    </aside>
  )
}
