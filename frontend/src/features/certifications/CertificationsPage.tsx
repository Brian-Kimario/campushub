import { useState, type FormEvent } from 'react'
import { describeError } from '../../api/problem'
import type { Certification } from '../../api/types'
import { useAppSelector } from '../../app/hooks'
import { ConfirmDialog, EmptyState, ErrorBanner, ErrorState, PageLoading } from '../../components/ui'
import { formatDate } from '../../components/format'
import {
  useCreateCertificationMutation,
  useDeleteCertificationMutation,
  useListCertificationsQuery,
  useUpdateCertificationMutation,
  type CertificationFields,
} from './certificationsApi'

const PAGE_SIZE = 20
const MAX_TITLE_LENGTH = 200
const MAX_ISSUER_LENGTH = 200
const MAX_CREDENTIAL_ID_LENGTH = 100
const MAX_URL_LENGTH = 500

export default function CertificationsPage() {
  const user = useAppSelector((state) => state.auth.user)

  return (
    <section>
      <div className="page-header">
        <h1>Certifications</h1>
      </div>

      {user?.role !== 'STUDENT' && (
        <EmptyState title="Only students keep certifications">
          <p className="muted">Certifications are private to the student who added them.</p>
        </EmptyState>
      )}

      {user?.role === 'STUDENT' && (
        <>
          {/* The portfolio leads; the add form follows it so the page reads as a record first. */}
          <CertificationList />
          <section className="add-certification" aria-labelledby="add-certification-heading">
            <h2 id="add-certification-heading">Add a certification</h2>
            <CertificationForm idPrefix="new" submitLabel="Add certification" />
          </section>
        </>
      )}
    </section>
  )
}

