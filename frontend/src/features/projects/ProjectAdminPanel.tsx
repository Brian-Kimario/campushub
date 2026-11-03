import { zodResolver } from '@hookform/resolvers/zod'
import { useState } from 'react'
import { useForm } from 'react-hook-form'
import { z } from 'zod'
import { applyFieldIssues, describeError, type ApiFailure } from '../../api/problem'
import type { Project } from '../../api/types'
import { ConfirmDialog, ErrorBanner, ErrorState } from '../../components/ui'
import { useArchiveProjectMutation, useAssignSupervisorMutation, useListFacultyQuery } from './projectsApi'

const supervisorSchema = z.object({
  supervisorId: z.string().min(1, { error: 'Choose a faculty supervisor.' }),
})

type SupervisorValues = z.infer<typeof supervisorSchema>

const FORM_FIELDS = ['supervisorId'] as const

// Admin-only actions (AUTHORIZATION_MATRIX: assign supervisor, archive). The API enforces the role.
export function ProjectAdminPanel({ project, onReload }: { project: Project; onReload: () => void }) {
  const { data: faculty, error: facultyError, isLoading: facultyLoading } = useListFacultyQuery()
  const [assignSupervisor, { isLoading: assigning }] = useAssignSupervisorMutation()
  const [archiveProject, { isLoading: archiving }] = useArchiveProjectMutation()
  const [failure, setFailure] = useState<ApiFailure | null>(null)
  const [confirmOpen, setConfirmOpen] = useState(false)
  const {
    register,
    handleSubmit,
    setError,
    formState: { errors },
  } = useForm<SupervisorValues>({
    resolver: zodResolver(supervisorSchema),
    defaultValues: { supervisorId: project.supervisor?.id ?? '' },
  })

  async function onAssign(values: SupervisorValues) {
    setFailure(null)
    try {
      await assignSupervisor({ id: project.id, supervisorId: values.supervisorId, version: project.version }).unwrap()
    } catch (error) {
      const described = describeError(error)
      const unmapped = applyFieldIssues(setError, described.fieldIssues, FORM_FIELDS)
      setFailure({ ...described, fieldIssues: unmapped })
    }
  }

  async function onArchive() {
    setFailure(null)
    try {
      await archiveProject({ id: project.id, version: project.version }).unwrap()
    } catch (error) {
      setFailure(describeError(error))
    } finally {
      setConfirmOpen(false)
    }
  }

  return (
    <section aria-labelledby="admin-heading" className="admin-panel">
      <h2 id="admin-heading">Administration</h2>

      {failure && (
        <>
          <ErrorBanner message={failure.message} requestId={failure.requestId} />
          {failure.code === 'STALE_VERSION' && (
            <button type="button" onClick={onReload}>
              Reload latest version
            </button>
          )}
        </>
      )}

      <form onSubmit={handleSubmit(onAssign)} noValidate>
        <div className="field">
          <label htmlFor="supervisorId">Supervisor</label>
          {facultyLoading && <p className="muted" role="status">Loading faculty…</p>}
          {facultyError && <ErrorState message={describeError(facultyError).message} />}
          <select
            id="supervisorId"
            aria-invalid={Boolean(errors.supervisorId)}
            aria-describedby={errors.supervisorId ? 'supervisorId-error' : undefined}
            {...register('supervisorId')}
          >
            <option value="">Choose a supervisor</option>
            {faculty?.map((member) => (
              <option key={member.id} value={member.id}>
                {member.displayName}
              </option>
            ))}
          </select>
          {errors.supervisorId && (
            <p id="supervisorId-error" className="field-error">
              {errors.supervisorId.message}
            </p>
          )}
        </div>
        <button type="submit" className="primary" disabled={assigning || facultyLoading}>
          {assigning ? 'Saving…' : 'Assign supervisor'}
        </button>
      </form>

      <div className="archive-zone">
        <button
          type="button"
          className="danger"
          onClick={() => setConfirmOpen(true)}
          disabled={project.status === 'ARCHIVED' || archiving}
        >
          Archive project
        </button>
      </div>

      <ConfirmDialog
        open={confirmOpen}
        title="Archive this project?"
        message="Archived projects are read-only."
        confirmLabel="Archive project"
        danger
        busy={archiving}
        onConfirm={() => void onArchive()}
        onCancel={() => setConfirmOpen(false)}
      />
    </section>
  )
}
