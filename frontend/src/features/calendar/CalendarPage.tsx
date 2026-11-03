import { useMemo, useState } from 'react'
import { Link } from 'react-router'
import { describeError } from '../../api/problem'
import type { CalendarEntry } from '../../api/types'
import { EmptyState, ErrorState, PageLoading } from '../../components/ui'
import { useListCalendarEntriesQuery } from './calendarApi'

const WEEKDAYS = ['Sun', 'Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat']
const GRID_DAYS = 42

// Dates are built from local parts so a day never shifts when formatted or compared (LocalDate, no zone).
function addDays(date: Date, days: number): Date {
  return new Date(date.getFullYear(), date.getMonth(), date.getDate() + days)
}

function startOfMonth(date: Date): Date {
  return new Date(date.getFullYear(), date.getMonth(), 1)
}

function toIsoDate(date: Date): string {
  const month = String(date.getMonth() + 1).padStart(2, '0')
  const day = String(date.getDate()).padStart(2, '0')
  return `${date.getFullYear()}-${month}-${day}`
}

function parseIsoDate(value: string): Date {
  const [year, month, day] = value.split('-').map(Number)
  return new Date(year, month - 1, day)
}

const TYPE_LABELS: Record<CalendarEntry['type'], string> = {
  TASK_DUE: 'Task due',
  PROJECT_DUE: 'Project due',
  MILESTONE_DUE: 'Milestone due',
}

export default function CalendarPage() {
  // Captured once on mount so the page keeps a stable "today" while it is open.
  const [todayIso] = useState(() => toIsoDate(new Date()))
  const [visibleMonth, setVisibleMonth] = useState(() => startOfMonth(parseIsoDate(todayIso)))
  const [selected, setSelected] = useState(todayIso)

  // The grid covers six full weeks, so days from neighbouring months also show their deadlines.
  const gridStart = addDays(visibleMonth, -visibleMonth.getDay())
  const days = Array.from({ length: GRID_DAYS }, (_, index) => addDays(gridStart, index))
  const { data, error, isLoading, refetch } = useListCalendarEntriesQuery({
    from: toIsoDate(gridStart),
    to: toIsoDate(addDays(gridStart, GRID_DAYS - 1)),
  })

  const entriesByDate = useMemo(() => {
    const map = new Map<string, CalendarEntry[]>()
    for (const entry of data ?? []) {
      map.set(entry.date, [...(map.get(entry.date) ?? []), entry])
    }
    return map
  }, [data])

  function showMonth(offset: number) {
    setVisibleMonth((current) => new Date(current.getFullYear(), current.getMonth() + offset, 1))
  }

  function showToday() {
    setVisibleMonth(startOfMonth(parseIsoDate(todayIso)))
    setSelected(todayIso)
  }

  const monthLabel = visibleMonth.toLocaleDateString(undefined, { month: 'long', year: 'numeric' })
  const selectedEntries = entriesByDate.get(selected) ?? []

  return (
    <section>
      <div className="page-header">
        <h1>Calendar</h1>
      </div>

      <div className="calendar-toolbar">
        <button type="button" onClick={() => showMonth(-1)} aria-label="Previous month">
          Previous
        </button>
        <h2 aria-live="polite">{monthLabel}</h2>
        <button type="button" onClick={() => showMonth(1)} aria-label="Next month">
          Next
        </button>
        <button type="button" onClick={showToday}>
          Today
        </button>
      </div>

      {isLoading && <PageLoading />}
      {error && <ErrorState message={describeError(error).message} onRetry={refetch} />}

      {!isLoading && !error && (
        <>
          <div className="calendar-grid" role="grid" aria-label={`Deadlines for ${monthLabel}`}>
            {WEEKDAYS.map((weekday) => (
              <div key={weekday} className="calendar-weekday" role="columnheader">
                {weekday}
              </div>
            ))}
            {days.map((day) => {
              const iso = toIsoDate(day)
              const count = entriesByDate.get(iso)?.length ?? 0
              const inMonth = day.getMonth() === visibleMonth.getMonth()
              const classes = [
                'calendar-day',
                inMonth ? '' : 'calendar-day-outside',
                iso === todayIso ? 'calendar-day-today' : '',
                iso === selected ? 'calendar-day-selected' : '',
              ]
                .filter(Boolean)
                .join(' ')
              return (
                <button
                  key={iso}
                  type="button"
                  className={classes}
                  aria-pressed={iso === selected}
                  aria-label={`${parseIsoDate(iso).toLocaleDateString(undefined, { dateStyle: 'full' })}${
                    count > 0 ? `, ${count} deadline${count === 1 ? '' : 's'}` : ''
                  }`}
                  onClick={() => setSelected(iso)}
                >
                  <span className="calendar-day-number">{day.getDate()}</span>
                  {count > 0 && <span className="calendar-day-count">{count}</span>}
                </button>
              )
            })}
          </div>

          <section className="calendar-details" aria-labelledby="calendar-details-heading">
            <h2 id="calendar-details-heading">
              Deadlines on {parseIsoDate(selected).toLocaleDateString(undefined, { dateStyle: 'full' })}
            </h2>
            {selectedEntries.length === 0 ? (
              <EmptyState title="No deadlines">
                <p className="muted">Nothing is due on this day in the projects you can see.</p>
              </EmptyState>
            ) : (
              <ul className="dashboard-list">
                {selectedEntries.map((entry) => (
                  <li key={`${entry.type}-${entry.id}`}>
                    <strong>{entry.title}</strong> · {TYPE_LABELS[entry.type]} · {entry.status.toLowerCase().replace('_', ' ')}
                    <br />
                    <Link to={`/projects/${entry.projectId}`}>
                      {entry.type === 'TASK_DUE' ? `Task in ${entry.projectTitle}` : entry.projectTitle}
                    </Link>
                  </li>
                ))}
              </ul>
            )}
          </section>
        </>
      )}
    </section>
  )
}
