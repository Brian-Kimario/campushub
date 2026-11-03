import { baseApi } from '../../api/baseApi'
import type { PageResponse, Task, TaskPriority, TaskStatus } from '../../api/types'

export interface TaskListArgs {
  projectId: string
  page: number
  size: number
}

export interface TaskFields {
  title: string
  description: string | null
  priority: TaskPriority
  dueDate: string | null
}

export interface CreateTaskArgs extends TaskFields {
  projectId: string
}

export interface UpdateTaskArgs extends TaskFields {
  id: string
  version: number
}

export interface TransitionTaskArgs {
  id: string
  to: TaskStatus
  version: number
  blockedReason?: string
}

export const tasksApi = baseApi.injectEndpoints({
  endpoints: (builder) => ({
    listTasks: builder.query<PageResponse<Task>, TaskListArgs>({
      query: ({ projectId, page, size }) => ({
        url: `/projects/${projectId}/tasks`,
        params: { page, size },
      }),
      providesTags: (result) =>
        result
          ? [...result.content.map((task) => ({ type: 'Task' as const, id: task.id })), { type: 'Task', id: 'LIST' }]
          : [{ type: 'Task', id: 'LIST' }],
    }),
    createTask: builder.mutation<Task, CreateTaskArgs>({
      query: ({ projectId, ...body }) => ({ url: `/projects/${projectId}/tasks`, method: 'POST', body }),
      invalidatesTags: [{ type: 'Task', id: 'LIST' }],
    }),
    updateTask: builder.mutation<Task, UpdateTaskArgs>({
      query: ({ id, ...body }) => ({ url: `/tasks/${id}`, method: 'PATCH', body }),
      invalidatesTags: (_result, _error, { id }) => [
        { type: 'Task', id },
        { type: 'Task', id: 'LIST' },
      ],
    }),
    transitionTask: builder.mutation<Task, TransitionTaskArgs>({
      query: ({ id, ...body }) => ({ url: `/tasks/${id}/transitions`, method: 'POST', body }),
      invalidatesTags: (_result, _error, { id }) => [
        { type: 'Task', id },
        { type: 'Task', id: 'LIST' },
      ],
    }),
  }),
})

export const {
  useListTasksQuery,
  useCreateTaskMutation,
  useUpdateTaskMutation,
  useTransitionTaskMutation,
} = tasksApi
