import { useState, type FormEvent } from 'react'
import { describeError } from '../../api/problem'
import type { Role } from '../../api/types'
import { useAppSelector } from '../../app/hooks'
import { ROLE_LABELS } from '../../components/format'
import { CharCount, ConfirmDialog, EmptyState, ErrorBanner, ErrorState, PageLoading } from '../../components/ui'
import { useListActiveDepartmentsQuery } from '../profile/profileApi'
import {
  useCreateAdminUserMutation,
  useDeactivateAdminUserMutation,
  useListAdminUsersQuery,
  useReactivateAdminUserMutation,
  useResetAdminUserPasswordMutation,
  type AccountStatus,
  type AdminUser,
} from './adminUsersApi'

const PAGE_SIZE = 20
const ROLES: Role[] = ['STUDENT', 'FACULTY', 'ADMIN']
const MAX_NAME = 100
const MAX_EMAIL = 255

type PendingAction = 'deactivate' | 'reactivate' | 'reset'

interface Issued {
  email: string
  password: string
  reason: 'created' | 'reset'
}

// The API enforces ADMIN on /admin/**; this check only keeps the page from rendering for other roles.
export default function AdminUsersPage() {
  const role = useAppSelector((state) => state.auth.user?.role)
  if (role !== 'ADMIN') {
    return (
      <EmptyState title="Administrators only">
        <p className="muted">User administration is available to administrator accounts.</p>
      </EmptyState>
    )
  }
  return <UserDirectory />
}

