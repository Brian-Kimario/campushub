import { configureStore } from '@reduxjs/toolkit'
import { setupListeners } from '@reduxjs/toolkit/query'
import { baseApi } from '../api/baseApi'
import { authReducer } from '../features/auth/authSlice'
import { projectFiltersReducer } from '../features/projects/projectFiltersSlice'

export const store = configureStore({
  reducer: {
    auth: authReducer,
    projectFilters: projectFiltersReducer,
    [baseApi.reducerPath]: baseApi.reducer,
  },
  middleware: (getDefaultMiddleware) => getDefaultMiddleware().concat(baseApi.middleware),
})

setupListeners(store.dispatch)

export type RootState = ReturnType<typeof store.getState>
export type AppDispatch = typeof store.dispatch
