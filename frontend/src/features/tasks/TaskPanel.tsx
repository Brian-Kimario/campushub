import { zodResolver } from '@hookform/resolvers/zod'
import { useId, useState } from 'react'
import { useForm, useWatch } from 'react-hook-form'
import { z } from 'zod'
import { describeError, type ApiFailure } from '../../api/problem'
import type { Project, Task, TaskStatus } from '../../api/types'
import { formatDate, TASK_PRIORITY_LABELS, TASK_STATUS_LABELS } from '../../components/format'
import { Badge, CharCount, ConfirmDialog, EmptyState, ErrorBanner, ErrorState, PageLoading } from '../../components/ui'
import { ALLOWED_TRANSITIONS, needsConfirmation, TRANSITION_LABELS, taskTone } from './taskRules'
import { TaskForm } from './TaskForm'
import { useListTasksQuery, useTransitionTaskMutation } from './tasksApi'

// The API returns pages of 50 here. The panel says how many are hidden instead of paging (Stage 4 scope).
const TASK_PAGE_SIZE = 50

const blockSchema = z.object({
  reason: z
    .string()
    .trim()
    .min(3, { error: 'Give a reason of at least 3 characters.' })
    .max(500, { error: 'Reason must be 500 characters or fewer.' }),
})

export function TaskPanel({ project, isOwner }: { project: Project; isOwner: boolean }) {
  const canManage = isOwner && project.status === 'ACTIVE'
  const { data, error, isLoading, refetch } = useListTasksQuery({
    projectId: project.id,
    page: 0,
    size: TASK_PAGE_SIZE,
  })
  const [transitionTask, { isLoading: transitioning }] = useTransitionTaskMutation()
  const [creating, setCreating] = useState(false)
  const [editingId, setEditingId] = useState<string | null>(null)
  const [blockingId, setBlockingId] = useState<string | null>(null)
  const [confirm, setConfirm] = useState<{ task: Task; to: TaskStatus } | null>(null)
  const [failure, setFailure] = useState<ApiFailure | null>(null)

  if (isLoading) return <PageLoading />
  if (error) return <ErrorState message={describeError(error).message} onRetry={refetch} />

  const tasks = data?.content ?? []
  const hiddenCount = data ? data.totalElements - tasks.length : 0

  async function runTransition(task: Task, to: TaskStatus, blockedReason?: string) {
    setFailure(null)
    try {
      await transitionTask({ id: task.id, to, version: task.version, blockedReason }).unwrap()
      setBlockingId(null)
    } catch (err) {
      setFailure(describeError(err))
    }
  }

  function handleTransition(task: Task, to: TaskStatus) {
    if (to === 'BLOCKED') {
      setBlockingId(task.id)
    } else if (needsConfirmation(to)) {
      setConfirm({ task, to })
    } else {
      void runTransition(task, to)
    }
  }

  return (
    <section className="task-panel" aria-labelledby="tasks-heading">
      <div className="page-header">
        <h2 id="tasks-heading">Tasks</h2>
        {canManage && !creating && (
          <button type="button" className="primary" onClick={() => setCreating(true)}>
            New task
          </button>
        )}
      </div>

      {failure && <ErrorBanner message={failure.message} requestId={failure.requestId} />}
      {creating && (
        <TaskForm projectId={project.id} onDone={() => setCreating(false)} onCancel={() => setCreating(false)} />
      )}

      {tasks.length === 0 && !creating && (
        <EmptyState title="No tasks yet">
          <p className="muted">{canManage ? 'Add the first task for this project.' : 'Tasks will appear here.'}</p>
        </EmptyState>
      )}

      <ul className="task-list">
        {tasks.map((task) => (
          <li key={task.id} className="task-card">
            {editingId === task.id ? (
              <TaskForm
                projectId={project.id}
                task={task}
                onDone={() => setEditingId(null)}
                onCancel={() => setEditingId(null)}
              />
            ) : (
              <>
                <div className="task-head">
                  <strong>{task.title}</strong>
                  <Badge tone={taskTone(task.status)}>{TASK_STATUS_LABELS[task.status]}</Badge>
                </div>
                <p className="muted">
                  Priority {TASK_PRIORITY_LABELS[task.priority]} · Due {formatDate(task.dueDate)}
                </p>
                {task.description && <p className="pre-wrap">{task.description}</p>}
                {task.status === 'BLOCKED' && task.blockedReason && (
                  <p className="banner banner-info">Blocked: {task.blockedReason}</p>
                )}

                {canManage &&
                  (blockingId === task.id ? (
                    <BlockReasonForm
                      disabled={transitioning}
                      onSubmit={(reason) => void runTransition(task, 'BLOCKED', reason)}
                      onCancel={() => setBlockingId(null)}
                    />
                  ) : (
                    <div className="task-actions">
                      {ALLOWED_TRANSITIONS[task.status].map((to) => (
                        <button key={to} type="button" disabled={transitioning} onClick={() => handleTransition(task, to)}>
                          {TRANSITION_LABELS[to]}
                        </button>
                      ))}
                      <button type="button" onClick={() => setEditingId(task.id)}>
                        Edit
                      </button>
                    </div>
                  ))}
              </>
            )}
          </li>
        ))}
      </ul>

      {hiddenCount > 0 && (
        <p className="muted">
          Showing the first {tasks.length} of {data?.totalElements} tasks.
        </p>
      )}

      <ConfirmDialog
        open={confirm !== null}
        title={confirm ? `${TRANSITION_LABELS[confirm.to]} this task?` : 'Confirm'}
        message={
          confirm?.to === 'DONE'
            ? 'Marking the task done ends its work. The owner can reopen it later.'
            : 'Resetting moves the task back to To do.'
        }
        confirmLabel={confirm ? TRANSITION_LABELS[confirm.to] : 'Confirm'}
        danger={confirm?.to === 'TODO'}
        busy={transitioning}
        onConfirm={() => {
          if (confirm) void runTransition(confirm.task, confirm.to)
          setConfirm(null)
        }}
        onCancel={() => setConfirm(null)}
      />
    </section>
  )
}

function BlockReasonForm({
  disabled,
  onSubmit,
  onCancel,
}: {
  disabled: boolean
  onSubmit: (reason: string) => void
  onCancel: () => void
}) {
  const inputId = useId()
  const {
    register,
    handleSubmit,
    control,
    formState: { errors },
  } = useForm<z.infer<typeof blockSchema>>({
    resolver: zodResolver(blockSchema),
    defaultValues: { reason: '' },
  })
  const reason = useWatch({ control, name: 'reason' })

  return (
    <form className="block-form" onSubmit={handleSubmit((values) => onSubmit(values.reason))} noValidate>
      <div className="field">
        <label htmlFor={inputId}>Why is this blocked?</label>
        <textarea
          id={inputId}
          rows={2}
          aria-invalid={Boolean(errors.reason)}
          aria-describedby={errors.reason ? `${inputId}-error` : undefined}
          {...register('reason')}
        />
        {errors.reason && (
          <p id={`${inputId}-error`} className="field-error">
            {errors.reason.message}
          </p>
        )}
        <CharCount value={reason} max={500} />
      </div>
      <div className="actions">
        <button type="button" onClick={onCancel} disabled={disabled}>
          Cancel
        </button>
        <button type="submit" className="primary" disabled={disabled}>
          Block task
        </button>
      </div>
    </form>
  )
}
