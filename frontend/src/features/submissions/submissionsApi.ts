import { baseApi } from '../../api/baseApi'
import type { ReviewDecision, Submission, SubmissionFile } from '../../api/types'

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

export interface UploadEvidenceArgs {
  submissionId: string
  file: File
}

export interface DownloadEvidenceArgs {
  submissionId: string
  file: SubmissionFile
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
    listEvidence: builder.query<SubmissionFile[], string>({
      query: (submissionId) => `/submissions/${submissionId}/files`,
      providesTags: (_result, _error, submissionId) => [{ type: 'Evidence', id: submissionId }],
    }),
    uploadEvidence: builder.mutation<SubmissionFile, UploadEvidenceArgs>({
      query: ({ submissionId, file }) => {
        // Leave the content type unset so the browser adds the multipart boundary.
        const body = new FormData()
        body.append('file', file)
        return { url: `/submissions/${submissionId}/files`, method: 'POST', body }
      },
      invalidatesTags: (_result, _error, { submissionId }) => [{ type: 'Evidence', id: submissionId }],
    }),
    downloadEvidence: builder.mutation<Blob, DownloadEvidenceArgs>({
      query: ({ submissionId, file }) => ({
        url: `/submissions/${submissionId}/files/${file.id}/content`,
        responseHandler: (response) => response.blob(),
      }),
    }),
  }),
})

export const {
  useListSubmissionsQuery,
  useCreateSubmissionMutation,
  useStartReviewMutation,
  useDecideReviewMutation,
  useListEvidenceQuery,
  useUploadEvidenceMutation,
  useDownloadEvidenceMutation,
} = submissionsApi