function UserDirectory() {
  const me = useAppSelector((state) => state.auth.user)
  const [role, setRole] = useState<Role | ''>('')
  const [status, setStatus] = useState<AccountStatus | ''>('')
  const [searchDraft, setSearchDraft] = useState('')
  const [q, setQ] = useState('')
  const [page, setPage] = useState(0)
  const [issued, setIssued] = useState<Issued | null>(null)
  const [actionError, setActionError] = useState<string | null>(null)
  const [pending, setPending] = useState<{ user: AdminUser; action: PendingAction } | null>(null)

  const { data, error, isLoading, isFetching, refetch } = useListAdminUsersQuery({
    role,
    status,
    q,
    page,
    size: PAGE_SIZE,
  })
  const [deactivate] = useDeactivateAdminUserMutation()
  const [reactivate] = useReactivateAdminUserMutation()
  const [resetPassword] = useResetAdminUserPasswordMutation()

  function changeRole(next: Role | '') {
    setRole(next)
    setPage(0)
  }

  function changeStatus(next: AccountStatus | '') {
    setStatus(next)
    setPage(0)
  }

  function applySearch(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setQ(searchDraft.trim().toLowerCase())
    setPage(0)
  }

  async function confirmPending() {
    if (!pending) return
    const { user, action } = pending
    setPending(null)
    setActionError(null)
    try {
      if (action === 'deactivate') {
        await deactivate(user.id).unwrap()
      } else if (action === 'reactivate') {
        await reactivate(user.id).unwrap()
      } else {
        const result = await resetPassword(user.id).unwrap()
        setIssued({ email: result.email, password: result.temporaryPassword ?? '', reason: 'reset' })
      }
    } catch (err) {
      setActionError(describeError(err).message)
    }
  }

  return (
    <section>
      <div className="page-header">
        <h1>User administration</h1>
      </div>

      {issued && (
        <section aria-labelledby="issued-heading" className="notice" role="status">
          <h2 id="issued-heading">
            {issued.reason === 'created' ? 'Account created' : 'Password reset'} for {issued.email}
          </h2>
          <p>
            Temporary password: <code>{issued.password}</code>
          </p>
          <p className="muted">
            Share it once, through a channel you trust. It is not shown again, and the user must change it at their next
            sign-in.
          </p>
          <button type="button" onClick={() => setIssued(null)}>
            Dismiss
          </button>
        </section>
      )}

      <section aria-labelledby="directory-heading">
        <h2 id="directory-heading">Directory</h2>

        <div className="filters">
          <div className="field">
            <label htmlFor="filter-role">Role</label>
            <select id="filter-role" value={role} onChange={(event) => changeRole(event.target.value as Role | '')}>
              <option value="">All roles</option>
              {ROLES.map((value) => (
                <option key={value} value={value}>
                  {ROLE_LABELS[value]}
                </option>
              ))}
            </select>
          </div>
          <div className="field">
            <label htmlFor="filter-status">Status</label>
            <select
              id="filter-status"
              value={status}
              onChange={(event) => changeStatus(event.target.value as AccountStatus | '')}
            >
              <option value="">All statuses</option>
              <option value="ACTIVE">Active</option>
              <option value="DEACTIVATED">Deactivated</option>
            </select>
          </div>
          <form onSubmit={applySearch} role="search" aria-label="Find users">
            <div className="field">
              <label htmlFor="filter-search">Name or email starts with</label>
              <input
                id="filter-search"
                type="search"
                value={searchDraft}
                maxLength={100}
                onChange={(event) => setSearchDraft(event.target.value)}
              />
            </div>
            <button type="submit">Search</button>
          </form>
        </div>

        {actionError && <ErrorBanner message={actionError} />}

        {isLoading && <PageLoading />}
        {error && !data && <ErrorState message={describeError(error).message} onRetry={refetch} />}

        {data && data.content.length === 0 && (
          <EmptyState title="No users match these filters">
            <p className="muted">Change the role, status or search text to see other accounts.</p>
          </EmptyState>
        )}

        {data && data.content.length > 0 && (
          <>
            <div className="table-scroll">
              <table className="admin-users">
                <caption className="sr-only">Accounts matching the current filters</caption>
                <thead>
                  <tr>
                    <th scope="col">Name</th>
                    <th scope="col">Email</th>
                    <th scope="col">Role</th>
                    <th scope="col">Status</th>
                    <th scope="col">Actions</th>
                  </tr>
                </thead>
                <tbody>
                  {data.content.map((user) => {
                    const self = me?.id === user.id
                    return (
                      <tr key={user.id}>
                        <td>
                          {user.displayName}
                          {user.mustChangePassword && <span className="muted"> · must change password</span>}
                        </td>
                        <td>{user.email}</td>
                        <td>{ROLE_LABELS[user.role]}</td>
                        <td>{user.status === 'ACTIVE' ? 'Active' : 'Deactivated'}</td>
                        <td className="row-actions">
                          {user.status === 'ACTIVE' ? (
                            <button
                              type="button"
                              className="danger"
                              disabled={self}
                              title={self ? 'You cannot deactivate your own account' : undefined}
                              onClick={() => setPending({ user, action: 'deactivate' })}
                            >
                              Deactivate<span className="sr-only"> {user.displayName}</span>
                            </button>
                          ) : (
                            <button type="button" onClick={() => setPending({ user, action: 'reactivate' })}>
                              Reactivate<span className="sr-only"> {user.displayName}</span>
                            </button>
                          )}
                          <button type="button" onClick={() => setPending({ user, action: 'reset' })}>
                            Reset password<span className="sr-only"> for {user.displayName}</span>
                          </button>
                        </td>
                      </tr>
                    )
                  })}
                </tbody>
              </table>
            </div>
            <nav className="pager" aria-label="Users pagination">
              <button type="button" onClick={() => setPage(page - 1)} disabled={page === 0 || isFetching}>
                Previous
              </button>
              <span>
                Page {data.page + 1} of {Math.max(data.totalPages, 1)}
              </span>
              <button
                type="button"
                onClick={() => setPage(page + 1)}
                disabled={page + 1 >= data.totalPages || isFetching}
              >
                Next
              </button>
            </nav>
          </>
        )}
      </section>

      <CreateUserForm onCreated={setIssued} />

      <ConfirmDialog
        open={pending !== null}
        title={pending ? confirmTitle(pending.action, pending.user) : ''}
        message={pending ? confirmMessage(pending.action, pending.user) : ''}
        confirmLabel={pending ? confirmLabel(pending.action) : ''}
        danger={pending?.action === 'deactivate'}
        onConfirm={() => void confirmPending()}
        onCancel={() => setPending(null)}
      />
    </section>
  )
}

