import { useState } from 'react'
import { useAuth } from '../context/AuthContext'
import { useT } from '../lib/useT'
import { httpStatusOf } from '../lib/apiError'

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
      {/* decorative animated bubbles (same as the sidebar) */}
      <div className="login-bubble login-bubble--1" />
      <div className="login-bubble login-bubble--2" />
      <div className="login-bubble login-bubble--3" />
      <div className="login-bubble login-bubble--4" />
      <div className="login-bubble login-bubble--5" />

      <form className="login-card" onSubmit={onSubmit}>
        <div className="login-brand">
          <div className="login-brand-icon">📡</div>
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
              {showPassword ? (
                /* Eye with a slash — currently visible, click to hide. */
                <svg viewBox="0 0 20 20" width="18" height="18" fill="none" stroke="currentColor" strokeWidth="1.6" strokeLinecap="round" strokeLinejoin="round">
                  <path d="M8.5 4.2a7.6 7.6 0 0 1 1.5-.15c4 0 7 3.2 8 5.95a12 12 0 0 1-2.2 3.2M5.1 5.6A11.6 11.6 0 0 0 2 10c1 2.75 4 5.95 8 5.95a8.6 8.6 0 0 0 3.5-.74" />
                  <path d="M8.2 8.3a2.5 2.5 0 0 0 3.5 3.5" />
                  <path d="M3 3l14 14" />
                </svg>
              ) : (
                /* Plain eye — currently hidden, click to reveal. */
                <svg viewBox="0 0 20 20" width="18" height="18" fill="none" stroke="currentColor" strokeWidth="1.6" strokeLinecap="round" strokeLinejoin="round">
                  <path d="M2 10c1-2.75 4-5.95 8-5.95S17 7.25 18 10c-1 2.75-4 5.95-8 5.95S3 12.75 2 10z" />
                  <circle cx="10" cy="10" r="2.5" />
                </svg>
              )}
            </button>
          </div>
        </label>

        {error && <p className="login-error">{error}</p>}

        <button type="submit" className="btn" disabled={busy}>
          {busy ? t('login.submitting') : t('login.submit')}
        </button>
      </form>
    </div>
  )
}
