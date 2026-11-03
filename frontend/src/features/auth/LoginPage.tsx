import { zodResolver } from '@hookform/resolvers/zod'
import { useState } from 'react'
import { useForm } from 'react-hook-form'
import { Navigate, useLocation } from 'react-router'
import { z } from 'zod'
import { describeError, applyFieldIssues, type ApiFailure } from '../../api/problem'
import { useAppDispatch, useAppSelector } from '../../app/hooks'
import { ErrorBanner } from '../../components/ui'
import { sessionStarted } from './authSlice'
import { useLoginMutation } from './authApi'

const loginSchema = z.object({
  email: z.email({ error: 'Enter a valid email address.' }).max(254, { error: 'Email must be 254 characters or fewer.' }),
  password: z
    .string()
    .min(1, { error: 'Enter your password.' })
    .max(128, { error: 'Password must be 128 characters or fewer.' }),
})

type LoginValues = z.infer<typeof loginSchema>

const FORM_FIELDS = ['email', 'password'] as const

export default function LoginPage() {
  const dispatch = useAppDispatch()
  const user = useAppSelector((state) => state.auth.user)
  const location = useLocation()
  const [login, { isLoading }] = useLoginMutation()
  const [failure, setFailure] = useState<ApiFailure | null>(null)
  const {
    register,
    handleSubmit,
    setError,
    formState: { errors },
  } = useForm<LoginValues>({
    resolver: zodResolver(loginSchema),
    defaultValues: { email: '', password: '' },
  })

  const state = location.state as { from?: string; notice?: string } | null
  const target = user?.mustChangePassword ? '/change-password' : (state?.from ?? '/projects')

  if (user) {
    return <Navigate to={target} replace />
  }

  async function onSubmit(values: LoginValues) {
    setFailure(null)
    try {
      const result = await login(values).unwrap()
      dispatch(sessionStarted({ accessToken: result.accessToken, user: result.user }))
    } catch (error) {
      const described = describeError(error)
      const unmapped = applyFieldIssues(setError, described.fieldIssues, FORM_FIELDS)
      setFailure({ ...described, fieldIssues: unmapped })
    }
  }

  return (
    <main className="auth-page">
      <h1>Sign in to CampusHub</h1>
      {state?.notice && <p className="banner banner-info" role="status">{state.notice}</p>}
      {failure && <ErrorBanner message={failure.message} requestId={failure.requestId} />}
      <form onSubmit={handleSubmit(onSubmit)} noValidate>
        <div className="field">
          <label htmlFor="email">Email</label>
          <input
            id="email"
            type="email"
            autoComplete="username"
            aria-invalid={Boolean(errors.email)}
            aria-describedby={errors.email ? 'email-error' : undefined}
            {...register('email')}
          />
          {errors.email && <p id="email-error" className="field-error">{errors.email.message}</p>}
        </div>
        <div className="field">
          <label htmlFor="password">Password</label>
          <input
            id="password"
            type="password"
            autoComplete="current-password"
            aria-invalid={Boolean(errors.password)}
            aria-describedby={errors.password ? 'password-error' : undefined}
            {...register('password')}
          />
          {errors.password && <p id="password-error" className="field-error">{errors.password.message}</p>}
        </div>
        <button type="submit" className="primary" disabled={isLoading}>
          {isLoading ? 'Signing in…' : 'Sign in'}
        </button>
      </form>
    </main>
  )
}