function CertificationList() {
  const [page, setPage] = useState(0)
  const { data, error, isLoading, isFetching, refetch } = useListCertificationsQuery({ page, size: PAGE_SIZE })
  const [editingId, setEditingId] = useState<string | null>(null)
  const [pendingDelete, setPendingDelete] = useState<Certification | null>(null)
  const [deleteCertification, { isLoading: deleting }] = useDeleteCertificationMutation()
  const [issue, setIssue] = useState<string | null>(null)
  const [saved, setSaved] = useState<string | null>(null)

  async function confirmDelete() {
    if (!pendingDelete) return
    setIssue(null)
    setSaved(null)
    try {
      await deleteCertification(pendingDelete.id).unwrap()
    } catch (err) {
      setIssue(describeError(err).message)
    } finally {
      setPendingDelete(null)
    }
  }

  return (
    <section aria-labelledby="certifications-heading">
      <h2 id="certifications-heading">My certifications</h2>

      {isLoading && <PageLoading />}
      {error && !data && <ErrorState message={describeError(error).message} onRetry={refetch} />}
      {issue && <ErrorBanner message={issue} />}
      {saved && <p role="status">{saved}</p>}

      {data && data.content.length === 0 && (
        <EmptyState title="No certifications yet">
          <p className="muted">Add your first certification below. Only you can see it.</p>
        </EmptyState>
      )}

      {data && data.content.length > 0 && (
        <>
          <ul className="certification-list">
            {data.content.map((certification) => (
              <li key={certification.id} className="certification">
                {editingId === certification.id ? (
                  <>
                    <h3>Edit {certification.title}</h3>
                    <CertificationForm
                      idPrefix={`edit-${certification.id}`}
                      initial={certification}
                      submitLabel="Save changes"
                      onCancel={() => setEditingId(null)}
                      onSaved={(message) => {
                        setEditingId(null)
                        setSaved(message)
                      }}
                    />
                  </>
                ) : (
                  <>
                    <h3>{certification.title}</h3>
                    <p className="muted">
                      {certification.issuer} · Issued {formatDate(certification.issuedOn)}
                      {certification.expiresOn && <> · Expires {formatDate(certification.expiresOn)}</>}
                    </p>
                    {certification.credentialId && <p className="muted">Credential ID: {certification.credentialId}</p>}
                    {certification.credentialUrl && (
                      <p>
                        <a href={certification.credentialUrl} target="_blank" rel="noopener noreferrer">
                          View credential
                        </a>
                      </p>
                    )}
                    <div className="actions">
                      <button
                        type="button"
                        onClick={() => {
                          setSaved(null)
                          setEditingId(certification.id)
                        }}
                      >
                        Edit {certification.title}
                      </button>
                      <button type="button" className="danger" onClick={() => setPendingDelete(certification)}>
                        Delete {certification.title}
                      </button>
                    </div>
                  </>
                )}
              </li>
            ))}
          </ul>
          <nav className="pager" aria-label="Certification pagination">
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

      <ConfirmDialog
        open={pendingDelete !== null}
        title="Delete certification?"
        message={`${pendingDelete?.title ?? 'This certification'} will be removed. This cannot be undone.`}
        confirmLabel="Delete"
        danger
        busy={deleting}
        onConfirm={confirmDelete}
        onCancel={() => setPendingDelete(null)}
      />
    </section>
  )
}

interface CertificationFormProps {
  idPrefix: string
  submitLabel: string
  initial?: Certification
  onCancel?: () => void
  onSaved?: (message: string) => void
}

/** Shared by "add" and "edit". Edit sends the version the form was opened with (optimistic lock). */
function CertificationForm({ idPrefix, submitLabel, initial, onCancel, onSaved }: CertificationFormProps) {
  const [title, setTitle] = useState(initial?.title ?? '')
  const [issuer, setIssuer] = useState(initial?.issuer ?? '')
  const [issuedOn, setIssuedOn] = useState(initial?.issuedOn ?? '')
  const [expiresOn, setExpiresOn] = useState(initial?.expiresOn ?? '')
  const [credentialId, setCredentialId] = useState(initial?.credentialId ?? '')
  const [credentialUrl, setCredentialUrl] = useState(initial?.credentialUrl ?? '')
  const [issues, setIssues] = useState<string[]>([])
  const [saved, setSaved] = useState<string | null>(null)
  const [createCertification, createState] = useCreateCertificationMutation()
  const [updateCertification, updateState] = useUpdateCertificationMutation()
  const busy = createState.isLoading || updateState.isLoading

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setSaved(null)
    const fields: CertificationFields = {
      title: title.trim(),
      issuer: issuer.trim(),
      issuedOn,
      expiresOn: expiresOn === '' ? null : expiresOn,
      credentialId: credentialId.trim() === '' ? null : credentialId.trim(),
      credentialUrl: credentialUrl.trim() === '' ? null : credentialUrl.trim(),
    }
    const problems = validate(fields)
    if (problems.length > 0) {
      setIssues(problems)
      return
    }
    setIssues([])
    try {
      if (initial) {
        const updated = await updateCertification({ ...fields, id: initial.id, version: initial.version }).unwrap()
        onSaved?.(`Saved ${updated.title}.`)
      } else {
        const created = await createCertification(fields).unwrap()
        setSaved(`Added ${created.title}.`)
        setTitle('')
        setIssuer('')
        setIssuedOn('')
        setExpiresOn('')
        setCredentialId('')
        setCredentialUrl('')
      }
    } catch (err) {
      const failure = describeError(err)
      setIssues(
        failure.fieldIssues.length > 0
          ? failure.fieldIssues.map((item) => `${item.field}: ${item.reason}`)
          : [failure.message],
      )
    }
  }

  return (
    <form onSubmit={handleSubmit} noValidate>
      <div className="field">
        <label htmlFor={`${idPrefix}-title`}>Title</label>
        <input
          id={`${idPrefix}-title`}
          value={title}
          maxLength={MAX_TITLE_LENGTH}
          onChange={(event) => setTitle(event.target.value)}
        />
      </div>
      <div className="field">
        <label htmlFor={`${idPrefix}-issuer`}>Issuer</label>
        <input
          id={`${idPrefix}-issuer`}
          value={issuer}
          maxLength={MAX_ISSUER_LENGTH}
          onChange={(event) => setIssuer(event.target.value)}
        />
      </div>
      <div className="field">
        <label htmlFor={`${idPrefix}-issued-on`}>Issue date</label>
        <input
          id={`${idPrefix}-issued-on`}
          type="date"
          value={issuedOn}
          onChange={(event) => setIssuedOn(event.target.value)}
        />
      </div>
      <div className="field">
        <label htmlFor={`${idPrefix}-expires-on`}>Expiry date (optional)</label>
        <input
          id={`${idPrefix}-expires-on`}
          type="date"
          value={expiresOn}
          onChange={(event) => setExpiresOn(event.target.value)}
        />
      </div>
      <div className="field">
        <label htmlFor={`${idPrefix}-credential-id`}>Credential ID (optional)</label>
        <input
          id={`${idPrefix}-credential-id`}
          value={credentialId}
          maxLength={MAX_CREDENTIAL_ID_LENGTH}
          onChange={(event) => setCredentialId(event.target.value)}
        />
      </div>
      <div className="field">
        <label htmlFor={`${idPrefix}-url`}>Credential link (optional)</label>
        <input
          id={`${idPrefix}-url`}
          type="url"
          value={credentialUrl}
          maxLength={MAX_URL_LENGTH}
          placeholder="https://"
          onChange={(event) => setCredentialUrl(event.target.value)}
        />
        <p className="muted">The link is stored as text. CampusHub does not open it.</p>
      </div>
      {issues.length > 0 && <ErrorBanner message={issues.join(' ')} />}
      {saved && <p role="status">{saved}</p>}
      <div className="actions">
        <button type="submit" className="primary" disabled={busy}>
          {busy ? 'Saving…' : submitLabel}
        </button>
        {onCancel && (
          <button type="button" onClick={onCancel} disabled={busy}>
            Cancel
          </button>
        )}
      </div>
    </form>
  )
}

function validate(fields: CertificationFields): string[] {
  const problems: string[] = []
  if (fields.title.length < 1 || fields.title.length > MAX_TITLE_LENGTH) {
    problems.push(`Title must be 1 to ${MAX_TITLE_LENGTH} characters.`)
  }
  if (fields.issuer.length < 1 || fields.issuer.length > MAX_ISSUER_LENGTH) {
    problems.push(`Issuer must be 1 to ${MAX_ISSUER_LENGTH} characters.`)
  }
  if (fields.issuedOn === '') {
    problems.push('Issue date is required.')
  }
  if (fields.issuedOn !== '' && fields.expiresOn !== null && fields.expiresOn < fields.issuedOn) {
    problems.push('The expiry date cannot be before the issue date.')
  }
  if (fields.credentialId !== null && fields.credentialId.length > MAX_CREDENTIAL_ID_LENGTH) {
    problems.push(`Credential ID must be ${MAX_CREDENTIAL_ID_LENGTH} characters or fewer.`)
  }
  if (
    fields.credentialUrl !== null &&
    (!fields.credentialUrl.startsWith('https://') || fields.credentialUrl.length > MAX_URL_LENGTH)
  ) {
    problems.push(`Credential link must start with https:// and be ${MAX_URL_LENGTH} characters or fewer.`)
  }
  return problems
}
