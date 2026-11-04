import { baseApi } from '../../api/baseApi'
import type { PageResponse, Role } from '../../api/types'

// Shape of GET and PATCH /users/me (API_CONTRACT §4).
export interface Profile {
  id: string
  email: string
  institutionalId?: string
  displayName: string
  role: Role
  status: 'ACTIVE' | 'DEACTIVATED'
  mustChangePassword: boolean
  departmentId?: string
  program?: string
  cohort?: string
  designation?: string
  bio?: string
}

// Only these fields can be changed by the signed-in user. Null clears a field.
export interface ProfilePatch {
  displayName: string
  departmentId: string | null
  program: string | null
  cohort: string | null
  designation: string | null
  bio: string | null
}

export interface Department {
  id: string
  code: string
  name: string
  active: boolean
}

export const profileApi = baseApi.injectEndpoints({
  endpoints: (builder) => ({
    getMyProfile: builder.query<Profile, void>({
      query: () => '/users/me',
      providesTags: [{ type: 'Profile', id: 'ME' }],
    }),
    updateMyProfile: builder.mutation<Profile, ProfilePatch>({
      query: (body) => ({ url: '/users/me', method: 'PATCH', body }),
      invalidatesTags: [{ type: 'Profile', id: 'ME' }],
    }),
    listActiveDepartments: builder.query<PageResponse<Department>, void>({
      query: () => ({ url: '/catalog/departments', params: { active: true, size: 100 } }),
    }),
  }),
})

export const { useGetMyProfileQuery, useUpdateMyProfileMutation, useListActiveDepartmentsQuery } = profileApi
