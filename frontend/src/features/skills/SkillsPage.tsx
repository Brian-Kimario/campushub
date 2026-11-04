import { useState, type FormEvent } from 'react'
import { describeError } from '../../api/problem'
import type { OwnSkill, SkillProficiency } from '../../api/types'
import { useAppSelector } from '../../app/hooks'
import { CharCount, ConfirmDialog, EmptyState, ErrorBanner, ErrorState, PageLoading } from '../../components/ui'
import {
  useCreateSkillMutation,
  useListOwnSkillsQuery,
  useListSkillsQuery,
  useReplaceOwnSkillsMutation,
} from './skillsApi'

const CATALOG_PAGE_SIZE = 20
// The student editor and duplicate check read the first page of the catalog. The server enforces both rules.
const EDITOR_CATALOG_SIZE = 100
const MAX_OWN_SKILLS = 50
const MAX_EVIDENCE_LENGTH = 500

const PROFICIENCY_OPTIONS: { value: SkillProficiency; label: string }[] = [
  { value: 'BEGINNER', label: 'Beginner' },
  { value: 'INTERMEDIATE', label: 'Intermediate' },
  { value: 'ADVANCED', label: 'Advanced' },
  { value: 'EXPERT', label: 'Expert' },
]

interface Row {
  skillId: string
  name: string
  category: string
  proficiency: SkillProficiency
  evidenceNote: string
}

export default function SkillsPage() {
  const user = useAppSelector((state) => state.auth.user)

  return (
    <section>
      <div className="page-header">
        <h1>Skills</h1>
      </div>

      <CatalogSection />

      {user?.role === 'ADMIN' && <AddSkillForm />}
      {user?.role === 'STUDENT' && <OwnSkillsSection />}
    </section>
  )
}

function CatalogSection() {
  const [page, setPage] = useState(0)
  const { data, error, isLoading, isFetching, refetch } = useListSkillsQuery({ page, size: CATALOG_PAGE_SIZE })

  return (
    <section aria-labelledby="catalog-heading">
      <h2 id="catalog-heading">Skill catalog</h2>

      {isLoading && <PageLoading />}
      {error && !data && <ErrorState message={describeError(error).message} onRetry={refetch} />}

      {data && data.content.length === 0 && (
        <EmptyState title="No skills yet">
          <p className="muted">Skills added to the catalog by an administrator will appear here.</p>
        </EmptyState>
      )}

      {data && data.content.length > 0 && (
        <>
          <ul className="skill-list">
            {data.content.map((skill) => (
              <li key={skill.id}>
                {skill.name} <span className="muted">· {skill.category}</span>
              </li>
            ))}
          </ul>
          <nav className="pager" aria-label="Catalog pagination">
            <button type="button" onClick={() => setPage(page - 1)} disabled={page === 0 || isFetching}>
              Previous
            </button>
            <span>
              Page {data.page + 1} of {Math.max(data.totalPages, 1)}
            </span>
            <button
              type="button"
              onClick={() => setPage(page + 1)}
              disabled={page + 1 >= data.totalPages || isFetching}
            >
              Next
            </button>
          </nav>
        </>
      )}
    </section>
  )
}

function AddSkillForm() {
  const [name, setName] = useState('')
  const [category, setCategory] = useState('')
  const [issue, setIssue] = useState<string | null>(null)
  const [saved, setSaved] = useState<string | null>(null)
  const [createSkill, { isLoading }] = useCreateSkillMutation()
  const known = useListSkillsQuery({ page: 0, size: EDITOR_CATALOG_SIZE })

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setSaved(null)
    const cleanName = name.trim().replace(/\s+/g, ' ')
    const cleanCategory = category.trim().replace(/\s+/g, ' ')
    if (cleanName.length < 2 || cleanName.length > 80) {
      setIssue('Name must be 2 to 80 characters.')
      return
    }
    if (cleanCategory.length < 1 || cleanCategory.length > 60) {
      setIssue('Category must be 1 to 60 characters.')
      return
    }
    const key = cleanName.toLowerCase()
    if (known.data?.content.some((skill) => skill.name.toLowerCase() === key)) {
      setIssue('A skill with this name already exists.')
      return
    }
    try {
      const created = await createSkill({ name: cleanName, category: cleanCategory }).unwrap()
      setSaved(`Added ${created.name}.`)
      setName('')
      setCategory('')
      setIssue(null)
    } catch (err) {
      setIssue(describeError(err).message)
    }
  }

  return (
    <section aria-labelledby="add-skill-heading">
      <h2 id="add-skill-heading">Add a skill to the catalog</h2>
      <form onSubmit={handleSubmit} noValidate>
        <div className="field">
          <label htmlFor="skill-name">Name</label>
          <input id="skill-name" value={name} maxLength={80} onChange={(event) => setName(event.target.value)} />
        </div>
        <div className="field">
          <label htmlFor="skill-category">Category</label>
          <input
            id="skill-category"
            value={category}
            maxLength={60}
            onChange={(event) => setCategory(event.target.value)}
          />
        </div>
        {issue && <ErrorBanner message={issue} />}
        {saved && <p role="status">{saved}</p>}
        <button type="submit" className="primary" disabled={isLoading}>
          Add skill
        </button>
      </form>
    </section>
  )
}

function OwnSkillsSection() {
  const { data, error, isLoading, refetch } = useListOwnSkillsQuery()

  if (isLoading) return <PageLoading />
  if (error && !data) return <ErrorState message={describeError(error).message} onRetry={refetch} />
  return <SkillEditor initial={data ?? []} />
}

