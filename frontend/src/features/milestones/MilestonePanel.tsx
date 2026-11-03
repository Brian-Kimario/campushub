import { zodResolver } from '@hookform/resolvers/zod'
import { useId, useState } from 'react'
import { useForm } from 'react-hook-form'
import { z } from 'zod'
import { describeError, type ApiFailure } from '../../api/problem'
import type { Milestone, Project } from '../../api/types'
import { formatInstant } from '../../components/format'
import { Badge, ConfirmDialog, EmptyState, ErrorBanner, ErrorState, PageLoading } from '../../components/ui'
import {
  useCloseMilestoneMutation,
  useCreateMilestoneMutation,
  useListMilestonesQuery,
  useUpdateMilestoneMutation,
} from './milestonesApi'

const milestoneSchema = z.object({
  title: z
    .string()
    .trim()
    .min(3, { error: 'Title must be at least 3 characters.' })
    .max(200, { error: 'Title must be 200 characters or fewer.' }),
  dueAt: z.string().min(1, { error: 'Choose a due date and time.' }),
})

type MilestoneFormValues = z.infer<typeof milestoneSchema>

/** Local date-time text for a datetime-local input, from an instant. */
function toLocalInput(instant: string): string {
  const date = new Date(instant)
  const pad = (value: number) => String(value).padStart(2, '0')
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}T${pad(date.getHours())}:${pad(date.getMinutes())}`
}

export function MilestonePanel({
  project,
  isOwner,
  isSupervisor,
}: {
  project: Project
  isOwner: boolean
  isSupervisor: boolean
}) {
  const canManage = project.status === 'ACTIVE'
  const { data: milestones, error, isLoading, refetch } = useListMilestonesQuery(project.id)
  const [updateMilestone, { isLoading: updating }] = useUpdateMilestoneMutation()
  const [closeMilestone, { isLoading: closing }] = useCloseMilestoneMutation()
  const [creating, setCreating] = useState(false)
  const [editingId, setEditingId] = useState<string | null>(null)
  const [confirmId, setConfirmId] = useState<string | null>(null)
  const [failure, setFailure] = useState<ApiFailure | null>(null)

  if (isLoading) return <PageLoading />
  if (error) return <ErrorState message={describeError(error).message} onRetry={refetch} />

  const list = milestones ?? []
  const confirming = list.find((milestone) => milestone.id === confirmId) ?? null

  async function runUpdate(milestone: Milestone, changes: { allowLate?: boolean }) {
    setFailure(null)
    try {
      await updateMilestone({ id: milestone.id, projectId: project.id, version: milestone.version, ...changes }).unwrap()
    } catch (err) {
      setFailure(describeError(err))
    }
  }

  async function runClose(milestone: Milestone) {
    setFailure(null)
    try {
      await closeMilestone({ id: milestone.id, projectId: project.id }).unwrap()
    } catch (err) {
      setFailure(describeError(err))
    } finally {
      setConfirmId(null)
    }
  }

  return (
    <section className="milestone-panel" aria-labelledby="milestones-heading">
      <div className="page-header">
        <h2 id="milestones-heading">Milestones</h2>
        {isOwner && canManage && !creating && (
          <button type="button" className="primary" onClick={() => setCreating(true)}>
            New milestone
          </button>
        )}
      </div>

      {failure && <ErrorBanner message={failure.message} requestId={failure.requestId} />}
      {creating && (
        <MilestoneForm projectId={project.id} onDone={() => setCreating(false)} onCancel={() => setCreating(false)} />
      )}

      {list.length === 0 && !creating && (
        <EmptyState title="No milestones yet">
          <p className="muted">
            {isOwner && canManage ? 'Add the first deadline for this project.' : 'Milestones will appear here.'}
          </p>
        </EmptyState>
      )}

      <ul className="milestone-list">
        {list.map((milestone) => (
          <li key={milestone.id} className="milestone-card">
            {editingId === milestone.id ? (
              <MilestoneForm
                projectId={project.id}
                milestone={milestone}
                onDone={() => setEditingId(null)}
                onCancel={() => setEditingId(null)}
              />
            ) : (
              <>
                <div className="task-head">
                  <strong>
                    {milestone.sequence}. {milestone.title}
                  </strong>
                  <Badge tone={milestone.status === 'CLOSED' ? 'neutral' : 'success'}>
                    {milestone.status === 'CLOSED' ? 'Closed' : 'Open'}
                  </Badge>
                </div>
                <p className="muted">
                  Due {formatInstant(milestone.dueAt)} · Late submissions {milestone.allowLate ? 'allowed' : 'not allowed'}
                </p>

                {milestone.status === 'OPEN' && canManage && (
                  <div className="task-actions">
                    {isOwner && (
                      <button type="button" disabled={updating} onClick={() => setEditingId(milestone.id)}>
                        Edit
                      </button>
                    )}
                    {isSupervisor && !isOwner && (
                      <label className="checkbox">
                        <input
                          type="checkbox"
                          checked={milestone.allowLate}
                          disabled={updating}
                          onChange={(event) => void runUpdate(milestone, { allowLate: event.target.checked })}
                        />
                        Allow late submissions
                      </label>
                    )}
                    {(isOwner || isSupervisor) && (
                      <button type="button" disabled={closing} onClick={() => setConfirmId(milestone.id)}>
                        Close milestone
                      </button>
                    )}
                  </div>
                )}
              </>
            )}
          </li>
        ))}
      </ul>

      <ConfirmDialog
        open={confirming !== null}
        title="Close this milestone?"
        message="Closing is permanent in this version. No further uploads or submissions will be accepted for it."
        confirmLabel="Close milestone"
        danger
        busy={closing}
        onConfirm={() => {
          if (confirming) void runClose(confirming)
        }}
        onCancel={() => setConfirmId(null)}
      />
    </section>
  )
}

function MilestoneForm({
  projectId,
  milestone,
  onDone,
  onCancel,
}: {
  projectId: string
  milestone?: Milestone
  onDone: () => void
  onCancel: () => void
}) {
  const [createMilestone, { isLoading: creating }] = useCreateMilestoneMutation()
  const [updateMilestone, { isLoading: updating }] = useUpdateMilestoneMutation()
  const [failure, setFailure] = useState<ApiFailure | null>(null)
  const idBase = useId()
  const {
    register,
    handleSubmit,
    formState: { errors },
  } = useForm<MilestoneFormValues>({
    resolver: zodResolver(milestoneSchema),
    defaultValues: {
      title: milestone?.title ?? '',
      dueAt: milestone ? toLocalInput(milestone.dueAt) : '',
    },
  })
  const saving = creating || updating

  async function onSubmit(values: MilestoneFormValues) {
    setFailure(null)
    const dueAt = new Date(values.dueAt).toISOString()
    try {
      if (milestone) {
        await updateMilestone({
          id: milestone.id,
          projectId,
          version: milestone.version,
          title: values.title,
          dueAt,
        }).unwrap()
      } else {
        await createMilestone({ projectId, title: values.title, dueAt }).unwrap()
      }
      onDone()
    } catch (error) {
      setFailure(describeError(error))
    }
  }

  return (
    <form className="milestone-form" onSubmit={handleSubmit(onSubmit)} noValidate>
      {failure && <ErrorBanner message={failure.message} requestId={failure.requestId} />}
      <div className="field">
        <label htmlFor={`${idBase}-title`}>Title</label>
        <input
          id={`${idBase}-title`}
          aria-invalid={Boolean(errors.title)}
          aria-describedby={errors.title ? `${idBase}-title-error` : undefined}
          {...register('title')}
        />
        {errors.title && (
          <p id={`${idBase}-title-error`} className="field-error">
            {errors.title.message}
          </p>
        )}
      </div>
      <div className="field">
        <label htmlFor={`${idBase}-due`}>Due date and time</label>
        <input
          id={`${idBase}-due`}
          type="datetime-local"
          aria-invalid={Boolean(errors.dueAt)}
          aria-describedby={errors.dueAt ? `${idBase}-due-error` : undefined}
          {...register('dueAt')}
        />
        {errors.dueAt && (
          <p id={`${idBase}-due-error`} className="field-error">
            {errors.dueAt.message}
          </p>
        )}
      </div>
      <div className="actions">
        <button type="button" onClick={onCancel} disabled={saving}>
          Cancel
        </button>
        <button type="submit" className="primary" disabled={saving}>
          {saving ? 'Saving…' : milestone ? 'Save milestone' : 'Create milestone'}
        </button>
      </div>
    </form>
  )
}
