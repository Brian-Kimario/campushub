import assert from 'node:assert/strict'
import { test } from 'node:test'
import { calendarDaysBetween, daysUntil, localDateKey } from '../src/features/dashboard/dueDates.ts'

// Dates are built with local-time constructors so the expectations hold in any machine timezone.
test('localDateKey uses the local calendar date, not the UTC date', () => {
  assert.equal(localDateKey(new Date(2026, 10, 4, 0, 0, 1)), '2026-11-04')
  assert.equal(localDateKey(new Date(2026, 10, 4, 23, 59, 59)), '2026-11-04')
})

test('daysUntil is 0 for a due date on the same calendar day, late or early', () => {
  assert.equal(daysUntil('2026-11-04', new Date(2026, 10, 4, 0, 0, 1)), 0)
  assert.equal(daysUntil('2026-11-04', new Date(2026, 10, 4, 23, 59, 59)), 0)
})

test('daysUntil is 1 for tomorrow at 23:59 and -1 for yesterday at 00:01', () => {
  assert.equal(daysUntil('2026-11-05', new Date(2026, 10, 4, 23, 59)), 1)
  assert.equal(daysUntil('2026-11-03', new Date(2026, 10, 4, 0, 1)), -1)
})

test('daysUntil crosses month and year boundaries', () => {
  assert.equal(daysUntil('2027-01-01', new Date(2026, 11, 31, 23, 30)), 1)
  assert.equal(daysUntil('2026-12-01', new Date(2026, 10, 30, 12)), 1)
})

test('daysUntil counts February 29 in a leap year', () => {
  assert.equal(daysUntil('2028-03-01', new Date(2028, 1, 28, 9)), 2)
  assert.equal(daysUntil('2027-03-01', new Date(2027, 1, 28, 9)), 1)
})

test('daysUntil is unaffected by a daylight-saving transition day', () => {
  // 8 March 2026 is a DST start in many zones; the calendar difference must still be one day.
  assert.equal(daysUntil('2026-03-09', new Date(2026, 2, 8, 12)), 1)
  assert.equal(daysUntil('2026-03-08', new Date(2026, 2, 8, 12)), 0)
})

test('calendarDaysBetween is exact over a long span', () => {
  assert.equal(calendarDaysBetween('2026-01-01', '2027-01-01'), 365)
  assert.equal(calendarDaysBetween('2028-01-01', '2029-01-01'), 366)
})
