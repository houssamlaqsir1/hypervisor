import { useState } from 'react'
import { useAuth } from '../context/AuthContext'
import { useT } from '../lib/useT'
import { httpStatusOf } from '../lib/apiError'
import { IconAlertCircle, IconBrand, IconEye, IconEyeOff } from '../components/icons'

/**
 * Standalone login screen (no sidebar). Shown by the router whenever there
 * is no authenticated user. The demo credentials hint is only there to make
 * the PFE demo self-explanatory.
 */
export function LoginPage() {
  const t = useT()
  const { login } = useAuth()
  const [username, setUsername] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)
  // Reveal is per-attempt and never persisted: a password left visible
  // across sessions on a control-room screen is a worse default than the
  // typo it was meant to catch.
  const [showPassword, setShowPassword] = useState(false)

  async function onSubmit(e: React.FormEvent) {
    e.preventDefault()
    setBusy(true)
    setError(null)
    try {
      await login(username.trim(), password)
    } catch (e) {
      // Only a 401 actually means the credentials were wrong. Everything
      // else — no response at all (the backend still starting, which takes
      // a good half-minute after `docker compose up`), or a server-side
      // failure — was previously reported as a bad password too, sending
      // the operator off to re-check credentials that were never the
      // problem.
      const httpStatus = httpStatusOf(e)
      setError(
        httpStatus === 401
          ? t('login.badCredentials')
          : httpStatus === null
            ? t('login.serverUnreachable')
            : t('login.serverError'),
      )
    } finally {
      setBusy(false)
    }
  }

  return (
    <div className="login-page">
      <form className="login-card" onSubmit={onSubmit}>
        <div className="login-brand">
          <div className="login-brand-mark">
            <IconBrand size={24} />
          </div>
          <div>
            <h1>Hypervisor</h1>
            <p>{t('login.subtitle')}</p>
          </div>
        </div>

        <label className="login-field">
          <span>{t('login.username')}</span>
          <input
            type="text"
            autoComplete="username"
            value={username}
            onChange={(e) => setUsername(e.target.value)}
            autoFocus
            required
          />
        </label>

        <label className="login-field">
          <span>{t('login.password')}</span>
          <div className="password-field">
            <input
              type={showPassword ? 'text' : 'password'}
              autoComplete="current-password"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              required
            />
            <button
              type="button"
              className="password-reveal"
              // Outside the tab order: someone tabbing username → password →
              // Sign in should not land on a decorative control on the way.
              tabIndex={-1}
              onClick={() => setShowPassword((v) => !v)}
              title={showPassword ? t('login.hidePassword') : t('login.showPassword')}
              aria-label={showPassword ? t('login.hidePassword') : t('login.showPassword')}
              aria-pressed={showPassword}
            >
              {/* Slashed eye: currently visible, click to hide. Plain eye:
                  currently hidden, click to reveal. */}
              {showPassword ? <IconEyeOff size={17} /> : <IconEye size={17} />}
            </button>
          </div>
        </label>

        {error && (
          <p className="login-error" role="alert">
            <IconAlertCircle size={15} />
            <span>{error}</span>
          </p>
        )}

        <button type="submit" className="btn" disabled={busy}>
          {busy ? t('login.submitting') : t('login.submit')}
        </button>
      </form>
    </div>
  )
}
