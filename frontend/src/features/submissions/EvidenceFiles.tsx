import { useId, useState, type ChangeEvent } from 'react'
import { describeError } from '../../api/problem'
import type { Submission, SubmissionFile } from '../../api/types'
import { formatInstant } from '../../components/format'
import { ErrorBanner, ErrorState, PageLoading } from '../../components/ui'
import {
  useDownloadEvidenceMutation,
  useListEvidenceQuery,
  useUploadEvidenceMutation,
} from './submissionsApi'

// Mirrors campushub.upload.max-file-bytes. The server enforces the limit; this only saves a round trip.
const MAX_FILE_BYTES = 20 * 1024 * 1024
const ACCEPTED_TYPES = 'application/pdf,image/png,image/jpeg,.pdf,.png,.jpg,.jpeg'

export function EvidenceFiles({
  submission,
  canUpload,
}: {
  submission: Submission
  canUpload: boolean
}) {
  const inputId = useId()
  const { data: files, error, isLoading, refetch } = useListEvidenceQuery(submission.id)
  const [uploadEvidence, { isLoading: uploading }] = useUploadEvidenceMutation()
  const [downloadEvidence] = useDownloadEvidenceMutation()
  const [failure, setFailure] = useState<{ message: string; requestId?: string | null } | null>(null)

  if (isLoading) return <PageLoading />
  if (error) return <ErrorState message={describeError(error).message} onRetry={refetch} />

  async function handleFile(event: ChangeEvent<HTMLInputElement>) {
    const file = event.target.files?.[0]
    event.target.value = ''
    if (!file) return
    setFailure(null)
    if (file.size > MAX_FILE_BYTES) {
      setFailure({ message: 'The file is larger than 20 MB.' })
      return
    }
    try {
      await uploadEvidence({ submissionId: submission.id, file }).unwrap()
    } catch (err) {
      setFailure(describeError(err))
    }
  }

  async function handleDownload(file: SubmissionFile) {
    setFailure(null)
    try {
      const blob = await downloadEvidence({ submissionId: submission.id, file }).unwrap()
      const url = URL.createObjectURL(blob)
      const link = document.createElement('a')
      link.href = url
      link.download = file.originalName
      link.click()
      setTimeout(() => URL.revokeObjectURL(url), 1000)
    } catch (err) {
      setFailure(describeError(err))
    }
  }

  const list = files ?? []

  return (
    <div className="evidence-files">
      <h3>Evidence files</h3>
      {failure && <ErrorBanner message={failure.message} requestId={failure.requestId} />}
      {list.length === 0 && <p className="muted">No files attached to this revision.</p>}
      {list.length > 0 && (
        <ul className="evidence-list">
          {list.map((file) => (
            <li key={file.id} className="evidence-item">
              <span>{file.originalName}</span>{' '}
              <span className="muted">
                {formatBytes(file.sizeBytes)}, added {formatInstant(file.uploadedAt)}
              </span>{' '}
              <button type="button" onClick={() => void handleDownload(file)}>
                Download
              </button>
            </li>
          ))}
        </ul>
      )}
      {canUpload && (
        <div className="field">
          <label htmlFor={inputId}>Add a file (PDF, PNG or JPEG, up to 20 MB)</label>
          <input id={inputId} type="file" accept={ACCEPTED_TYPES} disabled={uploading} onChange={handleFile} />
        </div>
      )}
    </div>
  )
}

function formatBytes(bytes: number): string {
  if (bytes < 1024) return `${bytes} B`
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`
}
