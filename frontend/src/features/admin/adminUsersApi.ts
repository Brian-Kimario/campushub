import { baseApi } from '../../api/baseApi'
import type { PageResponse, Role } from '../../api/types'

export type AccountStatus = 'ACTIVE' | 'DEACTIVATED'

export interface AdminUser {
  id: string
  email: string
  institutionalId: string | null
  displayName: string
  role: Role
  status: AccountStatus
  mustChangePassword: boolean
  departmentId: string | null
  createdAt: string
  /** Present only in the response that creates the account or resets its password. Shown once. */
  temporaryPassword?: string | null
}

export interface AdminUserListArgs {
  role?: Role | ''
  status?: AccountStatus | ''
  q?: string
  page: number
  size: number
}

export interface CreateAdminUserArgs {
  email: string
  institutionalId?: string | null
  displayName: string
  role: Role
  departmentId?: string | null
}

export const adminUsersApi = baseApi.injectEndpoints({
  endpoints: (builder) => ({
    listAdminUsers: builder.query<PageResponse<AdminUser>, AdminUserListArgs>({
      query: ({ role, status, q, page, size }) => ({
        url: '/admin/users',
        params: { role: role || undefined, status: status || undefined, q: q || undefined, page, size },
      }),
      providesTags: [{ type: 'AdminUser', id: 'LIST' }],
    }),
    createAdminUser: builder.mutation<AdminUser, CreateAdminUserArgs>({
      query: (body) => ({ url: '/admin/users', method: 'POST', body }),
      invalidatesTags: [{ type: 'AdminUser', id: 'LIST' }],
    }),
    deactivateAdminUser: builder.mutation<AdminUser, string>({
      query: (id) => ({ url: `/admin/users/${id}/deactivate`, method: 'POST' }),
      invalidatesTags: [{ type: 'AdminUser', id: 'LIST' }],
    }),
    reactivateAdminUser: builder.mutation<AdminUser, string>({
      query: (id) => ({ url: `/admin/users/${id}/reactivate`, method: 'POST' }),
      invalidatesTags: [{ type: 'AdminUser', id: 'LIST' }],
    }),
    resetAdminUserPassword: builder.mutation<AdminUser, string>({
      query: (id) => ({ url: `/admin/users/${id}/reset-password`, method: 'POST' }),
      invalidatesTags: [{ type: 'AdminUser', id: 'LIST' }],
    }),
  }),
})

export const {
  useListAdminUsersQuery,
  useCreateAdminUserMutation,
  useDeactivateAdminUserMutation,
  useReactivateAdminUserMutation,
  useResetAdminUserPasswordMutation,
} = adminUsersApi
