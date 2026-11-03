import { useState } from 'react'
import { Link } from 'react-router'
import { describeError } from '../../api/problem'
import type { Invitation } from '../../api/types'
import { formatInstant } from '../../components/format'
import { EmptyState, ErrorBanner, ErrorState, PageLoading } from '../../components/ui'
import { useAcceptInvitationMutation, useDeclineInvitationMutation, useListMyInvitationsQuery } from '../projects/membershipApi'

const PAGE_SIZE = 20

export default function InvitationsPage() {
  const [page, setPage] = useState(0)
  const { data, error, isLoading, refetch } = useListMyInvitationsQuery({ page, size: PAGE_SIZE })

  if (isLoading) return <PageLoading />
  if (error) return <ErrorState message={describeError(error).message} onRetry={refetch} />
  if (!data) return null

  return (
    <section>
      <div className="page-header">
        <h1>Team invitations</h1>
      </div>
      {data.content.length === 0 ? (
        <EmptyState title="No invitations">
          <p className="muted">When a classmate invites you to their project team, it appears here.</p>
        </EmptyState>
      ) : (
        <ul className="project-list" aria-label="Your invitations">
          {data.content.map((invitation) => (
            <InvitationCard key={invitation.id} invitation={invitation} />
          ))}
        </ul>
      )}
      {data.totalPages > 1 && (
        <nav className="pager" aria-label="Invitation pages">
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

function InvitationCard({ invitation }: { invitation: Invitation }) {
  const [accept, accepting] = useAcceptInvitationMutation()
  const [decline, declining] = useDeclineInvitationMutation()
  const failure = accepting.error ?? declining.error
  const busy = accepting.isLoading || declining.isLoading
  const isPending = invitation.status === 'PENDING'

  return (
    <li className="project-card">
      <h2>{invitation.projectTitle ?? 'Project'}</h2>
      <p className="muted">
        Invited by {invitation.inviter?.displayName ?? 'a team owner'} · {invitation.status.toLowerCase()}
        {isPending && ` · expires ${formatInstant(invitation.expiresAt)}`}
      </p>
      {failure && <ErrorBanner message={describeError(failure).message} />}
      {isPending && (
        <div className="actions">
          <button type="button" onClick={() => void accept(invitation.id)} disabled={busy}>
            Accept
          </button>
          <button type="button" onClick={() => void decline(invitation.id)} disabled={busy}>
            Decline
          </button>
        </div>
      )}
      {invitation.status === 'ACCEPTED' && <Link to={`/projects/${invitation.projectId}`}>Open project</Link>}
    </li>
  )
}