function confirmTitle(action: PendingAction, user: AdminUser) {
  if (action === 'deactivate') return `Deactivate ${user.displayName}?`
  if (action === 'reactivate') return `Reactivate ${user.displayName}?`
  return `Reset password for ${user.displayName}?`
}

function confirmMessage(action: PendingAction, user: AdminUser) {
  if (action === 'deactivate') {
    return `${user.email} will be signed out immediately and cannot sign in until reactivated.`
  }
  if (action === 'reactivate') return `${user.email} will be able to sign in again.`
  return `A new temporary password will be issued. Existing sessions for ${user.email} will end.`
}

function confirmLabel(action: PendingAction) {
  if (action === 'deactivate') return 'Deactivate'
  if (action === 'reactivate') return 'Reactivate'
  return 'Reset password'
}

function CreateUserForm({ onCreated }: { onCreated: (issued: Issued) => void }) {
  const [email, setEmail] = useState('')
  const [displayName, setDisplayName] = useState('')
  const [newRole, setNewRole] = useState<Role>('STUDENT')
  const [institutionalId, setInstitutionalId] = useState('')
  const [departmentId, setDepartmentId] = useState('')
  const [issue, setIssue] = useState<string | null>(null)
  const [createUser, { isLoading }] = useCreateAdminUserMutation()
  const departments = useListActiveDepartmentsQuery()

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const cleanEmail = email.trim()
    const cleanName = displayName.trim().replace(/\s+/g, ' ')
    if (cleanEmail.length === 0 || cleanEmail.length > MAX_EMAIL || !cleanEmail.includes('@')) {
      setIssue('Enter a valid email address.')
      return
    }
    if (cleanName.length < 2 || cleanName.length > MAX_NAME) {
      setIssue(`Display name must be 2 to ${MAX_NAME} characters.`)
      return
    }
    try {
      const created = await createUser({
        email: cleanEmail,
        displayName: cleanName,
        role: newRole,
        institutionalId: institutionalId.trim() || null,
        departmentId: departmentId || null,
      }).unwrap()
      onCreated({ email: created.email, password: created.temporaryPassword ?? '', reason: 'created' })
      setEmail('')
      setDisplayName('')
      setInstitutionalId('')
      setDepartmentId('')
      setNewRole('STUDENT')
      setIssue(null)
    } catch (err) {
      setIssue(describeError(err).message)
    }
  }

  return (
    <section className="create-user" aria-labelledby="create-user-heading">
      <h2 id="create-user-heading">Create an account</h2>
      <form onSubmit={handleSubmit} noValidate>
        <div className="field">
          <label htmlFor="new-email">Email</label>
          <input
            id="new-email"
            type="email"
            value={email}
            maxLength={MAX_EMAIL}
            onChange={(event) => setEmail(event.target.value)}
          />
        </div>
        <div className="field">
          <label htmlFor="new-name">Display name</label>
          <input
            id="new-name"
            value={displayName}
            maxLength={MAX_NAME}
            onChange={(event) => setDisplayName(event.target.value)}
          />
          <CharCount value={displayName} max={MAX_NAME} />
        </div>
        <div className="field">
          <label htmlFor="new-role">Role</label>
          <select id="new-role" value={newRole} onChange={(event) => setNewRole(event.target.value as Role)}>
            {ROLES.map((value) => (
              <option key={value} value={value}>
                {ROLE_LABELS[value]}
              </option>
            ))}
          </select>
        </div>
        <div className="field">
          <label htmlFor="new-institutional">Institutional ID (optional)</label>
          <input
            id="new-institutional"
            value={institutionalId}
            maxLength={32}
            onChange={(event) => setInstitutionalId(event.target.value)}
          />
        </div>
        <div className="field">
          <label htmlFor="new-department">Department (optional)</label>
          <select id="new-department" value={departmentId} onChange={(event) => setDepartmentId(event.target.value)}>
            <option value="">No department</option>
            {(departments.data?.content ?? []).map((department) => (
              <option key={department.id} value={department.id}>
                {department.name}
              </option>
            ))}
          </select>
        </div>
        {issue && <ErrorBanner message={issue} />}
        <button type="submit" className="primary" disabled={isLoading}>
          {isLoading ? 'Creating…' : 'Create account'}
        </button>
      </form>
    </section>
  )
}
