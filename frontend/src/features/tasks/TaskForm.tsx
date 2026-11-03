import { zodResolver } from '@hookform/resolvers/zod'
import { useState } from 'react'
import { useForm, useWatch } from 'react-hook-form'
import { z } from 'zod'
import { applyFieldIssues, describeError, type ApiFailure } from '../../api/problem'
import type { Task, TaskPriority } from '../../api/types'
import { emptyToNull, TASK_PRIORITY_LABELS } from '../../components/format'
import { CharCount, ErrorBanner } from '../../components/ui'
import { useListMembersQuery } from '../projects/membershipApi'
import { useCreateTaskMutation, useUpdateTaskMutation } from './tasksApi'

const PRIORITIES: TaskPriority[] = ['LOW', 'MEDIUM', 'HIGH']
const UNASSIGNED = ''

const taskSchema = z.object({
  title: z
    .string()
    .trim()
    .min(3, { error: 'Title must be at least 3 characters.' })
    .max(200, { error: 'Title must be 200 characters or fewer.' }),
  description: z.string().max(5000, { error: 'Description must be 5000 characters or fewer.' }),
  priority: z.enum(PRIORITIES, { error: 'Choose a priority.' }),
  dueDate: z.string(),
  assigneeId: z.string(),
})

type TaskFormValues = z.infer<typeof taskSchema>

const FORM_FIELDS = ['title', 'description', 'priority', 'dueDate', 'assigneeId'] as const

interface TaskFormProps {
  projectId: string
  task?: Task
  onDone: () => void
  onCancel: () => void
}

export function TaskForm({ projectId, task, onDone, onCancel }: TaskFormProps) {
  const [createTask, { isLoading: creating }] = useCreateTaskMutation()
  const [updateTask, { isLoading: updating }] = useUpdateTaskMutation()
  const members = useListMembersQuery(projectId)
  const [failure, setFailure] = useState<ApiFailure | null>(null)
  const {
    register,
    handleSubmit,
    setError,
    control,
    formState: { errors },
  } = useForm<TaskFormValues>({
    resolver: zodResolver(taskSchema),
    defaultValues: {
      title: task?.title ?? '',
      description: task?.description ?? '',
      priority: task?.priority ?? 'MEDIUM',
      dueDate: task?.dueDate ?? '',
      assigneeId: task?.assigneeId ?? UNASSIGNED,
    },
  })
  const [titleValue, descriptionValue] = useWatch({ control, name: ['title', 'description'] })
  const saving = creating || updating

  async function onSubmit(values: TaskFormValues) {
    setFailure(null)
    const fields = {
      title: values.title,
      description: emptyToNull(values.description),
      priority: values.priority,
      dueDate: emptyToNull(values.dueDate),
      // Until the team list has loaded, keep the current assignee: the request is full-field, so a
      // placeholder selection must not unassign the task.
      assigneeId: members.isSuccess ? emptyToNull(values.assigneeId) : (task?.assigneeId ?? null),
    }
    try {
      if (task) {
        await updateTask({ id: task.id, version: task.version, ...fields }).unwrap()
      } else {
        await createTask({ projectId, ...fields }).unwrap()
      }
      onDone()
    } catch (error) {
      const described = describeError(error)
      const unmapped = applyFieldIssues(setError, described.fieldIssues, FORM_FIELDS)
      setFailure({ ...described, fieldIssues: unmapped })
    }
  }

  return (
    <form className="task-form" onSubmit={handleSubmit(onSubmit)} noValidate>
      {failure && <ErrorBanner message={failure.message} requestId={failure.requestId} />}
      <div className="field">
        <label htmlFor={`task-title-${task?.id ?? 'new'}`}>Title</label>
        <input
          id={`task-title-${task?.id ?? 'new'}`}
          aria-invalid={Boolean(errors.title)}
          aria-describedby={errors.title ? 'task-title-error' : undefined}
          {...register('title')}
        />
        {errors.title && <p id="task-title-error" className="field-error">{errors.title.message}</p>}
        <CharCount value={titleValue} max={200} />
      </div>
      <div className="field">
        <label htmlFor={`task-description-${task?.id ?? 'new'}`}>Description</label>
        <textarea
          id={`task-description-${task?.id ?? 'new'}`}
          rows={4}
          aria-invalid={Boolean(errors.description)}
          aria-describedby={errors.description ? 'task-description-error' : undefined}
          {...register('description')}
        />
        {errors.description && (
          <p id="task-description-error" className="field-error">{errors.description.message}</p>
        )}
        <CharCount value={descriptionValue} max={5000} />
      </div>
      <div className="field">
        <label htmlFor={`task-priority-${task?.id ?? 'new'}`}>Priority</label>
        <select id={`task-priority-${task?.id ?? 'new'}`} {...register('priority')}>
          {PRIORITIES.map((priority) => (
            <option key={priority} value={priority}>
              {TASK_PRIORITY_LABELS[priority]}
            </option>
          ))}
        </select>
      </div>
      <div className="field">
        <label htmlFor={`task-due-${task?.id ?? 'new'}`}>Due date</label>
        <input id={`task-due-${task?.id ?? 'new'}`} type="date" {...register('dueDate')} />
      </div>
      <div className="field">
        <label htmlFor={`task-assignee-${task?.id ?? 'new'}`}>Assignee</label>
        <select
          id={`task-assignee-${task?.id ?? 'new'}`}
          disabled={!members.isSuccess}
          aria-describedby={members.isError ? `task-assignee-error-${task?.id ?? 'new'}` : undefined}
          {...register('assigneeId')}
        >
          <option value={UNASSIGNED}>Unassigned</option>
          {(members.data ?? []).map((member) => (
            <option key={member.userId} value={member.userId}>
              {member.displayName ?? 'Team member'}
              {member.role === 'OWNER' ? ' (owner)' : ''}
            </option>
          ))}
        </select>
        {members.isError && (
          <p id={`task-assignee-error-${task?.id ?? 'new'}`} className="field-error">
            Team members could not be loaded, so the assignee cannot be changed right now.
          </p>
        )}
      </div>
      <div className="actions">
        <button type="button" onClick={onCancel} disabled={saving}>
          Cancel
        </button>
        <button type="submit" className="primary" disabled={saving}>
          {saving ? 'Saving…' : task ? 'Save task' : 'Create task'}
        </button>
      </div>
    </form>
  )
}
