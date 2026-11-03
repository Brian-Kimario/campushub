import { createSlice, type PayloadAction } from '@reduxjs/toolkit'
import type { ProjectStatus } from '../../api/types'

export type ProjectStatusFilter = 'ALL' | ProjectStatus

export interface ProjectFiltersState {
  status: ProjectStatusFilter
  page: number
}

const projectFiltersSlice = createSlice({
  name: 'projectFilters',
  initialState: { status: 'ALL', page: 0 } as ProjectFiltersState,
  reducers: {
    statusChanged(state, action: PayloadAction<ProjectStatusFilter>) {
      state.status = action.payload
      state.page = 0
    },
    pageChanged(state, action: PayloadAction<number>) {
      state.page = action.payload
    },
  },
})

export const { statusChanged, pageChanged } = projectFiltersSlice.actions
export const projectFiltersReducer = projectFiltersSlice.reducer
