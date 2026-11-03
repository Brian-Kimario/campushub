import { Link } from 'react-router'
import { describeError } from '../../api/problem'
import { useAppSelector } from '../../app/hooks'
import {
  formatDate,
  PROJECT_STATUS_LABELS,
  ROLE_LABELS,
  TASK_STATUS_LABELS,
} from '../../components/format'
import { Badge, EmptyState, ErrorState, PageLoading } from '../../components/ui'
import {
  useGetAdminDashboardQuery,
  useGetFacultyDashboardQuery,
  useGetStudentDashboardQuery,
  type AdminDashboard,
  type FacultyDashboard,
  type StudentDashboard,
} from './dashboardApi'
import type { ProjectStatus, Role } from '../../api/types'

export default function DashboardPage() {
  const role = useAppSelector((state) => state.auth.user?.role)

  return (
    <section>
      <div className="page-header">
        <h1>Dashboard</h1>
      </div>
      {role === 'STUDENT' && <StudentOverview />}
      {role === 'FACULTY' && <FacultyOverview />}
      {role === 'ADMIN' && <AdminOverview />}
    </section>
  )
}

function Tile({ label, value, detail }: { label: string; value: string | number; detail?: string }) {
  return (
    <div className="stat-tile">
      <span className="stat-label">{label}</span>
      <span className="stat-value">{value}</span>
      {detail && <span className="muted">{detail}</span>}
    </div>
  )
}

function ProgressBar({ percent }: { percent: number | null }) {
  if (percent === null) {
    return <span className="muted">No tasks yet</span>
  }
  return (
    <div
      className="progress"
      role="progressbar"
      aria-label="Task progress"
      aria-valuemin={0}
      aria-valuemax={100}
      aria-valuenow={percent}
    >
      <span className="progress-fill" style={{ width: `${percent}%` }} />
      <span className="progress-text">{percent}% done</span>
    </div>
  )
}

function StudentOverview() {
  const { data, error, isLoading, refetch } = useGetStudentDashboardQuery()

  if (isLoading) return <PageLoading />
  if (error || !data) {
    return <ErrorState message={describeError(error).message} onRetry={refetch} />
  }
  return <StudentContent data={data} />
}

function StudentContent({ data }: { data: StudentDashboard }) {
  return (
    <>
      <div className="stat-grid">
        <Tile label="Projects" value={data.projectsTotal} detail={`${data.projectsActive} active`} />
        <Tile label="Tasks" value={data.tasksTotal} detail={`${data.tasksDone} done`} />
        <Tile label="Submissions" value={data.submissionsTotal} detail={`${data.submissionsAwaitingReview} awaiting review`} />
        <Tile label="Skills" value={data.skillsCount} />
        <Tile label="Certifications" value={data.certificationsCount} />
      </div>

      <section className="dashboard-block" aria-labelledby="task-breakdown">
        <h2 id="task-breakdown">Task progress</h2>
        <ProgressBar percent={data.progressPercent} />
        <ul className="breakdown">
          <li>To do: {data.tasksPending}</li>
          <li>In progress: {data.tasksInProgress}</li>
          <li>In review: {data.tasksInReview}</li>
          <li>Blocked: {data.tasksBlocked}</li>
          <li>Done: {data.tasksDone}</li>
        </ul>
      </section>

      <section className="dashboard-block" aria-labelledby="recent-projects">
        <h2 id="recent-projects">Recent projects</h2>
        {data.recentProjects.length === 0 ? (
          <EmptyState title="No projects yet.">
            <p>
              <Link to="/projects/new">Propose a project</Link> to start tracking tasks.
            </p>
          </EmptyState>
        ) : (
          <ul className="dashboard-list">
            {data.recentProjects.map((project) => (
              <li key={project.id}>
                <Link to={`/projects/${project.id}`}>{project.title}</Link>
                <Badge tone={project.status === 'ARCHIVED' ? 'warning' : 'success'}>
                  {PROJECT_STATUS_LABELS[project.status]}
                </Badge>
                <ProgressBar percent={project.progressPercent} />
              </li>
            ))}
          </ul>
        )}
      </section>

      <section className="dashboard-block" aria-labelledby="upcoming-tasks">
        <h2 id="upcoming-tasks">Upcoming due dates</h2>
        {data.upcomingTasks.length === 0 ? (
          <p className="muted">No open tasks have a due date.</p>
        ) : (
          <ul className="dashboard-list">
            {data.upcomingTasks.map((task) => (
              <li key={task.id}>
                <Link to={`/projects/${task.projectId}`}>{task.title}</Link>
                <span className="muted">
                  due {formatDate(task.dueDate)} · {TASK_STATUS_LABELS[task.status]}
                </span>
              </li>
            ))}
          </ul>
        )}
      </section>
    </>
  )
}

