import { useState, type FormEvent } from 'react'
import { describeError } from '../../api/problem'
import { CharCount, ErrorBanner, ErrorState, PageLoading } from '../../components/ui'
import { useGetMyProfileQuery, useListActiveDepartmentsQuery, useUpdateMyProfileMutation, type Profile } from './profileApi'

const MAX_NAME = 100
const MAX_PROGRAM = 120
const MAX_COHORT = 20
const MAX_DESIGNATION = 120
const MAX_BIO = 1000

export default function ProfilePage() {
  const { data: profile, error, isLoading, refetch } = useGetMyProfileQuery()

  return (
    <section>
      <div className="page-header">
        <h1>Profile</h1>
      </div>

      {isLoading && <PageLoading />}
      {error && !profile && <ErrorState message={describeError(error).message} onRetry={refetch} />}
      {profile && <ProfileForm key={profile.id} profile={profile} />}
    </section>
  )
}

function ProfileForm({ profile }: { profile: Profile }) {
  const departments = useListActiveDepartmentsQuery()
  const [updateProfile, { isLoading: saving }] = useUpdateMyProfileMutation()

  const [displayName, setDisplayName] = useState(profile.displayName)
  const [departmentId, setDepartmentId] = useState(profile.departmentId ?? '')
  const [program, setProgram] = useState(profile.program ?? '')
  const [cohort, setCohort] = useState(profile.cohort ?? '')
  const [designation, setDesignation] = useState(profile.designation ?? '')
  const [bio, setBio] = useState(profile.bio ?? '')
  const [issue, setIssue] = useState<string | null>(null)
  const [saved, setSaved] = useState(false)

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setIssue(null)
    setSaved(false)
    const cleanName = displayName.trim()
    if (cleanName.length < 2 || cleanName.length > MAX_NAME) {
      setIssue(`Display name must be 2 to ${MAX_NAME} characters.`)
      return
    }
    try {
      await updateProfile({
        displayName: cleanName,
        departmentId: departmentId || null,
        program: program.trim() || null,
        cohort: cohort.trim() || null,
        designation: designation.trim() || null,
        bio: bio.trim() || null,
      }).unwrap()
      setSaved(true)
    } catch (err) {
      setIssue(describeError(err).message)
    }
  }

  return (
    <>
      <dl className="profile-identity">
        <dt>Email</dt>
        <dd>{profile.email}</dd>
        {profile.institutionalId && (
          <>
            <dt>Institutional ID</dt>
            <dd>{profile.institutionalId}</dd>
          </>
        )}
        <dt>Role</dt>
        <dd>{profile.role}</dd>
      </dl>

      {issue && <ErrorBanner message={issue} />}

      <form onSubmit={handleSubmit} noValidate>
        <div className="field">
          <label htmlFor="profile-name">Display name</label>
          <input
            id="profile-name"
            value={displayName}
            maxLength={MAX_NAME}
            onChange={(event) => setDisplayName(event.target.value)}
          />
        </div>

        <div className="field">
          <label htmlFor="profile-department">Department</label>
          <select id="profile-department" value={departmentId} onChange={(event) => setDepartmentId(event.target.value)}>
            <option value="">No department</option>
            {departments.data?.content.map((department) => (
              <option key={department.id} value={department.id}>
                {department.name}
              </option>
            ))}
          </select>
        </div>

        <div className="field">
          <label htmlFor="profile-program">Program</label>
          <input id="profile-program" value={program} maxLength={MAX_PROGRAM} onChange={(event) => setProgram(event.target.value)} />
        </div>

        <div className="field">
          <label htmlFor="profile-cohort">Cohort</label>
          <input id="profile-cohort" value={cohort} maxLength={MAX_COHORT} onChange={(event) => setCohort(event.target.value)} />
        </div>

        <div className="field">
          <label htmlFor="profile-designation">Designation</label>
          <input
            id="profile-designation"
            value={designation}
            maxLength={MAX_DESIGNATION}
            onChange={(event) => setDesignation(event.target.value)}
          />
        </div>

        <div className="field">
          <label htmlFor="profile-bio">About you</label>
          <textarea id="profile-bio" value={bio} maxLength={MAX_BIO} rows={4} onChange={(event) => setBio(event.target.value)} />
          <CharCount value={bio} max={MAX_BIO} />
        </div>

        <button type="submit" disabled={saving}>
          {saving ? 'Saving…' : 'Save profile'}
        </button>
        {saved && <p role="status">Profile saved.</p>}
      </form>
    </>
  )
}