function SkillEditor({ initial }: { initial: OwnSkill[] }) {
  // The editor starts from the saved set and only sends the full set on save (API_CONTRACT §11).
  const [rows, setRows] = useState<Row[]>(() =>
    initial.map((skill) => ({
      skillId: skill.skillId,
      name: skill.name,
      category: skill.category,
      proficiency: skill.proficiency,
      evidenceNote: skill.evidenceNote ?? '',
    })),
  )
  const [pendingRemove, setPendingRemove] = useState<Row | null>(null)
  const [issues, setIssues] = useState<string[]>([])
  const [status, setStatus] = useState<string | null>(null)
  const [saveSkills, { isLoading: saving }] = useReplaceOwnSkillsMutation()
  const catalog = useListSkillsQuery({ page: 0, size: EDITOR_CATALOG_SIZE })

  const chosen = new Set(rows.map((row) => row.skillId))
  const available = (catalog.data?.content ?? []).filter((skill) => !chosen.has(skill.id))
  const atLimit = rows.length >= MAX_OWN_SKILLS

  function addSkill(skill: { id: string; name: string; category: string }) {
    if (chosen.has(skill.id) || atLimit) return
    setRows([...rows, { skillId: skill.id, name: skill.name, category: skill.category, proficiency: 'BEGINNER', evidenceNote: '' }])
    setStatus(null)
  }

  function updateRow(skillId: string, patch: Partial<Row>) {
    setRows(rows.map((row) => (row.skillId === skillId ? { ...row, ...patch } : row)))
    setStatus(null)
  }

  function removeRow(skillId: string) {
    setRows(rows.filter((row) => row.skillId !== skillId))
    setPendingRemove(null)
    setStatus(null)
  }

  async function handleSave() {
    const tooLong = rows.find((row) => row.evidenceNote.length > MAX_EVIDENCE_LENGTH)
    if (tooLong) {
      setIssues([`${tooLong.name}: evidence must be ${MAX_EVIDENCE_LENGTH} characters or fewer.`])
      return
    }
    setIssues([])
    try {
      await saveSkills(
        rows.map((row) => ({
          skillId: row.skillId,
          proficiency: row.proficiency,
          evidenceNote: row.evidenceNote.trim() === '' ? null : row.evidenceNote.trim(),
        })),
      ).unwrap()
      setStatus('Your skills are saved.')
    } catch (err) {
      const failure = describeError(err)
      setIssues(
        failure.fieldIssues.length > 0
          ? failure.fieldIssues.map((issue) => `${issue.field}: ${issue.reason}`)
          : [failure.message],
      )
    }
  }

  return (
    <section aria-labelledby="own-skills-heading">
      <h2 id="own-skills-heading">My skills</h2>
      <p className="muted">
        {rows.length} of {MAX_OWN_SKILLS} skills selected. Only you can see your skills.
      </p>

      {rows.length === 0 && (
        <EmptyState title="No skills selected">
          <p className="muted">Add skills from the catalog below, then save.</p>
        </EmptyState>
      )}

      {rows.length > 0 && (
        <ul className="own-skill-list">
          {rows.map((row) => (
            <li key={row.skillId} className="own-skill">
              <h3>{row.name}</h3>
              <p className="muted">{row.category}</p>
              <div className="field">
                <label htmlFor={`prof-${row.skillId}`}>Proficiency</label>
                <select
                  id={`prof-${row.skillId}`}
                  value={row.proficiency}
                  onChange={(event) => updateRow(row.skillId, { proficiency: event.target.value as SkillProficiency })}
                >
                  {PROFICIENCY_OPTIONS.map((option) => (
                    <option key={option.value} value={option.value}>
                      {option.label}
                    </option>
                  ))}
                </select>
              </div>
              <div className="field">
                <label htmlFor={`note-${row.skillId}`}>Evidence (optional)</label>
                <textarea
                  id={`note-${row.skillId}`}
                  value={row.evidenceNote}
                  onChange={(event) => updateRow(row.skillId, { evidenceNote: event.target.value })}
                />
                <CharCount value={row.evidenceNote} max={MAX_EVIDENCE_LENGTH} />
              </div>
              <button type="button" className="danger" onClick={() => setPendingRemove(row)}>
                Remove {row.name}
              </button>
            </li>
          ))}
        </ul>
      )}

      <h3>Add from the catalog</h3>
      {catalog.error && !catalog.data && (
        <ErrorState message={describeError(catalog.error).message} onRetry={catalog.refetch} />
      )}
      {catalog.data && available.length === 0 && (
        <p className="muted">Every catalog skill you can see is already selected.</p>
      )}
      {available.length > 0 && (
        <ul className="skill-list">
          {available.map((skill) => (
            <li key={skill.id}>
              <button type="button" onClick={() => addSkill(skill)} disabled={atLimit}>
                Add {skill.name}
              </button>{' '}
              <span className="muted">· {skill.category}</span>
            </li>
          ))}
        </ul>
      )}
      {atLimit && <p className="muted">You have reached the limit of {MAX_OWN_SKILLS} skills.</p>}

      {issues.length > 0 && (
        <ErrorBanner message={issues.join(' ')} />
      )}
      {status && <p role="status">{status}</p>}

      <div className="actions">
        <button type="button" className="primary" onClick={handleSave} disabled={saving}>
          {saving ? 'Saving…' : 'Save skills'}
        </button>
      </div>

      <ConfirmDialog
        open={pendingRemove !== null}
        title="Remove skill?"
        message={`${pendingRemove?.name ?? 'This skill'} will be removed from your skills when you save.`}
        confirmLabel="Remove"
        danger
        onConfirm={() => pendingRemove && removeRow(pendingRemove.skillId)}
        onCancel={() => setPendingRemove(null)}
      />
    </section>
  )
}
