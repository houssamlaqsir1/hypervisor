import { useEffect, useState } from 'react'
import {
  createUser,
  deleteUser,
  listUsers,
  setUserEnabled,
  updateUser,
} from '../api/admin'
import { useAuth } from '../context/AuthContext'
import type { AdminUser, Role } from '../types/api'
import { useT } from '../lib/useT'

const ROLE_LABEL: Record<Role, string> = {
  VIEWER: 'Viewer',
  OPERATOR: 'Operator',
  ADMIN: 'Admin',
}

const ROLES: Role[] = ['VIEWER', 'OPERATOR', 'ADMIN']

/** Strip the "NNN Status Text: {json}" prefix the API client puts on error messages. */
function extractMessage(raw: string): string {
  const match = raw.match(/^\d+\s+\S+:\s*(.*)$/s)
  if (!match) return raw
  try {
    const parsed = JSON.parse(match[1]) as { message?: string }
    return parsed.message ?? match[1]
  } catch {
    return match[1]
  }
}

interface RowProps {
  user: AdminUser
  isSelf: boolean
  onChanged: (updated: AdminUser) => void
  onDeleted: (id: number) => void
}

function UserRow({ user, isSelf, onChanged, onDeleted }: RowProps) {
  const [editing, setEditing] = useState(false)
  const [username, setUsername] = useState(user.username)
  const [fullName, setFullName] = useState(user.fullName ?? '')
  const [role, setRole] = useState<Role>(user.role)
  const [newPassword, setNewPassword] = useState('')
  const [busy, setBusy] = useState(false)
  const [rowError, setRowError] = useState<string | null>(null)

  function startEdit() {
    setUsername(user.username)
    setFullName(user.fullName ?? '')
    setRole(user.role)
    setNewPassword('')
    setRowError(null)
    setEditing(true)
  }

  async function onToggleEnabled() {
    setRowError(null)
    setBusy(true)
    try {
      onChanged(await setUserEnabled(user.id, !user.enabled))
    } catch (err) {
      setRowError(err instanceof Error ? extractMessage(err.message) : 'Action failed')
    } finally {
      setBusy(false)
    }
  }

  async function onSave() {
    setRowError(null)
    setBusy(true)
    try {
      const updated = await updateUser(user.id, {
        username: username.trim(),
        fullName: fullName.trim() || undefined,
        role,
        newPassword: newPassword.trim() || undefined,
      })
      onChanged(updated)
      setEditing(false)
    } catch (err) {
      setRowError(err instanceof Error ? extractMessage(err.message) : 'Update failed')
    } finally {
      setBusy(false)
    }
  }

  async function onDelete() {
    if (!window.confirm(`Delete user "${user.username}"? This can't be undone.`)) return
    setRowError(null)
    setBusy(true)
    try {
      await deleteUser(user.id)
      onDeleted(user.id)
    } catch (err) {
      setRowError(err instanceof Error ? extractMessage(err.message) : 'Delete failed')
      setBusy(false)
    }
  }

  if (editing) {
    return (
      <tr>
        <td>
          <input
            type="text"
            value={username}
            onChange={(e) => setUsername(e.target.value)}
            disabled={busy}
          />
        </td>
        <td>
          <input
            type="text"
            value={fullName}
            onChange={(e) => setFullName(e.target.value)}
            disabled={busy}
          />
        </td>
        <td>
          <select
            value={role}
            onChange={(e) => setRole(e.target.value as Role)}
            disabled={busy || (isSelf && user.role === 'ADMIN')}
            title={isSelf && user.role === 'ADMIN' ? "You can't change your own role away from Admin" : undefined}
          >
            {ROLES.map((r) => (
              <option key={r} value={r}>
                {ROLE_LABEL[r]}
              </option>
            ))}
          </select>
        </td>
        <td colSpan={2}>
          <input
            type="password"
            placeholder="New password (optional)"
            value={newPassword}
            onChange={(e) => setNewPassword(e.target.value)}
            disabled={busy}
          />
          {rowError && <div className="login-error" style={{ marginTop: 6 }}>{rowError}</div>}
        </td>
        <td style={{ whiteSpace: 'nowrap' }}>
          <button type="button" className="btn btn-sm" onClick={onSave} disabled={busy}>
            Save
          </button>{' '}
          <button
            type="button"
            className="btn secondary btn-sm"
            onClick={() => setEditing(false)}
            disabled={busy}
          >
            Cancel
          </button>
        </td>
      </tr>
    )
  }

  return (
    <tr>
      <td>{user.username}</td>
      <td>{user.fullName ?? '—'}</td>
      <td>
        <span className={`sidebar-user-role role-${user.role}`} style={{ color: 'var(--text)' }}>
          {ROLE_LABEL[user.role]}
        </span>
      </td>
      <td>
        <span className={`alert-status-badge status-${user.enabled ? 'RESOLVED' : 'NEW'}`}>
          {user.enabled ? 'Enabled' : 'Disabled'}
        </span>
      </td>
      <td>{new Date(user.createdAt).toLocaleDateString()}</td>
      <td style={{ whiteSpace: 'nowrap' }}>
        <button type="button" className="btn secondary btn-sm" onClick={startEdit} disabled={busy}>
          Edit
        </button>{' '}
        <button
          type="button"
          className="btn secondary btn-sm"
          onClick={onToggleEnabled}
          disabled={busy || isSelf}
          title={isSelf ? "You can't disable your own account" : undefined}
        >
          {user.enabled ? 'Disable' : 'Enable'}
        </button>{' '}
        <button
          type="button"
          className="btn danger btn-sm"
          onClick={onDelete}
          disabled={busy || isSelf}
          title={isSelf ? "You can't delete your own account" : undefined}
        >
          Delete
        </button>
        {rowError && <div className="login-error" style={{ marginTop: 6 }}>{rowError}</div>}
      </td>
    </tr>
  )
}

