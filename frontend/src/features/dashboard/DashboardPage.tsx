import { Link } from 'react-router'
import { describeError } from '../../api/problem'
import { useAppSelector } from '../../app/hooks'
import {
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
import {
  CountBreakdown,
  DashboardPanel,
  MetricTile,
  ProgressIndicator,
  StatusDistribution,
  type CountRow,
} from './DashboardParts'
import type { ProjectStatus, Role, TaskStatus } from '../../api/types'

export default function DashboardPage() {
  const role = useAppSelector((state) => state.auth.user?.role)

  if (role === 'STUDENT') return <StudentOverview />
  if (role === 'FACULTY') return <FacultyOverview />
  if (role === 'ADMIN') return <AdminOverview />

  return (
    <section>
      <div className="page-header">
        <h1>Dashboard</h1>
      </div>
    </section>
  )
}

function plural(count: number, word: string) {
  return `${count} ${word}${count === 1 ? '' : 's'}`
}

const DAY_MS = 86_400_000

// Due dates are calendar dates (YYYY-MM-DD), so compare them as UTC midnights to avoid zone drift.
function daysUntil(localDate: string): number {
  const [year, month, day] = localDate.split('-').map(Number)
  const now = new Date()
  const dueUtc = Date.UTC(year, month - 1, day)
  const todayUtc = Date.UTC(now.getFullYear(), now.getMonth(), now.getDate())
  return Math.round((dueUtc - todayUtc) / DAY_MS)
}

function dueLabel(days: number): string {
  if (days < 0) return 'Past due'
  if (days === 0) return 'Due today'
  if (days === 1) return 'Due tomorrow'
  return `Due in ${days} days`
}

function dayAndMonth(localDate: string) {
  const [year, month, day] = localDate.split('-').map(Number)
  const date = new Date(year, month - 1, day)
  return {
    day: String(day),
    month: date.toLocaleDateString(undefined, { month: 'short' }),
  }
}

const TASK_TONE: Record<TaskStatus, 'neutral' | 'warning' | 'danger'> = {
  TODO: 'neutral',
  IN_PROGRESS: 'neutral',
  IN_REVIEW: 'warning',
  BLOCKED: 'danger',
  DONE: 'neutral',
}

function StudentOverview() {
  const { data, error, isLoading, refetch } = useGetStudentDashboardQuery()

  return (
    <section>
      <div className="page-header dashboard-header">
        <div>
          <h1>Dashboard</h1>
          <p className="muted dashboard-subtitle">Upcoming deadlines, task progress and submissions.</p>
        </div>
        <Link className="button-link" to="/projects">
          View projects
        </Link>
      </div>

      {isLoading && <PageLoading />}
      {!isLoading && (error || !data) && (
        <ErrorState message={describeError(error).message} onRetry={refetch} />
      )}
      {!isLoading && data && <StudentContent data={data} />}
    </section>
  )
}

// Metrics first, then the deadline queue beside task status, then project progress.
function StudentContent({ data }: { data: StudentDashboard }) {
  const openTasks = data.tasksTotal - data.tasksDone

  return (
    <>
      <div className="metric-strip">
        <MetricTile
          label="Active projects"
          value={data.projectsActive}
          detail={`of ${plural(data.projectsTotal, 'project')}`}
        />
        <MetricTile
          label="Open tasks"
          value={openTasks}
          detail={`${data.tasksInProgress} in progress`}
        />
        <MetricTile
          label="Tasks completed"
          value={`${data.tasksDone} / ${data.tasksTotal}`}
          detail={
            <ProgressIndicator percent={data.progressPercent} done={data.tasksDone} total={data.tasksTotal} />
          }
        />
        <MetricTile
          label="Awaiting review"
          value={data.submissionsAwaitingReview}
          detail={`of ${plural(data.submissionsTotal, 'submission')}`}
        />
      </div>

      <div className="dash-columns">
        <DashboardPanel
          id="upcoming-tasks"
          title="Next deadlines"
          action={
            <Link className="dash-panel-link" to="/projects">
              All projects
            </Link>
          }
        >
          {data.upcomingTasks.length === 0 ? (
            <p className="muted">No open tasks have a due date.</p>
          ) : (
            <ol className="upcoming-list">
              {data.upcomingTasks.map((task) => {
                const days = daysUntil(task.dueDate)
                const date = dayAndMonth(task.dueDate)
                return (
                  <li key={task.id} className="upcoming-item">
                    <time className="upcoming-day" dateTime={task.dueDate}>
                      <span className="upcoming-day-number">{date.day}</span>
                      <span className="upcoming-day-month">{date.month}</span>
                    </time>
                    <div className="upcoming-body">
                      <Link to={`/projects/${task.projectId}`}>{task.title}</Link>
                      <div className="upcoming-meta">
                        <Badge tone={TASK_TONE[task.status]}>{TASK_STATUS_LABELS[task.status]}</Badge>
                        <span className={days <= 1 ? 'upcoming-urgent' : undefined}>{dueLabel(days)}</span>
                      </div>
                    </div>
                  </li>
                )
              })}
            </ol>
          )}
        </DashboardPanel>

        <DashboardPanel id="task-status" title="Task status">
          <StatusDistribution
            label="Task status"
            segments={[
              { label: 'Done', value: data.tasksDone, tone: 'done' },
              { label: 'In progress', value: data.tasksInProgress, tone: 'progress' },
              { label: 'In review', value: data.tasksInReview, tone: 'review' },
              { label: 'To do', value: data.tasksPending, tone: 'todo' },
              { label: 'Blocked', value: data.tasksBlocked, tone: 'blocked' },
            ]}
          />
        </DashboardPanel>
      </div>

      <DashboardPanel
        id="recent-projects"
        title="Recent projects"
        full
        action={
          <Link className="dash-panel-link" to="/projects">
            All projects
          </Link>
        }
      >
        {data.projectsTotal === 0 ? (
          <EmptyState title="No projects yet">
            <p className="muted">Propose a project to start tracking tasks and deadlines.</p>
            <div className="actions">
              <Link className="button-link primary" to="/projects/new">
                Propose a project
              </Link>
            </div>
          </EmptyState>
        ) : (
          <ul className="project-cards">
            {data.recentProjects.map((project) => (
              <li key={project.id} className="project-card">
                <div className="project-card-head">
                  <Link to={`/projects/${project.id}`}>{project.title}</Link>
                  <Badge tone={project.status === 'ARCHIVED' ? 'warning' : 'success'}>
                    {PROJECT_STATUS_LABELS[project.status]}
                  </Badge>
                </div>
                <ProgressIndicator
                  percent={project.progressPercent}
                  done={project.taskDone}
                  total={project.taskTotal}
                />
              </li>
            ))}
          </ul>
        )}
      </DashboardPanel>
    </>
  )
}

function FacultyOverview() {
  const { data, error, isLoading, refetch } = useGetFacultyDashboardQuery()

  return (
    <section>
      <div className="page-header dashboard-header">
        <div>
          <h1>Dashboard</h1>
          <p className="muted dashboard-subtitle">Submissions waiting for a decision and the projects you supervise.</p>
        </div>
      </div>

      {isLoading && <PageLoading />}
      {!isLoading && (error || !data) && (
        <ErrorState message={describeError(error).message} onRetry={refetch} />
      )}
      {!isLoading && data && <FacultyContent data={data} />}
    </section>
  )
}

// The review queue is the work faculty came to do, so it is the largest panel. Supervised projects sit beside it on desktop.
function FacultyContent({ data }: { data: FacultyDashboard }) {
  const waiting = data.projects
    .filter((project) => project.awaitingReview > 0)
    .sort((a, b) => b.awaitingReview - a.awaitingReview)
  // The server lists only the most recent supervised projects, so the queue can be incomplete.
  const listingIsPartial = data.assignedProjects > data.projects.length

  return (
    <>
      <div className="metric-strip metric-strip-three">
        <MetricTile
          label="Awaiting review"
          value={data.submissionsAwaitingReview}
          detail={`in ${plural(data.projectsNeedingAttention, 'project')}`}
        />
        <MetricTile label="Reviewed" value={data.submissionsReviewed} detail="Accepted or changes requested" />
        <MetricTile
          label="Active projects"
          value={data.activeProjects}
          detail={`of ${plural(data.assignedProjects, 'supervised project')}`}
        />
      </div>

      <div className="dash-columns">
        <DashboardPanel id="review-queue" title="Awaiting your review">
          {waiting.length === 0 ? (
            <p className="muted">Nothing is waiting for a decision.</p>
          ) : (
            <ul className="review-list">
              {waiting.map((project) => (
                <li key={project.id} className="review-item">
                  <div className="review-item-main">
                    <Link to={`/projects/${project.id}`}>{project.title}</Link>
                    <span className="muted">{plural(project.awaitingReview, 'submission')} waiting</span>
                  </div>
                  <Link className="button-link primary" to={`/projects/${project.id}#submissions`}>
                    Review
                  </Link>
                </li>
              ))}
            </ul>
          )}
          {listingIsPartial && (
            <p className="muted dash-note">
              Showing {data.projects.length} of {plural(data.assignedProjects, 'supervised project')}.
            </p>
          )}
        </DashboardPanel>

        <DashboardPanel id="supervised-projects" title="Supervised projects">
          {data.projects.length === 0 ? (
            <EmptyState title="No supervised projects yet">
              <p className="muted">An administrator assigns supervisors to projects.</p>
            </EmptyState>
          ) : (
            <ul className="supervised-list">
              {data.projects.map((project) => (
                <li key={project.id} className="supervised-item">
                  <div className="supervised-item-head">
                    <Link to={`/projects/${project.id}`}>{project.title}</Link>
                    <Badge tone={project.status === 'ARCHIVED' ? 'warning' : 'success'}>
                      {PROJECT_STATUS_LABELS[project.status]}
                    </Badge>
                  </div>
                  <span className="muted">
                    {project.awaitingReview > 0
                      ? `${plural(project.awaitingReview, 'submission')} waiting`
                      : 'Nothing waiting'}
                  </span>
                </li>
              ))}
            </ul>
          )}
        </DashboardPanel>
      </div>
    </>
  )
}

function AdminOverview() {
  const { data, error, isLoading, refetch } = useGetAdminDashboardQuery()

  return (
    <section>
      <div className="page-header dashboard-header">
        <div>
          <h1>Dashboard</h1>
          <p className="muted dashboard-subtitle">Active accounts and projects across CampusHub.</p>
        </div>
        <Link className="button-link primary" to="/admin/users">
          Manage user accounts
        </Link>
      </div>

      {isLoading && <PageLoading />}
      {!isLoading && (error || !data) && (
        <ErrorState message={describeError(error).message} onRetry={refetch} />
      )}
      {!isLoading && data && <AdminContent data={data} />}
    </section>
  )
}

const ROLES: Role[] = ['STUDENT', 'FACULTY', 'ADMIN']
const PROJECT_STATUSES: ProjectStatus[] = ['ACTIVE', 'ARCHIVED']

// Only the counts the API returns. There are no trends or term figures, because the service does not compute them.
function AdminContent({ data }: { data: AdminDashboard }) {
  const accounts = ROLES.reduce((sum, role) => sum + data.activeUsersByRole[role], 0)
  const activeProjects = data.projectsByStatus.ACTIVE
  const archivedProjects = data.projectsByStatus.ARCHIVED
  const roleRows: CountRow[] = ROLES.map((role) => ({
    label: ROLE_LABELS[role],
    value: data.activeUsersByRole[role],
  }))
  const statusRows: CountRow[] = PROJECT_STATUSES.map((status) => ({
    label: PROJECT_STATUS_LABELS[status],
    value: data.projectsByStatus[status],
  }))

  return (
    <>
      <div className="metric-strip metric-strip-three">
        <MetricTile label="Active accounts" value={accounts} detail="Students, faculty and administrators" />
        <MetricTile
          label="Active projects"
          value={activeProjects}
          detail={`of ${plural(activeProjects + archivedProjects, 'project')}`}
        />
        <MetricTile label="Archived projects" value={archivedProjects} detail="Not currently active" />
      </div>

      <div className="dash-columns dash-columns-equal">
        <DashboardPanel id="accounts-by-role" title="Active accounts by role">
          <CountBreakdown rows={roleRows} />
        </DashboardPanel>
        <DashboardPanel id="projects-by-status" title="Projects by status">
          <CountBreakdown rows={statusRows} />
        </DashboardPanel>
      </div>
    </>
  )
}
