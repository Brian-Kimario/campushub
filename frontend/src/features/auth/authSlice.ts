import { createSlice, type PayloadAction } from '@reduxjs/toolkit'
import type { UserSummary } from '../../api/types'

export interface AuthState {
  accessToken: string | null
  user: UserSummary | null
  // False until the first restore attempt after a page load finishes (see SessionGate).
  initialized: boolean
}

const initialState: AuthState = { accessToken: null, user: null, initialized: false }

// The access token stays in memory only. A reload restores the session from the HttpOnly refresh cookie (ADR-025).
const authSlice = createSlice({
  name: 'auth',
  initialState,
  reducers: {
    sessionStarted(state, action: PayloadAction<{ accessToken: string; user: UserSummary }>) {
      state.accessToken = action.payload.accessToken
      state.user = action.payload.user
    },
    sessionChecked(state) {
      state.initialized = true
    },
    userUpdated(state, action: PayloadAction<UserSummary>) {
      state.user = action.payload
    },
    sessionCleared(state) {
      state.accessToken = null
      state.user = null
    },
  },
})

export const { sessionStarted, sessionChecked, userUpdated, sessionCleared } = authSlice.actions
export const authReducer = authSlice.reducer
