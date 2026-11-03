import {
  createApi,
  fetchBaseQuery,
  type BaseQueryFn,
  type FetchArgs,
  type FetchBaseQueryError,
} from '@reduxjs/toolkit/query/react'
import type { RootState } from '../app/store'
import { sessionCleared } from '../features/auth/authSlice'

// Every API route is under /api/v1 (API_CONTRACT header). The Vite dev proxy forwards this prefix unchanged.
export const API_BASE_URL = '/api/v1'

const rawBaseQuery = fetchBaseQuery({
  baseUrl: API_BASE_URL,
  prepareHeaders: (headers, { getState }) => {
    // The access token lives only in Redux memory (ARCHITECTURE §9). It is never written to storage.
    const token = (getState() as RootState).auth.accessToken
    if (token) {
      headers.set('authorization', `Bearer ${token}`)
    }
    return headers
  },
})

const baseQueryWithSessionCheck: BaseQueryFn<string | FetchArgs, unknown, FetchBaseQueryError> = async (
  args,
  api,
  extraOptions,
) => {
  const result = await rawBaseQuery(args, api, extraOptions)
  const body = result.error?.data as { errorCode?: string } | undefined
  // UNAUTHENTICATED means the session has ended. A wrong password is also a 401 but keeps the session.
  if (result.error?.status === 401 && body?.errorCode === 'UNAUTHENTICATED') {
    api.dispatch(sessionCleared())
  }
  return result
}

export const baseApi = createApi({
  reducerPath: 'api',
  baseQuery: baseQueryWithSessionCheck,
  tagTypes: ['Project', 'Task', 'Submission', 'Faculty'],
  endpoints: () => ({}),
})
