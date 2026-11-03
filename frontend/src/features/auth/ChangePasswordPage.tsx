import { zodResolver } from '@hookform/resolvers/zod'
import { useState } from 'react'
import { useForm, useWatch } from 'react-hook-form'
import { useNavigate } from 'react-router'
import { z } from 'zod'
import { applyFieldIssues, describeError, type ApiFailure } from '../../api/problem'
import { useAppDispatch } from '../../app/hooks'
import { baseApi } from '../../api/baseApi'
import { CharCount, ErrorBanner } from '../../components/ui'
import { sessionCleared } from './authSlice'
import { useChangePasswordMutation } from './authApi'

// Policy: 12 to 72 UTF-8 bytes (SECURITY.md; BCrypt input limit). The server re-checks every rule.
const PASSWORD_MIN_CHARS = 12
const PASSWORD_MAX_BYTES = 72
const encoder = new TextEncoder()

const changePasswordSchema = z
  .object({
    currentPassword: z
      .string()
      .min(1, { error: 'Enter your current password.' })
      .max(128, { error: 'Password must be 128 characters or fewer.' }),
    newPassword: z
      .string()
      .min(PASSWORD_MIN_CHARS, { error: `Use at least ${PASSWORD_MIN_CHARS} characters.` })
      .refine((value) => encoder.encode(value).length <= PASSWORD_MAX_BYTES, {
        error: `Use at most ${PASSWORD_MAX_BYTES} bytes. Some characters take more than one byte.`,
      }),
    confirmPassword: z.string().min(1, { error: 'Confirm your new password.' }),
  })
  .refine((values) => values.newPassword === values.confirmPassword, {
    error: 'The passwords do not match.',
    path: ['confirmPassword'],
  })

type ChangePasswordValues = z.infer<typeof changePasswordSchema>

const FORM_FIELDS = ['currentPassword', 'newPassword'] as const

export default function ChangePasswordPage() {
  const dispatch = useAppDispatch()
  const navigate = useNavigate()
  const [changePassword, { isLoading }] = useChangePasswordMutation()
  const [failure, setFailure] = useState<ApiFailure | null>(null)
  const {
    register,
    handleSubmit,
    setError,
    control,
    formState: { errors },
  } = useForm<ChangePasswordValues>({
    resolver: zodResolver(changePasswordSchema),
    defaultValues: { currentPassword: '', newPassword: '', confirmPassword: '' },
  })
  const newPassword = useWatch({ control, name: 'newPassword' })

  async function onSubmit(values: ChangePasswordValues) {
    setFailure(null)
    try {
      await changePassword({ currentPassword: values.currentPassword, newPassword: values.newPassword }).unwrap()
      // Sign out after a change so the user confirms the new password. This also clears the cached data.
      dispatch(baseApi.util.resetApiState())
      dispatch(sessionCleared())
      navigate('/login', { replace: true, state: { notice: 'Password changed. Sign in with your new password.' } })
    } catch (error) {
      const described = describeError(error)
      const unmapped = applyFieldIssues(setError, described.fieldIssues, FORM_FIELDS)
      setFailure({ ...described, fieldIssues: unmapped })
    }
  }

  return (
    <section>
      <h1>Change password</h1>
      {failure && <ErrorBanner message={failure.message} requestId={failure.requestId} />}
      <form onSubmit={handleSubmit(onSubmit)} noValidate>
        <div className="field">
          <label htmlFor="currentPassword">Current password</label>
          <input
            id="currentPassword"
            type="password"
            autoComplete="current-password"
            aria-invalid={Boolean(errors.currentPassword)}
            aria-describedby={errors.currentPassword ? 'currentPassword-error' : undefined}
            {...register('currentPassword')}
          />
          {errors.currentPassword && (
            <p id="currentPassword-error" className="field-error">{errors.currentPassword.message}</p>
          )}
        </div>
        <div className="field">
          <label htmlFor="newPassword">New password</label>
          <input
            id="newPassword"
            type="password"
            autoComplete="new-password"
            aria-invalid={Boolean(errors.newPassword)}
            aria-describedby={errors.newPassword ? 'newPassword-error' : 'newPassword-hint'}
            {...register('newPassword')}
          />
          <p id="newPassword-hint" className="muted">
            Use {PASSWORD_MIN_CHARS} to {PASSWORD_MAX_BYTES} bytes.
          </p>
          {errors.newPassword && (
            <p id="newPassword-error" className="field-error">{errors.newPassword.message}</p>
          )}
          <CharCount value={newPassword} max={PASSWORD_MAX_BYTES} />
        </div>
        <div className="field">
          <label htmlFor="confirmPassword">Confirm new password</label>
          <input
            id="confirmPassword"
            type="password"
            autoComplete="new-password"
            aria-invalid={Boolean(errors.confirmPassword)}
            aria-describedby={errors.confirmPassword ? 'confirmPassword-error' : undefined}
            {...register('confirmPassword')}
          />
          {errors.confirmPassword && (
            <p id="confirmPassword-error" className="field-error">{errors.confirmPassword.message}</p>
          )}
        </div>
        <button type="submit" className="primary" disabled={isLoading}>
          {isLoading ? 'Saving…' : 'Change password'}
        </button>
      </form>
    </section>
  )
}
