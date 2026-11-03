import {
  createApi,
  fetchBaseQuery,
  type BaseQueryFn,
  type FetchArgs,
  type FetchBaseQueryError,
} from '@reduxjs/toolkit/query/react'
import type { RootState } from '../app/store'
import { sessionCleared, sessionStarted } from '../features/auth/authSlice'
import { API_BASE_URL } from './apiConfig'
import { refreshSession } from './session'

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

// Sign-in and the session endpoints handle their own failures. Retrying them could loop or hide the real error.
const SESSION_PATHS = new Set(['/auth/login', '/auth/refresh', '/auth/logout'])

function isUnauthenticated(error: FetchBaseQueryError | undefined): boolean {
  const body = error?.data as { errorCode?: string } | undefined
  // UNAUTHENTICATED means the access token is missing or expired. A wrong password is also a 401 but keeps the session.
  return error?.status === 401 && body?.errorCode === 'UNAUTHENTICATED'
}

const baseQueryWithSessionCheck: BaseQueryFn<string | FetchArgs, unknown, FetchBaseQueryError> = async (
  args,
  api,
  extraOptions,
) => {
  const sentToken = (api.getState() as RootState).auth.accessToken
  const result = await rawBaseQuery(args, api, extraOptions)
  const path = typeof args === 'string' ? args : args.url
  if (!isUnauthenticated(result.error) || SESSION_PATHS.has(path)) {
    return result
  }

  // Another request may already have refreshed the token while this one was in flight.
  const current = (api.getState() as RootState).auth.accessToken
  if (current && current !== sentToken) {
    return rawBaseQuery(args, api, extraOptions)
  }

  const session = await refreshSession()
  if (!session) {
    api.dispatch(sessionCleared())
    return result
  }
  api.dispatch(sessionStarted({ accessToken: session.accessToken, user: session.user }))
  const retried = await rawBaseQuery(args, api, extraOptions)
  if (isUnauthenticated(retried.error)) {
    api.dispatch(sessionCleared())
  }
  return retried
}

export const baseApi = createApi({
  reducerPath: 'api',
  baseQuery: baseQueryWithSessionCheck,
  tagTypes: [
    'Project',
    'Task',
    'Milestone',
    'Submission',
    'Faculty',
    'Skill',
    'OwnSkill',
    'Certification',
    'Dashboard',
    'Membership',
    'Invitation',
    'Calendar',
  ],
  endpoints: () => ({}),
})
