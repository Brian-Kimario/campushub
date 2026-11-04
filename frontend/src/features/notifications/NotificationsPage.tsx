import { useState } from 'react'
import { Link } from 'react-router'
import { describeError } from '../../api/problem'
import type { Notification } from '../../api/types'
import { formatInstant } from '../../components/format'
import { EmptyState, ErrorBanner, ErrorState, PageLoading } from '../../components/ui'
import {
  useListNotificationsQuery,
  useMarkAllNotificationsReadMutation,
  useMarkNotificationReadMutation,
} from './notificationsApi'

const PAGE_SIZE = 20

export default function NotificationsPage() {
  const [page, setPage] = useState(0)
  const [unreadOnly, setUnreadOnly] = useState(false)
  const { data, error, isLoading, refetch } = useListNotificationsQuery({ unreadOnly, page, size: PAGE_SIZE })
  const [markRead, marking] = useMarkNotificationReadMutation()
  const [markAll, markingAll] = useMarkAllNotificationsReadMutation()
  const failure = marking.error ?? markingAll.error

  if (isLoading) return <PageLoading />
  if (error) return <ErrorState message={describeError(error).message} onRetry={refetch} />
  if (!data) return null

  return (
    <section>
      <div className="page-header">
        <h1>Notifications</h1>
        <button type="button" onClick={() => markAll()} disabled={markingAll.isLoading}>
          Mark all as read
        </button>
      </div>
      {failure && <ErrorBanner message={describeError(failure).message} requestId={describeError(failure).requestId} />}
      <label className="checkbox-row">
        <input
          type="checkbox"
          checked={unreadOnly}
          onChange={(event) => {
            setUnreadOnly(event.target.checked)
            setPage(0)
          }}
        />
        Show unread only
      </label>
      {data.content.length === 0 ? (
        <EmptyState title={unreadOnly ? 'No unread notifications' : 'No notifications yet'}>
          <p className="muted">Team invitations and membership changes appear here.</p>
        </EmptyState>
      ) : (
        <ul className="notification-list" aria-label="Your notifications">
          {data.content.map((notification) => (
            <NotificationRow
              key={notification.id}
              notification={notification}
              onMarkRead={() => markRead(notification.id)}
              busy={marking.isLoading}
            />
          ))}
        </ul>
      )}
      {data.totalPages > 1 && (
        <nav className="pager" aria-label="Notification pages">
          <button type="button" onClick={() => setPage((current) => current - 1)} disabled={page === 0}>
            Previous
          </button>
          <span>
            Page {page + 1} of {data.totalPages}
          </span>
          <button type="button" onClick={() => setPage((current) => current + 1)} disabled={page + 1 >= data.totalPages}>
            Next
          </button>
        </nav>
      )}
    </section>
  )
}

function NotificationRow({
  notification,
  onMarkRead,
  busy,
}: {
  notification: Notification
  onMarkRead: () => void
  busy: boolean
}) {
  const isUnread = !notification.readAt
  return (
    <li className={isUnread ? 'notification-item is-unread' : 'notification-item'}>
      <p className={isUnread ? 'notification-message is-unread' : 'notification-message'}>
        {isUnread && <span className="sr-only">Unread: </span>}
        {notification.message}
      </p>
      <p className="muted notification-time">
        {formatInstant(notification.createdAt)} · {isUnread ? 'Unread' : `Read ${formatInstant(notification.readAt)}`}
      </p>
      <div className="row-actions">
        {notification.projectId && notification.type !== 'MEMBER_REMOVED' && (
          <Link to={`/projects/${notification.projectId}`}>Open project</Link>
        )}
        {isUnread && (
          <button type="button" onClick={onMarkRead} disabled={busy}>
            Mark as read
          </button>
        )}
      </div>
    </li>
  )
}
