const DAY_MS = 86_400_000

function pad(value: number) {
  return String(value).padStart(2, '0')
}

// The browser's calendar date as YYYY-MM-DD. The server's "today" is not in the dashboard response,
// so the browser's own calendar date is the reference for "Due in N days".
export function localDateKey(now: Date): string {
  return `${now.getFullYear()}-${pad(now.getMonth() + 1)}-${pad(now.getDate())}`
}

// Due dates are calendar dates (YYYY-MM-DD). Comparing UTC midnights of the two dates keeps the
// difference in whole days, so local time of day and DST shifts cannot change the result.
export function calendarDaysBetween(fromDate: string, toDate: string): number {
  const [fromYear, fromMonth, fromDay] = fromDate.split('-').map(Number)
  const [toYear, toMonth, toDay] = toDate.split('-').map(Number)
  const fromUtc = Date.UTC(fromYear, fromMonth - 1, fromDay)
  const toUtc = Date.UTC(toYear, toMonth - 1, toDay)
  return Math.round((toUtc - fromUtc) / DAY_MS)
}

export function daysUntil(dueDate: string, now: Date = new Date()): number {
  return calendarDaysBetween(localDateKey(now), dueDate)
}
