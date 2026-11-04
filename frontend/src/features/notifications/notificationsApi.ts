import { baseApi } from '../../api/baseApi'
import type { Notification, PageResponse, UnreadCount } from '../../api/types'

export interface NotificationsArgs {
  unreadOnly: boolean
  page: number
  size: number
}

export const notificationsApi = baseApi.injectEndpoints({
  endpoints: (builder) => ({
    listNotifications: builder.query<PageResponse<Notification>, NotificationsArgs>({
      query: ({ unreadOnly, page, size }) => ({ url: '/notifications', params: { unreadOnly, page, size } }),
      providesTags: [{ type: 'Notification', id: 'LIST' }],
    }),
    unreadNotificationCount: builder.query<UnreadCount, void>({
      query: () => '/notifications/unread-count',
      providesTags: [{ type: 'Notification', id: 'COUNT' }],
    }),
    markNotificationRead: builder.mutation<Notification, string>({
      query: (id) => ({ url: `/notifications/${id}/read`, method: 'POST' }),
      invalidatesTags: [{ type: 'Notification', id: 'LIST' }, { type: 'Notification', id: 'COUNT' }],
    }),
    markAllNotificationsRead: builder.mutation<{ updated: number }, void>({
      query: () => ({ url: '/notifications/read-all', method: 'POST' }),
      invalidatesTags: [{ type: 'Notification', id: 'LIST' }, { type: 'Notification', id: 'COUNT' }],
    }),
  }),
})

export const {
  useListNotificationsQuery,
  useUnreadNotificationCountQuery,
  useMarkNotificationReadMutation,
  useMarkAllNotificationsReadMutation,
} = notificationsApi
