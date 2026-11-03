import { useState, type FormEvent } from 'react'
import { describeError } from '../../api/problem'
import type { Invitation, InvitationStatus, Member, Project } from '../../api/types'
import { formatInstant } from '../../components/format'
import { Badge, ConfirmDialog, ErrorBanner, ErrorState, PageLoading } from '../../components/ui'
import {
  useInviteMemberMutation,
  useListMembersQuery,
  useListProjectInvitationsQuery,
  useRemoveMemberMutation,
} from './membershipApi'

const INVITATION_LABELS: Record<InvitationStatus, string> = {
  PENDING: 'Pending',
  ACCEPTED: 'Accepted',
  DECLINED: 'Declined',
  EXPIRED: 'Expired',
}

const INVITATION_TONES: Record<InvitationStatus, 'neutral' | 'success' | 'warning' | 'danger'> = {
  PENDING: 'warning',
  ACCEPTED: 'success',
  DECLINED: 'neutral',
  EXPIRED: 'danger',
}

// Team size includes the owner, so a full team shows five people (STATE_MACHINES §6).
const TEAM_LIMIT = 5

export function TeamPanel({ project, isOwner }: { project: Project; isOwner: boolean }) {
  const isActive = project.status === 'ACTIVE'
  const members = useListMembersQuery(project.id)
  const invitations = useListProjectInvitationsQuery({ projectId: project.id, page: 0, size: 20 }, { skip: !isOwner })

  if (members.isLoading) return <PageLoading />
  if (members.error) {
    return <ErrorState message={describeError(members.error).message} onRetry={members.refetch} />
  }

  const memberCount = members.data?.length ?? 0

  return (
    <section className="task-panel" aria-labelledby="team-heading">
      <h2 id="team-heading">Team</h2>
      <p className="muted">
        {memberCount} of {TEAM_LIMIT} people on this team.
      </p>

      <MemberList members={members.data ?? []} projectId={project.id} canRemove={isOwner && isActive} />

      {isOwner && isActive && memberCount < TEAM_LIMIT && <InviteForm projectId={project.id} />}
      {isOwner && isActive && memberCount >= TEAM_LIMIT && (
        <p className="muted">The team is full. A member must leave before another person can be invited.</p>
      )}
      {isOwner && !isActive && <p className="muted">The team cannot change while the project is archived.</p>}

      {isOwner && (
        <InvitationList
          invitations={invitations.data?.content ?? []}
          loading={invitations.isLoading}
          error={invitations.error}
          onRetry={invitations.refetch}
        />
      )}
    </section>
  )
}

function MemberList({ members, projectId, canRemove }: { members: Member[]; projectId: string; canRemove: boolean }) {
  const [removeMember, { isLoading: removing, error: removeError }] = useRemoveMemberMutation()
  const [target, setTarget] = useState<Member | null>(null)

  async function confirmRemove() {
    if (!target) return
    try {
      await removeMember({ projectId, userId: target.userId }).unwrap()
    } finally {
      setTarget(null)
    }
  }

  return (
    <>
      {removeError && <ErrorBanner message={describeError(removeError).message} />}
      <ul className="dashboard-list" aria-label="Team members">
        {members.map((member) => (
          <li key={member.userId}>
            <span>{member.displayName ?? 'Unknown person'}</span>{' '}
            <Badge tone={member.role === 'OWNER' ? 'success' : 'neutral'}>
              {member.role === 'OWNER' ? 'Owner' : 'Member'}
            </Badge>
            {canRemove && member.role === 'MEMBER' && (
              <button type="button" onClick={() => setTarget(member)} aria-label={`Remove ${member.displayName ?? 'member'}`}>
                Remove
              </button>
            )}
          </li>
        ))}
      </ul>
      <ConfirmDialog
        open={target !== null}
        title="Remove team member?"
        message={`${target?.displayName ?? 'This person'} will lose access to the project and its tasks right away.`}
        confirmLabel="Remove"
        danger
        busy={removing}
        onConfirm={() => void confirmRemove()}
        onCancel={() => setTarget(null)}
      />
    </>
  )
}

function InviteForm({ projectId }: { projectId: string }) {
  const [email, setEmail] = useState('')
  const [invite, { isLoading, error, isSuccess, reset }] = useInviteMemberMutation()

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const inviteeEmail = email.trim()
    if (!inviteeEmail) return
    try {
      await invite({ projectId, inviteeEmail }).unwrap()
      setEmail('')
    } catch {
      // The error banner below shows the failure.
    }
  }

  return (
    <form className="block-form" onSubmit={(event) => void handleSubmit(event)} aria-label="Invite a team member">
      <h3>Invite a student</h3>
      <div className="field">
        <label htmlFor="invite-email">Student email</label>
        <input
          id="invite-email"
          type="email"
          autoComplete="off"
          maxLength={254}
          value={email}
          onChange={(event) => {
            setEmail(event.target.value)
            reset()
          }}
        />
      </div>
      {error && <ErrorBanner message={describeError(error).message} />}
      {isSuccess && (
        <p className="banner banner-info" role="status">
          Invitation sent. It expires in 14 days.
        </p>
      )}
      <button type="submit" disabled={isLoading || email.trim() === ''}>
        {isLoading ? 'Sending…' : 'Send invitation'}
      </button>
    </form>
  )
}

function InvitationList({
  invitations,
  loading,
  error,
  onRetry,
}: {
  invitations: Invitation[]
  loading: boolean
  error: unknown
  onRetry: () => void
}) {
  if (loading) return <PageLoading />
  if (error) return <ErrorState message={describeError(error).message} onRetry={onRetry} />
  if (invitations.length === 0) return null

  return (
    <div>
      <h3>Invitations</h3>
      <ul className="dashboard-list" aria-label="Invitations for this project">
        {invitations.map((invitation) => (
          <li key={invitation.id}>
            <span>{invitation.invitee?.displayName ?? 'Invitee'}</span>{' '}
            <Badge tone={INVITATION_TONES[invitation.status]}>{INVITATION_LABELS[invitation.status]}</Badge>{' '}
            <span className="muted">expires {formatInstant(invitation.expiresAt)}</span>
          </li>
        ))}
      </ul>
    </div>
  )
}