function FacultyOverview() {
  const { data, error, isLoading, refetch } = useGetFacultyDashboardQuery()

  if (isLoading) return <PageLoading />
  if (error || !data) {
    return <ErrorState message={describeError(error).message} onRetry={refetch} />
  }
  return <FacultyContent data={data} />
}

function FacultyContent({ data }: { data: FacultyDashboard }) {
  return (
    <>
      <div className="stat-grid">
        <Tile label="Supervised projects" value={data.assignedProjects} detail={`${data.activeProjects} active`} />
        <Tile label="Awaiting review" value={data.submissionsAwaitingReview} />
        <Tile label="Reviewed" value={data.submissionsReviewed} detail="changes requested or accepted" />
        <Tile label="Needing attention" value={data.projectsNeedingAttention} detail="have revisions awaiting review" />
      </div>

      <section className="dashboard-block" aria-labelledby="supervised-projects">
        <h2 id="supervised-projects">Supervised projects</h2>
        {data.projects.length === 0 ? (
          <EmptyState title="No supervised projects yet.">
            <p>An administrator assigns supervisors to projects.</p>
          </EmptyState>
        ) : (
          <ul className="dashboard-list">
            {data.projects.map((project) => (
              <li key={project.id}>
                <Link to={`/projects/${project.id}`}>{project.title}</Link>
                <Badge tone={project.awaitingReview > 0 ? 'warning' : 'neutral'}>
                  {project.awaitingReview > 0 ? `${project.awaitingReview} awaiting review` : 'Nothing waiting'}
                </Badge>
              </li>
            ))}
          </ul>
        )}
      </section>
    </>
  )
}

function AdminOverview() {
  const { data, error, isLoading, refetch } = useGetAdminDashboardQuery()

  if (isLoading) return <PageLoading />
  if (error || !data) {
    return <ErrorState message={describeError(error).message} onRetry={refetch} />
  }
  return <AdminContent data={data} />
}

const ROLES: Role[] = ['STUDENT', 'FACULTY', 'ADMIN']
const PROJECT_STATUSES: ProjectStatus[] = ['ACTIVE', 'ARCHIVED']

function AdminContent({ data }: { data: AdminDashboard }) {
  const users = ROLES.reduce((sum, role) => sum + data.activeUsersByRole[role], 0)
  const projects = PROJECT_STATUSES.reduce((sum, status) => sum + data.projectsByStatus[status], 0)

  return (
    <>
      <div className="stat-grid">
        <Tile label="Active accounts" value={users} />
        <Tile label="Projects" value={projects} detail={`${data.projectsByStatus.ACTIVE} active`} />
      </div>

      <section className="dashboard-block" aria-labelledby="users-by-role">
        <h2 id="users-by-role">Active accounts by role</h2>
        <ul className="breakdown">
          {ROLES.map((role) => (
            <li key={role}>
              {ROLE_LABELS[role]}: {data.activeUsersByRole[role]}
            </li>
          ))}
        </ul>
      </section>

      <section className="dashboard-block" aria-labelledby="projects-by-status">
        <h2 id="projects-by-status">Projects by status</h2>
        <ul className="breakdown">
          {PROJECT_STATUSES.map((status) => (
            <li key={status}>
              {PROJECT_STATUS_LABELS[status]}: {data.projectsByStatus[status]}
            </li>
          ))}
        </ul>
      </section>
    </>
  )
}
