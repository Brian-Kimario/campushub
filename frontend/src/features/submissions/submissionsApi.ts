import { baseApi } from '../../api/baseApi'
import type { ReviewDecision, Submission } from '../../api/types'

export interface CreateSubmissionArgs {
  projectId: string
  title: string
  description: string | null
  link: string
}

export interface DecideReviewArgs {
  id: string
  decision: ReviewDecision
  feedback: string | null
}

// The list is not paged. It is ordered by revision ascending (API_CONTRACT §10).
export const submissionsApi = baseApi.injectEndpoints({
  endpoints: (builder) => ({
    listSubmissions: builder.query<Submission[], string>({
      query: (projectId) => `/projects/${projectId}/submissions`,
      providesTags: [{ type: 'Submission', id: 'LIST' }],
    }),
    createSubmission: builder.mutation<Submission, CreateSubmissionArgs>({
      query: ({ projectId, ...body }) => ({ url: `/projects/${projectId}/submissions`, method: 'POST', body }),
      invalidatesTags: [{ type: 'Submission', id: 'LIST' }],
    }),
    startReview: builder.mutation<Submission, string>({
      query: (id) => ({ url: `/submissions/${id}/review/start`, method: 'POST' }),
      invalidatesTags: [{ type: 'Submission', id: 'LIST' }],
    }),
    decideReview: builder.mutation<Submission, DecideReviewArgs>({
      query: ({ id, ...body }) => ({ url: `/submissions/${id}/review`, method: 'POST', body }),
      invalidatesTags: [{ type: 'Submission', id: 'LIST' }],
    }),
  }),
})

export const {
  useListSubmissionsQuery,
  useCreateSubmissionMutation,
  useStartReviewMutation,
  useDecideReviewMutation,
} = submissionsApi
