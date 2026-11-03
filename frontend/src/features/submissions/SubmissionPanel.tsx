import { zodResolver } from '@hookform/resolvers/zod'
import { useId, useState } from 'react'
import { useForm, useWatch } from 'react-hook-form'
import { z } from 'zod'
import { applyFieldIssues, describeError, type ApiFailure } from '../../api/problem'
import type { Project, Submission, SubmissionStatus } from '../../api/types'
import { emptyToNull, formatInstant, SUBMISSION_STATUS_LABELS } from '../../components/format'
import { Badge, CharCount, EmptyState, ErrorBanner, ErrorState, PageLoading } from '../../components/ui'
import { useCreateSubmissionMutation, useDecideReviewMutation, useListSubmissionsQuery, useStartReviewMutation } from './submissionsApi'

const submissionSchema = z.object({
  title: z
    .string()
    .trim()
    .min(3, { error: 'Title must be at least 3 characters.' })
    .max(200, { error: 'Title must be 200 characters or fewer.' }),
  description: z.string().max(5000, { error: 'Description must be 5000 characters or fewer.' }),
  link: z
    .string()
    .trim()
    .max(500, { error: 'Link must be 500 characters or fewer.' })
    .regex(/^https:\/\/\S+$/, { error: 'Enter a link that starts with https://.' }),
})

const reviewSchema = z
  .object({
    decision: z.enum(['ACCEPTED', 'CHANGES_REQUESTED'], { error: 'Choose a decision.' }),
    feedback: z.string().max(2000, { error: 'Feedback must be 2000 characters or fewer.' }),
  })
  .refine((values) => values.decision !== 'CHANGES_REQUESTED' || values.feedback.trim() !== '', {
    error: 'Feedback is required when requesting changes.',
    path: ['feedback'],
  })

type SubmissionValues = z.infer<typeof submissionSchema>
type ReviewValues = z.infer<typeof reviewSchema>

const SUBMISSION_FIELDS = ['title', 'description', 'link'] as const
const REVIEW_FIELDS = ['decision', 'feedback'] as const

export function SubmissionPanel({
  project,
  isOwner,
  isSupervisor,
}: {
  project: Project
  isOwner: boolean
  isSupervisor: boolean
}) {
  const { data: submissions, error, isLoading, refetch } = useListSubmissionsQuery(project.id)
  const [startReview, { isLoading: starting }] = useStartReviewMutation()
  const [showForm, setShowForm] = useState(false)
  const [failure, setFailure] = useState<ApiFailure | null>(null)

  if (isLoading) return <PageLoading />
  if (error) return <ErrorState message={describeError(error).message} onRetry={refetch} />

  const list = submissions ?? []
  const latest = list.length > 0 ? list[list.length - 1] : undefined
  const archived = project.status === 'ARCHIVED'
  // A new revision is allowed only after changes were requested (API_CONTRACT §10).
  const canSubmit = isOwner && !archived && (!latest || latest.status === 'CHANGES_REQUESTED')
  const canReview = isSupervisor && !archived

  async function handleStartReview(id: string) {
    setFailure(null)
    try {
      await startReview(id).unwrap()
    } catch (err) {
      setFailure(describeError(err))
    }
  }

  return (
    <section className="submission-panel" aria-labelledby="submissions-heading">
      <div className="page-header">
        <h2 id="submissions-heading">Submissions</h2>
        {canSubmit && !showForm && (
          <button type="button" className="primary" onClick={() => setShowForm(true)}>
            {latest ? 'Submit next revision' : 'Submit work'}
          </button>
        )}
      </div>

      {failure && <ErrorBanner message={failure.message} requestId={failure.requestId} />}
      {showForm && (
        <SubmissionForm
          projectId={project.id}
          onDone={() => setShowForm(false)}
          onCancel={() => setShowForm(false)}
        />
      )}

      {list.length === 0 && !showForm && (
        <EmptyState title="No submissions yet">
          <p className="muted">
            {isOwner ? 'Share a link to your work to request a review.' : 'Submissions will appear here.'}
          </p>
        </EmptyState>
      )}

      <ul className="submission-list">
        {list.map((submission) => {
          const isLatest = submission.id === latest?.id
          return (
            <li key={submission.id} className="submission-card">
              <div className="task-head">
                <strong>
                  Revision {submission.revisionNo}: {submission.title}
                </strong>
                <Badge tone={submissionTone(submission.status)}>{SUBMISSION_STATUS_LABELS[submission.status]}</Badge>
              </div>
              <p className="muted">Submitted {formatInstant(submission.submittedAt)}</p>
              <p>
                <a href={submission.link} target="_blank" rel="noopener noreferrer">
                  Open link
                </a>
              </p>
              {submission.description && <p className="pre-wrap">{submission.description}</p>}
              {submission.feedback && <p className="banner banner-info">Feedback: {submission.feedback}</p>}

              {canReview && isLatest && submission.status === 'SUBMITTED' && (
                <button type="button" disabled={starting} onClick={() => void handleStartReview(submission.id)}>
                  Start review
                </button>
              )}
              {canReview && isLatest && submission.status === 'UNDER_REVIEW' && (
                <ReviewForm submission={submission} />
              )}
            </li>
          )
        })}
      </ul>
    </section>
  )
}

