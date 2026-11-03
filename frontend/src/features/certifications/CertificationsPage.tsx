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
} from './certificationsApi'

const PAGE_SIZE = 20
const MAX_TITLE_LENGTH = 200
const MAX_ISSUER_LENGTH = 200
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
          <AddCertificationForm />
          <CertificationList />
        </>
      )}
    </section>
  )
}

function CertificationList() {
  const [page, setPage] = useState(0)
  const { data, error, isLoading, isFetching, refetch } = useListCertificationsQuery({ page, size: PAGE_SIZE })
  const [pendingDelete, setPendingDelete] = useState<Certification | null>(null)
  const [deleteCertification, { isLoading: deleting }] = useDeleteCertificationMutation()
  const [issue, setIssue] = useState<string | null>(null)

  async function confirmDelete() {
    if (!pendingDelete) return
    setIssue(null)
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

      {data && data.content.length === 0 && (
        <EmptyState title="No certifications yet">
          <p className="muted">Add a certification above. Only you can see it.</p>
        </EmptyState>
      )}

      {data && data.content.length > 0 && (
        <>
          <ul className="certification-list">
            {data.content.map((certification) => (
              <li key={certification.id} className="certification">
                <h3>{certification.title}</h3>
                <p className="muted">
                  {certification.issuer} · Issued {formatDate(certification.issuedOn)}
                </p>
                {certification.credentialUrl && (
                  <p>
                    <a href={certification.credentialUrl} target="_blank" rel="noopener noreferrer">
                      View credential
                    </a>
                  </p>
                )}
                <button type="button" className="danger" onClick={() => setPendingDelete(certification)}>
                  Delete {certification.title}
                </button>
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

function AddCertificationForm() {
  const [title, setTitle] = useState('')
  const [issuer, setIssuer] = useState('')
  const [issuedOn, setIssuedOn] = useState('')
  const [credentialUrl, setCredentialUrl] = useState('')
  const [issues, setIssues] = useState<string[]>([])
  const [saved, setSaved] = useState<string | null>(null)
  const [createCertification, { isLoading }] = useCreateCertificationMutation()

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setSaved(null)
    const cleanTitle = title.trim()
    const cleanIssuer = issuer.trim()
    const cleanUrl = credentialUrl.trim()
    const problems: string[] = []
    if (cleanTitle.length < 1 || cleanTitle.length > MAX_TITLE_LENGTH) {
      problems.push(`Title must be 1 to ${MAX_TITLE_LENGTH} characters.`)
    }
    if (cleanIssuer.length < 1 || cleanIssuer.length > MAX_ISSUER_LENGTH) {
      problems.push(`Issuer must be 1 to ${MAX_ISSUER_LENGTH} characters.`)
    }
    if (issuedOn === '') {
      problems.push('Issue date is required.')
    }
    if (cleanUrl !== '' && (!cleanUrl.startsWith('https://') || cleanUrl.length > MAX_URL_LENGTH)) {
      problems.push(`Credential link must start with https:// and be ${MAX_URL_LENGTH} characters or fewer.`)
    }
    if (problems.length > 0) {
      setIssues(problems)
      return
    }
    setIssues([])
    try {
      const created = await createCertification({
        title: cleanTitle,
        issuer: cleanIssuer,
        issuedOn,
        credentialUrl: cleanUrl === '' ? null : cleanUrl,
      }).unwrap()
      setSaved(`Added ${created.title}.`)
      setTitle('')
      setIssuer('')
      setIssuedOn('')
      setCredentialUrl('')
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
    <section aria-labelledby="add-certification-heading">
      <h2 id="add-certification-heading">Add a certification</h2>
      <form onSubmit={handleSubmit} noValidate>
        <div className="field">
          <label htmlFor="certification-title">Title</label>
          <input
            id="certification-title"
            value={title}
            maxLength={MAX_TITLE_LENGTH}
            onChange={(event) => setTitle(event.target.value)}
          />
        </div>
        <div className="field">
          <label htmlFor="certification-issuer">Issuer</label>
          <input
            id="certification-issuer"
            value={issuer}
            maxLength={MAX_ISSUER_LENGTH}
            onChange={(event) => setIssuer(event.target.value)}
          />
        </div>
        <div className="field">
          <label htmlFor="certification-issued-on">Issue date</label>
          <input
            id="certification-issued-on"
            type="date"
            value={issuedOn}
            onChange={(event) => setIssuedOn(event.target.value)}
          />
        </div>
        <div className="field">
          <label htmlFor="certification-url">Credential link (optional)</label>
          <input
            id="certification-url"
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
        <button type="submit" className="primary" disabled={isLoading}>
          {isLoading ? 'Saving…' : 'Add certification'}
        </button>
      </form>
    </section>
  )
}
