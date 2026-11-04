import { baseApi } from '../../api/baseApi'
import { CLIENT_HEADER_NAME, CLIENT_HEADER_VALUE } from '../../api/apiConfig'
import type { LoginResponse } from '../../api/types'

export interface LoginRequest {
  email: string
  password: string
}

export interface ChangePasswordRequest {
  currentPassword: string
  newPassword: string
}

// The caller dispatches sessionStarted or sessionCleared, so the auth flow stays visible in one place.
export const authApi = baseApi.injectEndpoints({
  endpoints: (builder) => ({
    login: builder.mutation<LoginResponse, LoginRequest>({
      query: (body) => ({ url: '/auth/login', method: 'POST', body }),
    }),
    logout: builder.mutation<void, void>({
      query: () => ({ url: '/auth/logout', method: 'POST', headers: { [CLIENT_HEADER_NAME]: CLIENT_HEADER_VALUE } }),
    }),
    changePassword: builder.mutation<void, ChangePasswordRequest>({
      query: (body) => ({ url: '/auth/change-password', method: 'POST', body }),
    }),
  }),
})

export const { useLoginMutation, useLogoutMutation, useChangePasswordMutation } = authApi
