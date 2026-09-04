import { useCallback, useEffect, useState } from 'react'
import {
  createCamera,
  deleteCamera,
  listCameras,
  updateCamera,
  type CameraInput,
} from '../api/admin'
import { extractApiError } from '../lib/apiError'
import type { AdminCamera } from '../types/api'
import { useT } from '../lib/useT'
import type { Translate } from '../lib/i18n'
import { NumberField } from '../components/NumberField'
import {
  IconAlertCircle,
  IconCamera,
  IconCheck,
  IconPencil,
  IconPlus,
  IconTrash,
  IconX,
} from '../components/icons'

const BLANK: CameraInput = {
  cameraId: '',
  name: '',
  site: '',
  latitude: 33.5731,
  longitude: -7.5898,
  elevationM: 0,
  headingDeg: 0,
  active: true,
}

interface RowProps {
  camera: AdminCamera
  t: Translate
  onChanged: (c: AdminCamera) => void
  onDeleted: (id: number) => void
}

function CameraRow({ camera, t, onChanged, onDeleted }: RowProps) {
  const [editing, setEditing] = useState(false)
  const [form, setForm] = useState<CameraInput>(toInput(camera))
  const [busy, setBusy] = useState(false)
  const [err, setErr] = useState<string | null>(null)

  function startEdit() {
    setForm(toInput(camera))
    setErr(null)
    setEditing(true)
  }

  async function onSave() {
    setErr(null)
    setBusy(true)
    try {
      onChanged(await updateCamera(camera.id, cleaned(form)))
      setEditing(false)
    } catch (e) {
      setErr(extractApiError(e, t('admin.updateFailed')))
    } finally {
      setBusy(false)
    }
  }

  async function onDelete() {
    if (!window.confirm(t('admin.cameras.confirmDelete', { name: camera.cameraId }))) return
    setErr(null)
    setBusy(true)
    try {
      await deleteCamera(camera.id)
      onDeleted(camera.id)
    } catch (e) {
      setErr(extractApiError(e, t('admin.deleteFailed')))
      setBusy(false)
    }
  }

  if (editing) {
    return (
      <tr>
        <td><input value={form.cameraId} onChange={(e) => setForm({ ...form, cameraId: e.target.value })} disabled={busy} /></td>
        <td><input value={form.name} onChange={(e) => setForm({ ...form, name: e.target.value })} disabled={busy} /></td>
        <td><input value={form.site ?? ''} onChange={(e) => setForm({ ...form, site: e.target.value })} disabled={busy} /></td>
        <td>
          <div className="admin-coord-pair">
            <NumberField value={form.latitude} onChange={(v) => setForm({ ...form, latitude: v })} step={0.0001} min={-90} max={90} disabled={busy} aria-label={t('field.latitude')} />
            <NumberField value={form.longitude} onChange={(v) => setForm({ ...form, longitude: v })} step={0.0001} min={-180} max={180} disabled={busy} aria-label={t('field.longitude')} />
          </div>
          {err && <div className="login-error" style={{ marginTop: 6 }}>{err}</div>}
        </td>
        <td>
          <NumberField
            value={form.headingDeg ?? 0}
            onChange={(v) => setForm({ ...form, headingDeg: v })}
            step={1} min={0} max={360}
            disabled={busy}
            title={t('admin.cameras.headingHint')}
            aria-label={t('admin.cameras.colHeading')}
          />
        </td>
        <td>
          <label className="admin-inline-check">
            <input type="checkbox" checked={form.active ?? true} onChange={(e) => setForm({ ...form, active: e.target.checked })} disabled={busy} />
            {t('admin.active')}
          </label>
        </td>
        <td className="cell-actions">
          <div className="action-group">
            <button type="button" className="btn btn-sm" onClick={onSave} disabled={busy}>
              <IconCheck size={14} />
              {t('admin.save')}
            </button>
            <button type="button" className="btn secondary btn-sm" onClick={() => setEditing(false)} disabled={busy}>
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
      <td className="cell-mono"><b>{camera.cameraId}</b></td>
      <td>{camera.name}</td>
      <td>{camera.site ?? t('common.none')}</td>
      <td className="cell-mono cell-num">
        {camera.latitude.toFixed(5)}, {camera.longitude.toFixed(5)}
      </td>
      <td className="cell-num">
        {camera.headingDeg != null ? `${camera.headingDeg.toFixed(0)}°` : t('common.none')}
      </td>
      <td>
        <span className={`alert-status-badge status-${camera.active ? 'RESOLVED' : 'NEW'}`}>
          {camera.active ? t('admin.active') : t('admin.inactive')}
        </span>
      </td>
      <td className="cell-actions">
        <div className="action-group">
          <button type="button" className="btn ghost btn-sm" onClick={startEdit} disabled={busy}>
            <IconPencil size={14} />
            {t('admin.edit')}
          </button>
          <button
            type="button"
            className="btn danger btn-sm btn-icon"
            onClick={onDelete}
            disabled={busy}
            title={t('common.delete')}
            aria-label={t('common.delete')}
          >
            <IconTrash size={14} />
          </button>
        </div>
        {err && <div className="login-error" style={{ marginTop: 6 }}>{err}</div>}
      </td>
    </tr>
  )
}

export function AdminCamerasPage() {
  const t = useT()
  const [cameras, setCameras] = useState<AdminCamera[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [form, setForm] = useState<CameraInput>(BLANK)
  const [creating, setCreating] = useState(false)
  const [formError, setFormError] = useState<string | null>(null)

  /* Memoised on `t` — see the note in AdminUsersPage. */
  const refresh = useCallback(() => {
    setLoading(true)
    listCameras()
      .then(setCameras)
      .catch((e) => setError(extractApiError(e, t('admin.cameras.loadFailed'))))
      .finally(() => setLoading(false))
  }, [t])

  useEffect(() => { refresh() }, [refresh])

  async function onCreate(e: React.FormEvent) {
    e.preventDefault()
    setFormError(null)
    setCreating(true)
    try {
      await createCamera(cleaned(form))
      setForm(BLANK)
      refresh()
    } catch (err) {
      setFormError(extractApiError(err, t('admin.cameras.createFailed')))
    } finally {
      setCreating(false)
    }
  }

  return (
    <>
      <div className="page-header">
        <div>
          <h2>{t('admin.cameras.title')}</h2>
          <p>{t('admin.cameras.subtitle')}</p>
        </div>
      </div>

      <div className="card" style={{ marginBottom: 18 }}>
        <h3>{t('admin.cameras.new')}</h3>
        <form onSubmit={onCreate} className="admin-user-form">
          <label className="login-field">
            <span>{t('admin.cameras.id')}</span>
            <input value={form.cameraId} onChange={(e) => setForm({ ...form, cameraId: e.target.value })} required />
          </label>
          <label className="login-field">
            <span>{t('admin.cameras.name')}</span>
            <input value={form.name} onChange={(e) => setForm({ ...form, name: e.target.value })} required />
          </label>
          <label className="login-field">
            <span>{t('admin.cameras.site')}</span>
            <input value={form.site ?? ''} onChange={(e) => setForm({ ...form, site: e.target.value })} />
          </label>
          <label className="login-field">
            <span>{t('field.latitude')}</span>
            <NumberField value={form.latitude} onChange={(v) => setForm({ ...form, latitude: v })} step={0.0001} min={-90} max={90} required />
          </label>
          <label className="login-field">
            <span>{t('field.longitude')}</span>
            <NumberField value={form.longitude} onChange={(v) => setForm({ ...form, longitude: v })} step={0.0001} min={-180} max={180} required />
          </label>
          <label className="login-field">
            <span>{t('admin.cameras.heading')}</span>
            <NumberField
              value={form.headingDeg ?? 0}
              onChange={(v) => setForm({ ...form, headingDeg: v })}
              step={1} min={0} max={360}
              title={t('admin.cameras.headingHint')}
            />
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
              {creating ? t('admin.cameras.creating') : t('admin.cameras.create')}
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

      {!loading && cameras.length === 0 && !error && (
        <div className="empty-state">
          <IconCamera size={26} />
          <span>{t('common.none')}</span>
        </div>
      )}

      {!loading && cameras.length > 0 && (
        <div className="card table-card">
          <div className="table-scroll">
            <table className="admin-user-table">
              <thead>
                <tr>
                  <th>{t('admin.cameras.id')}</th>
                  <th>{t('admin.cameras.name')}</th>
                  <th>{t('admin.cameras.colSite')}</th>
                  <th>{t('admin.cameras.location')}</th>
                  <th>{t('admin.cameras.colHeading')}</th>
                  <th>{t('field.status')}</th>
                  <th aria-label={t('common.actions')} />
                </tr>
              </thead>
              <tbody>
                {cameras.map((c) => (
                  <CameraRow
                    key={c.id}
                    camera={c}
                    t={t}
                    onChanged={(u) => setCameras((prev) => prev.map((x) => (x.id === u.id ? u : x)))}
                    onDeleted={(id) => setCameras((prev) => prev.filter((x) => x.id !== id))}
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

function toInput(c: AdminCamera): CameraInput {
  return {
    cameraId: c.cameraId,
    name: c.name,
    site: c.site ?? '',
    latitude: c.latitude,
    longitude: c.longitude,
    elevationM: c.elevationM ?? 0,
    headingDeg: c.headingDeg ?? 0,
    active: c.active,
  }
}

function cleaned(input: CameraInput): CameraInput {
  return {
    ...input,
    cameraId: input.cameraId.trim(),
    name: input.name.trim(),
    site: input.site?.trim() || undefined,
  }
}
