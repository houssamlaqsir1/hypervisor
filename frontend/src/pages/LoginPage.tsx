import { useState } from 'react'
import { useAuth } from '../context/AuthContext'
import { useT } from '../lib/useT'

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

  async function onSubmit(e: React.FormEvent) {
    e.preventDefault()
    setBusy(true)
    setError(null)
    try {
      await login(username.trim(), password)
    } catch {
      setError('Invalid username or password.')
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
          <input
            type="password"
            autoComplete="current-password"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            required
          />
        </label>

        {error && <p className="login-error">{error}</p>}

        <button type="submit" className="btn" disabled={busy}>
          {busy ? t('login.submitting') : t('login.submit')}
        </button>
      </form>
    </div>
  )
}
