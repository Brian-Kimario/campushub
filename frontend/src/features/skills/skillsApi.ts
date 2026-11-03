import { baseApi } from '../../api/baseApi'
import type { OwnSkill, PageResponse, Skill, SkillProficiency } from '../../api/types'

export interface SkillListArgs {
  page: number
  size: number
}

export interface CreateSkillArgs {
  name: string
  category: string
}

export interface OwnSkillEntry {
  skillId: string
  proficiency: SkillProficiency
  evidenceNote: string | null
}

export const skillsApi = baseApi.injectEndpoints({
  endpoints: (builder) => ({
    listSkills: builder.query<PageResponse<Skill>, SkillListArgs>({
      query: ({ page, size }) => ({ url: '/skills', params: { page, size } }),
      providesTags: (result) =>
        result
          ? [...result.content.map((skill) => ({ type: 'Skill' as const, id: skill.id })), { type: 'Skill', id: 'LIST' }]
          : [{ type: 'Skill', id: 'LIST' }],
    }),
    createSkill: builder.mutation<Skill, CreateSkillArgs>({
      query: (body) => ({ url: '/admin/skills', method: 'POST', body }),
      invalidatesTags: [{ type: 'Skill', id: 'LIST' }],
    }),
    listOwnSkills: builder.query<OwnSkill[], void>({
      query: () => '/users/me/skills',
      providesTags: [{ type: 'OwnSkill', id: 'LIST' }],
    }),
    replaceOwnSkills: builder.mutation<OwnSkill[], OwnSkillEntry[]>({
      query: (body) => ({ url: '/users/me/skills', method: 'PUT', body }),
      invalidatesTags: [{ type: 'OwnSkill', id: 'LIST' }],
    }),
  }),
})

export const {
  useListSkillsQuery,
  useCreateSkillMutation,
  useListOwnSkillsQuery,
  useReplaceOwnSkillsMutation,
} = skillsApi
