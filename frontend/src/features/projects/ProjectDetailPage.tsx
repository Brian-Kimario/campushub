import { Link, useParams } from 'react-router'
import { describeError } from '../../api/problem'
import { useAppSelector } from '../../app/hooks'
import { formatDate, formatInstant, PROJECT_STATUS_LABELS } from '../../components/format'
import { EmptyState, ErrorState, PageLoading } from '../../components/ui'
import { SubmissionPanel } from '../submissions/SubmissionPanel'
import { TaskPanel } from '../tasks/TaskPanel'
import { ProjectAdminPanel } from './ProjectAdminPanel'
import { useGetProjectQuery } from './projectsApi'
import { TeamPanel } from './TeamPanel'

export default function ProjectDetailPage() {
  const { projectId = '' } = useParams()
  const user = useAppSelector((state) => state.auth.user)
  const { data: project, error, isLoading, refetch } = useGetProjectQuery(projectId)

  if (isLoading) return <PageLoading />

  if (error) {
    const failure = describeError(error)
    if (failure.status === 404) {
      return (
        <EmptyState title="Project not found">
          <p className="muted">{failure.message}</p>
          <Link to="/projects">Back to projects</Link>
        </EmptyState>
      )
    }
    return <ErrorState message={failure.message} onRetry={refetch} />
  }

  if (!project) return null

  const isOwner = user?.id === project.owner?.id
  const isSupervisor = user?.role === 'FACULTY' && user.id === project.supervisor?.id
  const canEdit = isOwner && project.status === 'ACTIVE'

  return (
    <article>
      <div className="page-header">
        <h1>{project.title}</h1>
        {canEdit && (
          <Link className="button-link" to={`/projects/${project.id}/edit`}>
            Edit
          </Link>
        )}
      </div>

      {project.status === 'ARCHIVED' && (
        <p className="banner banner-info" role="status">
          This project is archived and read-only.
        </p>
      )}

      <dl className="details">
        <dt>Status</dt>
        <dd>{PROJECT_STATUS_LABELS[project.status]}</dd>
        <dt>Subject</dt>
        <dd>{project.subject ?? '—'}</dd>
        <dt>Owner</dt>
        <dd>{project.owner?.displayName ?? '—'}</dd>
        <dt>Supervisor</dt>
        <dd>{project.supervisor?.displayName ?? 'Not assigned'}</dd>
        <dt>Start</dt>
        <dd>{formatDate(project.startDate)}</dd>
        <dt>Due</dt>
        <dd>{formatDate(project.dueDate)}</dd>
        <dt>Last updated</dt>
        <dd>{formatInstant(project.updatedAt)}</dd>
      </dl>

      {project.description && (
        <section>
          <h2>Description</h2>
          <p className="pre-wrap">{project.description}</p>
        </section>
      )}

      <TaskPanel project={project} isOwner={isOwner} />
      <TeamPanel project={project} isOwner={isOwner} />
      <SubmissionPanel project={project} isOwner={isOwner} isSupervisor={isSupervisor} />
      {user?.role === 'ADMIN' && <ProjectAdminPanel project={project} onReload={() => void refetch()} />}
    </article>
  )
}
