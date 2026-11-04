import type { ReactNode } from 'react'

// Shared dashboard building blocks. Every figure shown here comes from an authenticated dashboard response.

export function MetricTile({ label, value, detail }: { label: string; value: ReactNode; detail?: ReactNode }) {
  return (
    <div className="metric-tile">
      <span className="metric-label">{label}</span>
      <span className="metric-value">{value}</span>
      {detail && <div className="metric-detail">{detail}</div>}
    </div>
  )
}

export function DashboardPanel({
  id,
  title,
  action,
  full = false,
  children,
}: {
  id: string
  title: string
  action?: ReactNode
  full?: boolean
  children: ReactNode
}) {
  return (
    <section className={full ? 'dash-panel dash-panel-full' : 'dash-panel'} aria-labelledby={id}>
      <div className="dash-panel-head">
        <h2 id={id}>{title}</h2>
        {action}
      </div>
      {children}
    </section>
  )
}

// Server-calculated percent is null when there are no tasks, so an empty project never reads as complete.
export function ProgressIndicator({
  percent,
  done,
  total,
}: {
  percent: number | null
  done: number
  total: number
}) {
  if (percent === null || total === 0) {
    return <span className="muted">No tasks yet</span>
  }
  return (
    <div className="progress-indicator">
      <div
        className="progress-track"
        role="progressbar"
        aria-label="Task progress"
        aria-valuemin={0}
        aria-valuemax={100}
        aria-valuenow={percent}
      >
        <span style={{ width: `${percent}%` }} />
      </div>
      <span>
        {done} of {total} tasks done
      </span>
    </div>
  )
}

export interface CountRow {
  label: string
  value: number
}

// Counts as a list. Each track is scaled to the sum of the rows, so it shows share, not a target.
export function CountBreakdown({ rows }: { rows: CountRow[] }) {
  const total = rows.reduce((sum, row) => sum + row.value, 0)

  return (
    <ul className="count-breakdown">
      {rows.map((row) => (
        <li key={row.label} className="count-row">
          <span className="count-label">{row.label}</span>
          <span className="count-track" aria-hidden="true">
            <span style={{ width: total === 0 ? '0%' : `${(row.value / total) * 100}%` }} />
          </span>
          <span className="count-value">{row.value}</span>
        </li>
      ))}
    </ul>
  )
}

export type DistributionTone ='done' | 'progress' | 'review' | 'todo' | 'blocked'

export interface DistributionSegment {
  label: string
  value: number
  tone: DistributionTone
}

// A single proportional bar with a legend. The legend carries the counts, so colour is never the only cue.
export function StatusDistribution({ label, segments }: { label: string; segments: DistributionSegment[] }) {
  const total = segments.reduce((sum, segment) => sum + segment.value, 0)
  if (total === 0) {
    return <p className="muted">No tasks yet.</p>
  }
  const summary = segments.map((segment) => `${segment.label} ${segment.value}`).join(', ')

  return (
    <div className="status-distribution">
      <div className="status-bar" role="img" aria-label={`${label}: ${summary}`}>
        {segments
          .filter((segment) => segment.value > 0)
          .map((segment) => (
            <span
              key={segment.label}
              className={`status-bar-${segment.tone}`}
              style={{ width: `${(segment.value / total) * 100}%` }}
            />
          ))}
      </div>
      <ul className="status-legend">
        {segments.map((segment) => (
          <li key={segment.label}>
            <span className={`status-swatch status-bar-${segment.tone}`} aria-hidden="true" />
            <span>{segment.label}</span>
            <span className="status-count">{segment.value}</span>
          </li>
        ))}
      </ul>
    </div>
  )
}
