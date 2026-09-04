import { useCallback, useEffect, useState } from 'react'
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
import type { Translate } from '../lib/i18n'
import {
  IconAlertCircle,
  IconCheck,
  IconPlus,
  IconTrash,
  IconUsers,
  IconX,
} from '../components/icons'

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
  t: Translate
  onChanged: (updated: AdminUser) => void
  onDeleted: (id: number) => void
}

function UserRow({ user, isSelf, t, onChanged, onDeleted }: RowProps) {
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
      setRowError(err instanceof Error ? extractMessage(err.message) : t('admin.actionFailed'))
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
      setRowError(err instanceof Error ? extractMessage(err.message) : t('admin.updateFailed'))
    } finally {
      setBusy(false)
    }
  }

  async function onDelete() {
    if (!window.confirm(t('admin.users.confirmDelete', { name: user.username }))) return
    setRowError(null)
    setBusy(true)
    try {
      await deleteUser(user.id)
      onDeleted(user.id)
    } catch (err) {
      setRowError(err instanceof Error ? extractMessage(err.message) : t('admin.deleteFailed'))
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
            title={isSelf && user.role === 'ADMIN' ? t('admin.users.selfRole') : undefined}
          >
            {ROLES.map((r) => (
              <option key={r} value={r}>
                {t(`role.${r}`)}
              </option>
            ))}
          </select>
        </td>
        <td colSpan={2}>
          <input
            type="password"
            placeholder={t('admin.users.newPassword')}
            value={newPassword}
            onChange={(e) => setNewPassword(e.target.value)}
            disabled={busy}
          />
          {rowError && <div className="login-error">{rowError}</div>}
        </td>
        <td className="cell-actions">
          <div className="action-group">
            <button type="button" className="btn btn-sm" onClick={onSave} disabled={busy}>
              <IconCheck size={14} />
              {t('admin.save')}
            </button>
            <button
              type="button"
              className="btn secondary btn-sm"
              onClick={() => setEditing(false)}
              disabled={busy}
            >
              <IconX size={14} />
              {t('common.cancel')}
            </button>
          </div>
        </td>
      </tr>
    )
  }

  return (
    <tr>
      <td><b>{user.username}</b></td>
      <td>{user.fullName ?? t('common.none')}</td>
      <td>
        <span className={`role-badge role-${user.role}`}>{t(`role.${user.role}`)}</span>
      </td>
      <td>
        <span className={`alert-status-badge status-${user.enabled ? 'RESOLVED' : 'NEW'}`}>
          {user.enabled ? t('admin.enabled') : t('admin.disabled')}
        </span>
      </td>
      <td className="cell-num">{new Date(user.createdAt).toLocaleDateString()}</td>
      <td className="cell-actions">
        {/*
          Row actions are unlabelled outlines rather than solid buttons.
          One "Delete" filled in red per row turned a four-row table into
          a column of red rectangles, which is a lot of visual insistence
          for the action nobody should take by accident.
        */}
        {/* Both text actions are label-only. "Edit" used to carry a pencil
            while "Disable" beside it carried nothing, which made two
            equal-weight actions look like different kinds of control. */}
        <div className="action-group">
          <button type="button" className="btn ghost btn-sm" onClick={startEdit} disabled={busy}>
            {t('admin.edit')}
          </button>
          <button
            type="button"
            className="btn ghost btn-sm"
            onClick={onToggleEnabled}
            disabled={busy || isSelf}
            title={isSelf ? t('admin.users.selfDisable') : undefined}
          >
            {user.enabled ? t('admin.disable') : t('admin.enable')}
          </button>
          <button
            type="button"
            className="btn danger btn-sm btn-icon"
            onClick={onDelete}
            disabled={busy || isSelf}
            title={isSelf ? t('admin.users.selfDelete') : t('common.delete')}
            aria-label={t('common.delete')}
          >
            <IconTrash size={14} />
          </button>
        </div>
        {rowError && <div className="login-error">{rowError}</div>}
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

  /*
   * Memoised on `t` because the failure message is written in the
   * operator's language: switching language re-runs the load, so an error
   * already on screen is replaced by the same error in the new language
   * rather than being left behind in the old one.
   */
  const refresh = useCallback(() => {
    setLoading(true)
    listUsers()
      .then(setUsers)
      .catch((e: unknown) =>
        setError(e instanceof Error ? extractMessage(e.message) : t('admin.users.loadFailed')),
      )
      .finally(() => setLoading(false))
  }, [t])

  useEffect(() => { refresh() }, [refresh])

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
        err instanceof Error ? extractMessage(err.message) : t('admin.users.createFailed'),
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

      <div className="card form-card">
        <h3>{t('admin.users.new')}</h3>
        <form onSubmit={onCreate} className="admin-user-form">
          <label className="login-field">
            <span>{t('admin.users.username')}</span>
            <input
              type="text"
              value={username}
              onChange={(e) => setUsername(e.target.value)}
              required
            />
          </label>
          <label className="login-field">
            <span>{t('admin.users.fullName')}</span>
            <input
              type="text"
              value={fullName}
              onChange={(e) => setFullName(e.target.value)}
            />
          </label>
          <label className="login-field">
            <span>{t('admin.users.password')}</span>
            <input
              type="password"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              minLength={6}
              required
            />
          </label>
          <label className="login-field">
            <span>{t('admin.users.role')}</span>
            <select value={role} onChange={(e) => setRole(e.target.value as Role)}>
              {ROLES.map((r) => (
                <option key={r} value={r}>
                  {t(`role.${r}`)}
                </option>
              ))}
            </select>
          </label>
          {formError && (
            <p className="login-error" role="alert">
              <IconAlertCircle size={15} />
              <span>{formError}</span>
            </p>
          )}
          <div className="form-footer">
            <button type="submit" className="btn" disabled={creating}>
              <IconPlus size={15} />
              {creating ? t('admin.users.creating') : t('admin.users.create')}
            </button>
          </div>
        </form>
      </div>

      {loading && <p className="muted">{t('common.loading')}</p>}
      {error && (
        <p className="login-error" role="alert">
          <IconAlertCircle size={15} />
          <span>{error}</span>
        </p>
      )}

      {!loading && users.length === 0 && !error && (
        <div className="empty-state">
          <IconUsers size={26} />
          <span>{t('common.none')}</span>
        </div>
      )}

      {!loading && users.length > 0 && (
        <div className="card table-card">
          <div className="table-scroll">
            <table className="admin-user-table">
              <thead>
                <tr>
                  <th>{t('admin.users.username')}</th>
                  <th>{t('admin.users.colFullName')}</th>
                  <th>{t('admin.users.role')}</th>
                  <th>{t('field.status')}</th>
                  <th>{t('admin.users.created')}</th>
                  <th aria-label={t('common.actions')} />
                </tr>
              </thead>
              <tbody>
                {users.map((u) => (
                  <UserRow
                    key={u.id}
                    user={u}
                    t={t}
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
        </div>
      )}
    </>
  )
}
