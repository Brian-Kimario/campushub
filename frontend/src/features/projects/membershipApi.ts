import { baseApi } from '../../api/baseApi'
import type { Invitation, Member, PageResponse } from '../../api/types'

export interface ProjectInvitationsArgs {
  projectId: string
  page: number
  size: number
}

export interface MyInvitationsArgs {
  page: number
  size: number
}

export const membershipApi = baseApi.injectEndpoints({
  endpoints: (builder) => ({
    listMembers: builder.query<Member[], string>({
      query: (projectId) => `/projects/${projectId}/members`,
      providesTags: (_result, _error, projectId) => [{ type: 'Membership', id: projectId }],
    }),
    removeMember: builder.mutation<void, { projectId: string; userId: string }>({
      query: ({ projectId, userId }) => ({ url: `/projects/${projectId}/members/${userId}`, method: 'DELETE' }),
      invalidatesTags: (_result, _error, { projectId }) => [
        { type: 'Membership', id: projectId },
        { type: 'Project', id: projectId },
      ],
    }),
    listProjectInvitations: builder.query<PageResponse<Invitation>, ProjectInvitationsArgs>({
      query: ({ projectId, page, size }) => ({ url: `/projects/${projectId}/invitations`, params: { page, size } }),
      providesTags: (_result, _error, { projectId }) => [{ type: 'Invitation', id: projectId }],
    }),
    inviteMember: builder.mutation<Invitation, { projectId: string; inviteeEmail: string }>({
      query: ({ projectId, inviteeEmail }) => ({
        url: `/projects/${projectId}/invitations`,
        method: 'POST',
        body: { inviteeEmail },
      }),
      invalidatesTags: (_result, _error, { projectId }) => [{ type: 'Invitation', id: projectId }],
    }),
    listMyInvitations: builder.query<PageResponse<Invitation>, MyInvitationsArgs>({
      query: ({ page, size }) => ({ url: '/invitations/mine', params: { page, size } }),
      providesTags: [{ type: 'Invitation', id: 'MINE' }],
    }),
    acceptInvitation: builder.mutation<Invitation, string>({
      query: (id) => ({ url: `/invitations/${id}/accept`, method: 'POST' }),
      invalidatesTags: [
        { type: 'Invitation', id: 'MINE' },
        { type: 'Project', id: 'LIST' },
      ],
    }),
    declineInvitation: builder.mutation<Invitation, string>({
      query: (id) => ({ url: `/invitations/${id}/decline`, method: 'POST' }),
      invalidatesTags: [{ type: 'Invitation', id: 'MINE' }],
    }),
  }),
})

export const {
  useListMembersQuery,
  useRemoveMemberMutation,
  useListProjectInvitationsQuery,
  useInviteMemberMutation,
  useListMyInvitationsQuery,
  useAcceptInvitationMutation,
  useDeclineInvitationMutation,
} = membershipApi
