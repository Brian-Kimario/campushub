import { Link } from 'react-router'
import { describeError } from '../../api/problem'
import type { ProjectStatus } from '../../api/types'
import { useAppDispatch, useAppSelector } from '../../app/hooks'
import { formatDate, PROJECT_STATUS_LABELS } from '../../components/format'
import { Badge, EmptyState, ErrorState, PageLoading } from '../../components/ui'
import { pageChanged, statusChanged, type ProjectStatusFilter } from './projectFiltersSlice'
import { useListProjectsQuery } from './projectsApi'

const PAGE_SIZE = 20

const STATUS_OPTIONS: { value: ProjectStatusFilter; label: string }[] = [
  { value: 'ALL', label: 'All' },
  { value: 'ACTIVE', label: 'Active' },
  { value: 'ARCHIVED', label: 'Archived' },
]

export default function ProjectListPage() {
  const dispatch = useAppDispatch()
  const user = useAppSelector((state) => state.auth.user)
  const { status, page } = useAppSelector((state) => state.projectFilters)
  const { data, error, isLoading, isFetching, refetch } = useListProjectsQuery({
    status: status === 'ALL' ? undefined : status,
    page,
    size: PAGE_SIZE,
  })

  function handleStatusChange(value: string) {
    const next = STATUS_OPTIONS.find((option) => option.value === value)
    if (next) dispatch(statusChanged(next.value))
  }

  return (
    <section>
      <div className="page-header">
        <h1>Projects</h1>
        {user?.role === 'STUDENT' && (
          <Link className="button-link primary" to="/projects/new">
            New project
          </Link>
        )}
      </div>

      <div className="field inline">
        <label htmlFor="status-filter">Status</label>
        <select id="status-filter" value={status} onChange={(event) => handleStatusChange(event.target.value)}>
          {STATUS_OPTIONS.map((option) => (
            <option key={option.value} value={option.value}>
              {option.label}
            </option>
          ))}
        </select>
      </div>

      {isLoading && <PageLoading />}
      {error && !data && <ErrorState message={describeError(error).message} onRetry={refetch} />}

      {data && data.content.length === 0 && (
        <EmptyState title="No projects to show">
          <p className="muted">
            {status === 'ALL' ? 'Projects you can see will appear here.' : 'No projects match this status.'}
          </p>
        </EmptyState>
      )}

      {data && data.content.length > 0 && (
        <>
          <ul className="project-list">
            {data.content.map((project) => (
              <li key={project.id} className="project-card">
                <Link to={`/projects/${project.id}`}>{project.title}</Link>
                <p className="muted">
                  {project.subject ?? 'No subject'}
                  {project.dueDate && ` · Due ${formatDate(project.dueDate)}`}
                </p>
                <Badge tone={project.status === 'ARCHIVED' ? 'warning' : 'success'}>
                  {PROJECT_STATUS_LABELS[project.status as ProjectStatus]}
                </Badge>
              </li>
            ))}
          </ul>
          <nav className="pager" aria-label="Pagination">
            <button
              type="button"
              onClick={() => dispatch(pageChanged(page - 1))}
              disabled={page === 0 || isFetching}
            >
              Previous
            </button>
            <span>
              Page {data.page + 1} of {Math.max(data.totalPages, 1)}
            </span>
            <button
              type="button"
              onClick={() => dispatch(pageChanged(page + 1))}
              disabled={page + 1 >= data.totalPages || isFetching}
            >
              Next
            </button>
          </nav>
        </>
      )}
    </section>
  )
}
