import { createSlice, type PayloadAction } from '@reduxjs/toolkit'
import type { UserSummary } from '../../api/types'

export interface AuthState {
  accessToken: string | null
  user: UserSummary | null
}

const initialState: AuthState = { accessToken: null, user: null }

// The token stays in memory only. A reload ends the session (ADR-004: no refresh tokens in the MVP).
const authSlice = createSlice({
  name: 'auth',
  initialState,
  reducers: {
    sessionStarted(state, action: PayloadAction<{ accessToken: string; user: UserSummary }>) {
      state.accessToken = action.payload.accessToken
      state.user = action.payload.user
    },
    userUpdated(state, action: PayloadAction<UserSummary>) {
      state.user = action.payload
    },
    sessionCleared() {
      return initialState
    },
  },
})

export const { sessionStarted, userUpdated, sessionCleared } = authSlice.actions
export const authReducer = authSlice.reducer
