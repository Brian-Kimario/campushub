import { baseApi } from '../../api/baseApi'
import type { PageResponse, Project, ProjectStatus, UserSummary } from '../../api/types'

export interface ProjectListArgs {
  status?: ProjectStatus
  page: number
  size: number
}

// Fields are optional because the API clears a field when it is sent as null (API_CONTRACT §5).
export interface ProjectFields {
  title: string
  description: string | null
  subject: string | null
  startDate: string | null
  dueDate: string | null
}

export interface UpdateProjectArgs extends Partial<ProjectFields> {
  id: string
  version: number
}

export interface AssignSupervisorArgs {
  id: string
  supervisorId: string
  version: number
}

export interface ArchiveProjectArgs {
  id: string
  version: number
}

export const projectsApi = baseApi.injectEndpoints({
  endpoints: (builder) => ({
    listProjects: builder.query<PageResponse<Project>, ProjectListArgs>({
      query: ({ status, page, size }) => ({
        url: '/projects',
        params: { status, page, size },
      }),
      providesTags: (result) =>
        result
          ? [...result.content.map((project) => ({ type: 'Project' as const, id: project.id })), { type: 'Project', id: 'LIST' }]
          : [{ type: 'Project', id: 'LIST' }],
    }),
    getProject: builder.query<Project, string>({
      query: (id) => `/projects/${id}`,
      providesTags: (_result, _error, id) => [{ type: 'Project', id }],
    }),
    createProject: builder.mutation<Project, ProjectFields>({
      query: (body) => ({ url: '/projects', method: 'POST', body }),
      invalidatesTags: [{ type: 'Project', id: 'LIST' }],
    }),
    updateProject: builder.mutation<Project, UpdateProjectArgs>({
      query: ({ id, ...body }) => ({ url: `/projects/${id}`, method: 'PATCH', body }),
      invalidatesTags: (_result, _error, { id }) => [
        { type: 'Project', id },
        { type: 'Project', id: 'LIST' },
      ],
    }),
    assignSupervisor: builder.mutation<Project, AssignSupervisorArgs>({
      query: ({ id, ...body }) => ({ url: `/projects/${id}/supervisor`, method: 'PUT', body }),
      invalidatesTags: (_result, _error, { id }) => [
        { type: 'Project', id },
        { type: 'Project', id: 'LIST' },
      ],
    }),
    archiveProject: builder.mutation<Project, ArchiveProjectArgs>({
      query: ({ id, version }) => ({ url: `/projects/${id}/archive`, method: 'POST', body: { version } }),
      invalidatesTags: (_result, _error, { id }) => [
        { type: 'Project', id },
        { type: 'Project', id: 'LIST' },
      ],
    }),
    listFaculty: builder.query<UserSummary[], void>({
      query: () => '/users/faculty',
      providesTags: [{ type: 'Faculty', id: 'LIST' }],
    }),
  }),
})

export const {
  useListProjectsQuery,
  useGetProjectQuery,
  useCreateProjectMutation,
  useUpdateProjectMutation,
  useAssignSupervisorMutation,
  useArchiveProjectMutation,
  useListFacultyQuery,
} = projectsApi
