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
  type AssignedProject,
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

function plural(count: number, word: string) {
  return `${count} ${word}${count === 1 ? '' : 's'}`
}

// One line of figures under the heading. Numbers read as a sentence, not as a grid of equal boxes.
function SummaryLine({ items }: { items: string[] }) {
  return (
    <ul className="summary-line" aria-label="Summary">
      {items.map((item) => (
        <li key={item}>{item}</li>
      ))}
    </ul>
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

// Students need to know what is due next, so deadlines lead. Progress and project status follow.
function StudentContent({ data }: { data: StudentDashboard }) {
  const hasProjects = data.projectsTotal > 0

  return (
    <>
      <SummaryLine
        items={[
          `${data.projectsActive} of ${plural(data.projectsTotal, 'project')} active`,
          `${data.tasksDone} of ${plural(data.tasksTotal, 'task')} done`,
          `${plural(data.submissionsAwaitingReview, 'submission')} awaiting review`,
        ]}
      />

      <section className="dashboard-section" aria-labelledby="upcoming-tasks">
        <h2 id="upcoming-tasks">Next deadlines</h2>
        {data.upcomingTasks.length === 0 ? (
          <p className="muted">No open tasks have a due date.</p>
        ) : (
          <ul className="deadline-list">
            {data.upcomingTasks.map((task) => (
              <li key={task.id}>
                <span className="deadline-date">{formatDate(task.dueDate)}</span>
                <span className="deadline-main">
                  <Link to={`/projects/${task.projectId}`}>{task.title}</Link>
                  <span className="muted">{TASK_STATUS_LABELS[task.status]}</span>
                </span>
              </li>
            ))}
          </ul>
        )}
      </section>

      <section className="dashboard-section" aria-labelledby="recent-projects">
        <h2 id="recent-projects">Your projects</h2>
        {!hasProjects ? (
          <EmptyState title="No projects yet">
            <p className="muted">Propose a project to start tracking tasks and deadlines.</p>
            <div className="actions">
              <Link className="button-link primary" to="/projects/new">
                Propose a project
              </Link>
            </div>
          </EmptyState>
        ) : (
          <ul className="project-progress-list">
            {data.recentProjects.map((project) => (
              <li key={project.id}>
                <div className="project-progress-head">
                  <Link to={`/projects/${project.id}`}>{project.title}</Link>
                  <Badge tone={project.status === 'ARCHIVED' ? 'warning' : 'success'}>
                    {PROJECT_STATUS_LABELS[project.status]}
                  </Badge>
                </div>
                <ProgressBar percent={project.progressPercent} />
              </li>
            ))}
          </ul>
        )}
      </section>

      <section className="dashboard-section" aria-labelledby="task-breakdown">
        <h2 id="task-breakdown">Task status</h2>
        <ProgressBar percent={data.progressPercent} />
        <ul className="breakdown">
          <li>To do: {data.tasksPending}</li>
          <li>In progress: {data.tasksInProgress}</li>
          <li>In review: {data.tasksInReview}</li>
          <li>Blocked: {data.tasksBlocked}</li>
          <li>Done: {data.tasksDone}</li>
        </ul>
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

// Faculty come to this page to review, so anything waiting for a decision is the first thing listed.
function FacultyContent({ data }: { data: FacultyDashboard }) {
  const waiting = data.projects
    .filter((project) => project.awaitingReview > 0)
    .sort((a, b) => b.awaitingReview - a.awaitingReview)

  return (
    <>
      <SummaryLine
        items={[
          `${plural(data.submissionsAwaitingReview, 'submission')} awaiting review`,
          `${data.submissionsReviewed} reviewed`,
          `${plural(data.assignedProjects, 'supervised project')}, ${data.activeProjects} active`,
        ]}
      />

      <section className="dashboard-section" aria-labelledby="awaiting-review">
        <h2 id="awaiting-review">Awaiting your review</h2>
        {waiting.length === 0 ? (
          <p className="muted">Nothing is waiting for a decision.</p>
        ) : (
          <ul className="review-queue">
            {waiting.map((project) => (
              <ReviewRow key={project.id} project={project} />
            ))}
          </ul>
        )}
      </section>

      <section className="dashboard-section" aria-labelledby="supervised-projects">
        <h2 id="supervised-projects">Supervised projects</h2>
        {data.projects.length === 0 ? (
          <EmptyState title="No supervised projects yet">
            <p className="muted">An administrator assigns supervisors to projects.</p>
          </EmptyState>
        ) : (
          <ul className="project-progress-list">
            {data.projects.map((project) => (
              <li key={project.id}>
                <div className="project-progress-head">
                  <Link to={`/projects/${project.id}`}>{project.title}</Link>
                  <Badge tone={project.status === 'ARCHIVED' ? 'warning' : 'success'}>
                    {PROJECT_STATUS_LABELS[project.status]}
                  </Badge>
                </div>
              </li>
            ))}
          </ul>
        )}
      </section>
    </>
  )
}

function ReviewRow({ project }: { project: AssignedProject }) {
  return (
    <li className="review-row">
      <span className="review-row-main">
        <Link to={`/projects/${project.id}`}>{project.title}</Link>
        <span className="muted">{plural(project.awaitingReview, 'submission')} waiting</span>
      </span>
      <Link className="button-link primary" to={`/projects/${project.id}#submissions`}>
        Review
      </Link>
    </li>
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
      <SummaryLine
        items={[
          `${plural(users, 'active account')}`,
          `${plural(projects, 'project')}, ${data.projectsByStatus.ACTIVE} active`,
        ]}
      />

      <p>
        <Link className="button-link primary" to="/admin/users">
          Manage user accounts
        </Link>
      </p>

      <section className="dashboard-section" aria-labelledby="users-by-role">
        <h2 id="users-by-role">Active accounts by role</h2>
        <ul className="breakdown">
          {ROLES.map((role) => (
            <li key={role}>
              {ROLE_LABELS[role]}: {data.activeUsersByRole[role]}
            </li>
          ))}
        </ul>
      </section>

      <section className="dashboard-section" aria-labelledby="projects-by-status">
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