export function AdminUsersPage() {
  const t = useT()
  const { user: currentUser } = useAuth()
  const [users, setUsers] = useState<AdminUser[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  const [username, setUsername] = useState('')
  const [fullName, setFullName] = useState('')
  const [password, setPassword] = useState('')
  const [role, setRole] = useState<Role>('VIEWER')
  const [creating, setCreating] = useState(false)
  const [formError, setFormError] = useState<string | null>(null)

  function refresh() {
    setLoading(true)
    listUsers()
      .then(setUsers)
      .catch((e: unknown) => setError(e instanceof Error ? e.message : 'Failed to load users'))
      .finally(() => setLoading(false))
  }

  useEffect(refresh, [])

  async function onCreate(e: React.FormEvent) {
    e.preventDefault()
    setFormError(null)
    setCreating(true)
    try {
      await createUser({
        username: username.trim(),
        fullName: fullName.trim() || undefined,
        password,
        role,
      })
      setUsername('')
      setFullName('')
      setPassword('')
      setRole('VIEWER')
      refresh()
    } catch (err) {
      setFormError(
        err instanceof Error ? extractMessage(err.message) : 'Failed to create user',
      )
    } finally {
      setCreating(false)
    }
  }

  return (
    <>
      <div className="page-header">
        <div>
          <h2>{t('admin.users.title')}</h2>
          <p>{t('admin.users.subtitle')}</p>
        </div>
      </div>

      <div className="card" style={{ marginBottom: 24 }}>
        <h3 style={{ marginBottom: 16 }}>New user</h3>
        <form onSubmit={onCreate} className="admin-user-form">
          <label className="login-field">
            <span>Username</span>
            <input
              type="text"
              value={username}
              onChange={(e) => setUsername(e.target.value)}
              required
            />
          </label>
          <label className="login-field">
            <span>Full name (optional)</span>
            <input
              type="text"
              value={fullName}
              onChange={(e) => setFullName(e.target.value)}
            />
          </label>
          <label className="login-field">
            <span>Password</span>
            <input
              type="password"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              minLength={6}
              required
            />
          </label>
          <label className="login-field">
            <span>Role</span>
            <select value={role} onChange={(e) => setRole(e.target.value as Role)}>
              {ROLES.map((r) => (
                <option key={r} value={r}>
                  {ROLE_LABEL[r]}
                </option>
              ))}
            </select>
          </label>
          {formError && <p className="login-error">{formError}</p>}
          <button type="submit" className="btn" disabled={creating}>
            {creating ? 'Creating…' : 'Create user'}
          </button>
        </form>
      </div>

      {loading && <p className="muted">Loading…</p>}
      {error && <p className="login-error">{error}</p>}

      {!loading && (
        <div className="card">
          <table className="admin-user-table">
            <thead>
              <tr>
                <th>Username</th>
                <th>Full name</th>
                <th>Role</th>
                <th>Status</th>
                <th>Created</th>
                <th></th>
              </tr>
            </thead>
            <tbody>
              {users.map((u) => (
                <UserRow
                  key={u.id}
                  user={u}
                  isSelf={currentUser?.username === u.username}
                  onChanged={(updated) =>
                    setUsers((prev) => prev.map((x) => (x.id === updated.id ? updated : x)))
                  }
                  onDeleted={(id) => setUsers((prev) => prev.filter((x) => x.id !== id))}
                />
              ))}
            </tbody>
          </table>
        </div>
      )}
    </>
  )
}
