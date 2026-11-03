import { baseApi } from '../../api/baseApi'
import type { ProjectStatus, Role, TaskStatus } from '../../api/types'

export interface ProjectProgress {
  id: string
  title: string
  status: ProjectStatus
  taskTotal: number
  taskDone: number
  progressPercent: number | null
  createdAt: string
}

export interface UpcomingTask {
  id: string
  title: string
  projectId: string
  dueDate: string
  status: TaskStatus
}

export interface StudentDashboard {
  projectsTotal: number
  projectsActive: number
  tasksTotal: number
  tasksPending: number
  tasksInProgress: number
  tasksInReview: number
  tasksBlocked: number
  tasksDone: number
  progressPercent: number | null
  submissionsTotal: number
  submissionsAwaitingReview: number
  skillsCount: number
  certificationsCount: number
  recentProjects: ProjectProgress[]
  upcomingTasks: UpcomingTask[]
}

export interface AssignedProject {
  id: string
  title: string
  status: ProjectStatus
  awaitingReview: number
}

export interface FacultyDashboard {
  assignedProjects: number
  activeProjects: number
  submissionsAwaitingReview: number
  submissionsReviewed: number
  projectsNeedingAttention: number
  projects: AssignedProject[]
}

export interface AdminDashboard {
  activeUsersByRole: Record<Role, number>
  projectsByStatus: Record<ProjectStatus, number>
}

export const dashboardApi = baseApi.injectEndpoints({
  endpoints: (builder) => ({
    getStudentDashboard: builder.query<StudentDashboard, void>({
      query: () => ({ url: '/users/me/dashboard/student' }),
      providesTags: [{ type: 'Dashboard', id: 'STUDENT' }],
    }),
    getFacultyDashboard: builder.query<FacultyDashboard, void>({
      query: () => ({ url: '/users/me/dashboard/faculty' }),
      providesTags: [{ type: 'Dashboard', id: 'FACULTY' }],
    }),
    getAdminDashboard: builder.query<AdminDashboard, void>({
      query: () => ({ url: '/users/me/dashboard/admin' }),
      providesTags: [{ type: 'Dashboard', id: 'ADMIN' }],
    }),
  }),
})

export const {
  useGetStudentDashboardQuery,
  useGetFacultyDashboardQuery,
  useGetAdminDashboardQuery,
} = dashboardApi