function SubmissionForm({ projectId, onDone, onCancel }: { projectId: string; onDone: () => void; onCancel: () => void }) {
  const [createSubmission, { isLoading }] = useCreateSubmissionMutation()
  const [failure, setFailure] = useState<ApiFailure | null>(null)
  const {
    register,
    handleSubmit,
    setError,
    control,
    formState: { errors },
  } = useForm<SubmissionValues>({
    resolver: zodResolver(submissionSchema),
    defaultValues: { title: '', description: '', link: 'https://' },
  })
  const [titleValue, descriptionValue] = useWatch({ control, name: ['title', 'description'] })

  async function onSubmit(values: SubmissionValues) {
    setFailure(null)
    try {
      await createSubmission({
        projectId,
        title: values.title,
        description: emptyToNull(values.description),
        link: values.link,
      }).unwrap()
      onDone()
    } catch (error) {
      const described = describeError(error)
      const unmapped = applyFieldIssues(setError, described.fieldIssues, SUBMISSION_FIELDS)
      setFailure({ ...described, fieldIssues: unmapped })
    }
  }

  return (
    <form className="submission-form" onSubmit={handleSubmit(onSubmit)} noValidate>
      {failure && <ErrorBanner message={failure.message} requestId={failure.requestId} />}
      <div className="field">
        <label htmlFor="submission-title">Title</label>
        <input
          id="submission-title"
          aria-invalid={Boolean(errors.title)}
          aria-describedby={errors.title ? 'submission-title-error' : undefined}
          {...register('title')}
        />
        {errors.title && <p id="submission-title-error" className="field-error">{errors.title.message}</p>}
        <CharCount value={titleValue} max={200} />
      </div>
      <div className="field">
        <label htmlFor="submission-description">Description</label>
        <textarea
          id="submission-description"
          rows={4}
          aria-invalid={Boolean(errors.description)}
          aria-describedby={errors.description ? 'submission-description-error' : undefined}
          {...register('description')}
        />
        {errors.description && (
          <p id="submission-description-error" className="field-error">{errors.description.message}</p>
        )}
        <CharCount value={descriptionValue} max={5000} />
      </div>
      <div className="field">
        <label htmlFor="submission-link">Link (https://)</label>
        <input
          id="submission-link"
          type="url"
          aria-invalid={Boolean(errors.link)}
          aria-describedby={errors.link ? 'submission-link-error' : undefined}
          {...register('link')}
        />
        {errors.link && <p id="submission-link-error" className="field-error">{errors.link.message}</p>}
      </div>
      <div className="actions">
        <button type="button" onClick={onCancel} disabled={isLoading}>
          Cancel
        </button>
        <button type="submit" className="primary" disabled={isLoading}>
          {isLoading ? 'Submitting…' : 'Submit'}
        </button>
      </div>
    </form>
  )
}

function ReviewForm({ submission }: { submission: Submission }) {
  const decisionId = useId()
  const [decideReview, { isLoading }] = useDecideReviewMutation()
  const [failure, setFailure] = useState<ApiFailure | null>(null)
  const {
    register,
    handleSubmit,
    setError,
    control,
    formState: { errors },
  } = useForm<ReviewValues>({
    resolver: zodResolver(reviewSchema),
    defaultValues: { decision: undefined, feedback: '' },
  })
  const feedback = useWatch({ control, name: 'feedback' })

  async function onSubmit(values: ReviewValues) {
    setFailure(null)
    try {
      await decideReview({
        id: submission.id,
        decision: values.decision,
        feedback: emptyToNull(values.feedback),
      }).unwrap()
    } catch (error) {
      const described = describeError(error)
      const unmapped = applyFieldIssues(setError, described.fieldIssues, REVIEW_FIELDS)
      setFailure({ ...described, fieldIssues: unmapped })
    }
  }

  return (
    <form className="review-form" onSubmit={handleSubmit(onSubmit)} noValidate>
      <h3>Record decision</h3>
      {failure && <ErrorBanner message={failure.message} requestId={failure.requestId} />}
      <div className="field">
        <label htmlFor={`${decisionId}-decision`}>Decision</label>
        <select
          id={`${decisionId}-decision`}
          aria-invalid={Boolean(errors.decision)}
          aria-describedby={errors.decision ? `${decisionId}-decision-error` : undefined}
          {...register('decision')}
        >
          <option value="">Choose a decision</option>
          <option value="ACCEPTED">Accept</option>
          <option value="CHANGES_REQUESTED">Request changes</option>
        </select>
        {errors.decision && (
          <p id={`${decisionId}-decision-error`} className="field-error">
            {errors.decision.message}
          </p>
        )}
      </div>
      <div className="field">
        <label htmlFor={`${decisionId}-feedback`}>Feedback</label>
        <textarea
          id={`${decisionId}-feedback`}
          rows={3}
          aria-invalid={Boolean(errors.feedback)}
          aria-describedby={errors.feedback ? `${decisionId}-feedback-error` : undefined}
          {...register('feedback')}
        />
        {errors.feedback && (
          <p id={`${decisionId}-feedback-error`} className="field-error">
            {errors.feedback.message}
          </p>
        )}
        <CharCount value={feedback} max={2000} />
      </div>
      <button type="submit" className="primary" disabled={isLoading}>
        {isLoading ? 'Saving…' : 'Save decision'}
      </button>
    </form>
  )
}

function submissionTone(status: SubmissionStatus): 'neutral' | 'success' | 'warning' | 'danger' {
  switch (status) {
    case 'ACCEPTED':
      return 'success'
    case 'CHANGES_REQUESTED':
      return 'warning'
    default:
      return 'neutral'
  }
}
