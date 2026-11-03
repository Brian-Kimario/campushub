import { baseApi } from '../../api/baseApi'
import type { CalendarEntry } from '../../api/types'

export interface CalendarRangeArgs {
  from: string
  to: string
}

export const calendarApi = baseApi.injectEndpoints({
  endpoints: (builder) => ({
    listCalendarEntries: builder.query<CalendarEntry[], CalendarRangeArgs>({
      query: ({ from, to }) => ({ url: '/calendar', params: { from, to } }),
      providesTags: [{ type: 'Calendar', id: 'LIST' }],
    }),
  }),
})

export const { useListCalendarEntriesQuery } = calendarApi
