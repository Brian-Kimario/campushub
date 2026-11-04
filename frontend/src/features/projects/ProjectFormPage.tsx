import { zodResolver } from '@hookform/resolvers/zod'
import { useState } from 'react'
import { useForm, useWatch } from 'react-hook-form'
import { Link, useNavigate, useParams } from 'react-router'
import { z } from 'zod'
import { applyFieldIssues, describeError, type ApiFailure } from '../../api/problem'
import type { Project } from '../../api/types'
import { useAppSelector } from '../../app/hooks'
import { emptyToNull } from '../../components/format'
import { CharCount, EmptyState, ErrorBanner, ErrorState, PageLoading } from '../../components/ui'
import { useCreateProjectMutation, useGetProjectQuery, useUpdateProjectMutation } from './projectsApi'

const projectSchema = z
  .object({
    title: z
      .string()
      .trim()
      .min(3, { error: 'Title must be at least 3 characters.' })
      .max(200, { error: 'Title must be 200 characters or fewer.' }),
    description: z.string().max(5000, { error: 'Description must be 5000 characters or fewer.' }),
    subject: z.string().max(120, { error: 'Subject must be 120 characters or fewer.' }),
    startDate: z.string(),
    dueDate: z.string(),
  })
  .refine((values) => !values.startDate || !values.dueDate || values.dueDate >= values.startDate, {
    error: 'Due date cannot be before the start date.',
    path: ['dueDate'],
  })

type ProjectFormValues = z.infer<typeof projectSchema>

const FORM_FIELDS = ['title', 'description', 'subject', 'startDate', 'dueDate'] as const

// The edit form mounts only after the project loads, so its defaults come from the saved version.
export default function ProjectFormPage() {
  const { projectId } = useParams()
  const user = useAppSelector((state) => state.auth.user)
  const isEdit = Boolean(projectId)
  const { data: project, error, isLoading, refetch } = useGetProjectQuery(projectId ?? '', { skip: !isEdit })

  if (!isEdit) {
    return <ProjectForm />
  }
  if (isLoading) return <PageLoading />
  if (error) return <ErrorState message={describeError(error).message} onRetry={refetch} />
  if (!project) return null

  const canEdit = user?.id === project.owner?.id && project.status === 'ACTIVE'
  if (!canEdit) {
    return (
      <EmptyState title="You cannot edit this project">
        <p className="muted">Only the owner can edit an active project.</p>
        <Link to={`/projects/${project.id}`}>Back to project</Link>
      </EmptyState>
    )
  }

  return <ProjectForm project={project} onReload={() => refetch().unwrap()} />
}

function ProjectForm({
  project,
  onReload,
}: {
  project?: Project
  onReload?: () => Promise<Project>
}) {
  const navigate = useNavigate()
  const [createProject, { isLoading: creating }] = useCreateProjectMutation()
  const [updateProject, { isLoading: updating }] = useUpdateProjectMutation()
  const [version, setVersion] = useState(project?.version ?? 0)
  const [failure, setFailure] = useState<ApiFailure | null>(null)
  const {
    register,
    handleSubmit,
    setError,
    control,
    formState: { errors },
  } = useForm<ProjectFormValues>({
    resolver: zodResolver(projectSchema),
    defaultValues: {
      title: project?.title ?? '',
      description: project?.description ?? '',
      subject: project?.subject ?? '',
      startDate: project?.startDate ?? '',
      dueDate: project?.dueDate ?? '',
    },
  })
  const watched = useWatch({ control, name: ['title', 'description', 'subject'] })
  const saving = creating || updating

  async function onSubmit(values: ProjectFormValues) {
    setFailure(null)
    const fields = {
      title: values.title,
      description: emptyToNull(values.description),
      subject: emptyToNull(values.subject),
      startDate: emptyToNull(values.startDate),
      dueDate: emptyToNull(values.dueDate),
    }
    try {
      if (project) {
        const saved = await updateProject({ id: project.id, version, ...fields }).unwrap()
        navigate(`/projects/${saved.id}`, { replace: true })
      } else {
        const created = await createProject(fields).unwrap()
        navigate(`/projects/${created.id}`, { replace: true })
      }
    } catch (error) {
      const described = describeError(error)
      const unmapped = applyFieldIssues(setError, described.fieldIssues, FORM_FIELDS)
      setFailure({ ...described, fieldIssues: unmapped })
    }
  }

  async function handleReload() {
    if (!onReload) return
    try {
      // Keep the user's typed values. Only the version the next save sends changes.
      const latest = await onReload()
      setVersion(latest.version)
    } catch {
      // The reload error is shown by the project query, so nothing more is needed here.
    }
  }

  return (
    <section>
      <h1>{project ? 'Edit project' : 'New project'}</h1>
      {failure && (
        <>
          <ErrorBanner message={failure.message} requestId={failure.requestId} />
          {failure.code === 'STALE_VERSION' && (
            <button type="button" onClick={() => void handleReload()}>
              Reload latest version
            </button>
          )}
        </>
      )}
      <form onSubmit={handleSubmit(onSubmit)} noValidate>
        <div className="field">
          <label htmlFor="title">Title</label>
          <input
            id="title"
            aria-invalid={Boolean(errors.title)}
            aria-describedby={errors.title ? 'title-error' : undefined}
            {...register('title')}
          />
          {errors.title && <p id="title-error" className="field-error">{errors.title.message}</p>}
          <CharCount value={watched[0]} max={200} />
        </div>

        <div className="field">
          <label htmlFor="description">Description</label>
          <textarea
            id="description"
            rows={6}
            aria-invalid={Boolean(errors.description)}
            aria-describedby={errors.description ? 'description-error' : undefined}
            {...register('description')}
          />
          {errors.description && (
            <p id="description-error" className="field-error">{errors.description.message}</p>
          )}
          <CharCount value={watched[1]} max={5000} />
        </div>

        <div className="field">
          <label htmlFor="subject">Subject</label>
          <input
            id="subject"
            aria-invalid={Boolean(errors.subject)}
            aria-describedby={errors.subject ? 'subject-error' : undefined}
            {...register('subject')}
          />
          {errors.subject && <p id="subject-error" className="field-error">{errors.subject.message}</p>}
          <CharCount value={watched[2]} max={120} />
        </div>

        <div className="field">
          <label htmlFor="startDate">Start date</label>
          <input id="startDate" type="date" {...register('startDate')} />
        </div>

        <div className="field">
          <label htmlFor="dueDate">Due date</label>
          <input
            id="dueDate"
            type="date"
            aria-invalid={Boolean(errors.dueDate)}
            aria-describedby={errors.dueDate ? 'dueDate-error' : undefined}
            {...register('dueDate')}
          />
          {errors.dueDate && <p id="dueDate-error" className="field-error">{errors.dueDate.message}</p>}
        </div>

        <div className="actions">
          <Link to={project ? `/projects/${project.id}` : '/projects'}>Cancel</Link>
          <button type="submit" className="primary" disabled={saving}>
            {saving ? 'Saving…' : 'Save'}
          </button>
        </div>
      </form>
    </section>
  )
}
