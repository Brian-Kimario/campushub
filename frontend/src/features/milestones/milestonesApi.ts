import { baseApi } from '../../api/baseApi'
import type { Milestone } from '../../api/types'

export interface CreateMilestoneArgs {
  projectId: string
  title: string
  /** An instant in UTC (ISO 8601). */
  dueAt: string
}

export interface UpdateMilestoneArgs {
  id: string
  projectId: string
  version: number
  /** Owner only. Omitted when not sent. */
  title?: string
  dueAt?: string
  /** Supervisor only. Omitted when not sent. */
  allowLate?: boolean
}

export interface CloseMilestoneArgs {
  id: string
  projectId: string
}

export const milestonesApi = baseApi.injectEndpoints({
  endpoints: (builder) => ({
    listMilestones: builder.query<Milestone[], string>({
      query: (projectId) => `/projects/${projectId}/milestones`,
      providesTags: (result) =>
        result
          ? [...result.map((milestone) => ({ type: 'Milestone' as const, id: milestone.id })), { type: 'Milestone', id: 'LIST' }]
          : [{ type: 'Milestone', id: 'LIST' }],
    }),
    createMilestone: builder.mutation<Milestone, CreateMilestoneArgs>({
      query: ({ projectId, ...body }) => ({ url: `/projects/${projectId}/milestones`, method: 'POST', body }),
      invalidatesTags: [{ type: 'Milestone', id: 'LIST' }],
    }),
    updateMilestone: builder.mutation<Milestone, UpdateMilestoneArgs>({
      query: ({ id, projectId: _projectId, ...body }) => ({ url: `/milestones/${id}`, method: 'PATCH', body }),
      invalidatesTags: (_result, _error, { id }) => [
        { type: 'Milestone', id },
        { type: 'Milestone', id: 'LIST' },
      ],
    }),
    closeMilestone: builder.mutation<Milestone, CloseMilestoneArgs>({
      query: ({ id }) => ({ url: `/milestones/${id}/close`, method: 'POST' }),
      invalidatesTags: (_result, _error, { id }) => [
        { type: 'Milestone', id },
        { type: 'Milestone', id: 'LIST' },
      ],
    }),
  }),
})

export const {
  useListMilestonesQuery,
  useCreateMilestoneMutation,
  useUpdateMilestoneMutation,
  useCloseMilestoneMutation,
} = milestonesApi
