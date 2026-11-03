// Response shapes from the API (API_CONTRACT). The API omits null fields, so nullable values are optional.

export type Role = 'STUDENT' | 'FACULTY' | 'ADMIN'

export interface UserSummary {
  id: string
  email: string
  institutionalId?: string
  displayName: string
  role: Role
  status: 'ACTIVE' | 'DEACTIVATED'
  mustChangePassword: boolean
  departmentId?: string
}

export interface LoginResponse {
  accessToken: string
  tokenType: string
  expiresIn: number
  user: UserSummary
}

export interface PersonSummary {
  id: string
  displayName: string
}

export interface PageResponse<T> {
  content: T[]
  page: number
  size: number
  totalElements: number
  totalPages: number
  sort: string[]
}

export type ProjectStatus = 'ACTIVE' | 'ARCHIVED'

export interface Project {
  id: string
  title: string
  description?: string
  subject?: string
  status: ProjectStatus
  startDate?: string
  dueDate?: string
  owner?: PersonSummary
  supervisor?: PersonSummary
  version: number
  createdAt: string
  updatedAt: string
}

export type TaskStatus = 'TODO' | 'IN_PROGRESS' | 'IN_REVIEW' | 'BLOCKED' | 'DONE'
export type TaskPriority = 'LOW' | 'MEDIUM' | 'HIGH'

export interface Task {
  id: string
  projectId: string
  title: string
  description?: string
  status: TaskStatus
  priority: TaskPriority
  blockedReason?: string
  dueDate?: string
  completedAt?: string
  createdBy: string
  assigneeId?: string | null
  version: number
  createdAt: string
  updatedAt: string
}

export type MilestoneStatus = 'OPEN' | 'CLOSED'

export interface Milestone {
  id: string
  projectId: string
  sequence: number
  title: string
  dueAt: string
  allowLate: boolean
  status: MilestoneStatus
  version: number
  createdAt: string
  updatedAt: string
}

export type SubmissionStatus = 'SUBMITTED' | 'UNDER_REVIEW' | 'CHANGES_REQUESTED' | 'ACCEPTED'
export type ReviewDecision = 'ACCEPTED' | 'CHANGES_REQUESTED'

export interface Submission {
  id: string
  projectId: string
  revisionNo: number
  title: string
  description?: string
  link: string
  status: SubmissionStatus
  submittedBy: string
  submittedAt: string
  reviewedBy?: string
  reviewedAt?: string
  feedback?: string
  version: number
}

export type SkillProficiency = 'BEGINNER' | 'INTERMEDIATE' | 'ADVANCED' | 'EXPERT'

export interface Skill {
  id: string
  name: string
  category: string
}

export interface OwnSkill {
  skillId: string
  name: string
  category: string
  proficiency: SkillProficiency
  evidenceNote: string | null
  updatedAt: string
}

export interface Certification {
  id: string
  title: string
  issuer: string
  issuedOn: string
  expiresOn: string | null
  credentialId: string | null
  credentialUrl: string | null
  createdAt: string
  version: number
}

export interface Member {
  userId: string
  displayName?: string
  role: 'OWNER' | 'MEMBER'
  joinedAt?: string
}

export type InvitationStatus = 'PENDING' | 'ACCEPTED' | 'DECLINED' | 'EXPIRED'

export interface Invitation {
  id: string
  projectId: string
  projectTitle?: string
  invitee?: PersonSummary
  inviter?: PersonSummary
  status: InvitationStatus
  expiresAt: string
  createdAt: string
  respondedAt?: string
  version: number
}

export type CalendarEntryType = 'TASK_DUE' | 'PROJECT_DUE' | 'MILESTONE_DUE'

export interface CalendarEntry {
  type: CalendarEntryType
  date: string
  id: string
  title: string
  status: string
  projectId: string
  projectTitle: string
}
