import { baseApi } from '../../api/baseApi'
import type { Certification, PageResponse } from '../../api/types'

export interface CertificationListArgs {
  page: number
  size: number
}

export interface CreateCertificationArgs {
  title: string
  issuer: string
  issuedOn: string
  credentialUrl: string | null
}

export const certificationsApi = baseApi.injectEndpoints({
  endpoints: (builder) => ({
    listCertifications: builder.query<PageResponse<Certification>, CertificationListArgs>({
      query: ({ page, size }) => ({ url: '/users/me/certifications', params: { page, size } }),
      providesTags: [{ type: 'Certification', id: 'LIST' }],
    }),
    createCertification: builder.mutation<Certification, CreateCertificationArgs>({
      query: (body) => ({ url: '/users/me/certifications', method: 'POST', body }),
      invalidatesTags: [{ type: 'Certification', id: 'LIST' }],
    }),
    deleteCertification: builder.mutation<void, string>({
      query: (id) => ({ url: `/users/me/certifications/${id}`, method: 'DELETE' }),
      invalidatesTags: [{ type: 'Certification', id: 'LIST' }],
    }),
  }),
})

export const {
  useListCertificationsQuery,
  useCreateCertificationMutation,
  useDeleteCertificationMutation,
} = certificationsApi
