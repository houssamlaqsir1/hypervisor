import { useEffect, useState } from 'react'
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
import { NumberField } from '../components/NumberField'

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
  onChanged: (c: AdminCamera) => void
  onDeleted: (id: number) => void
}

function CameraRow({ camera, onChanged, onDeleted }: RowProps) {
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
      setErr(extractApiError(e, 'Update failed'))
    } finally {
      setBusy(false)
    }
  }

  async function onDelete() {
    if (!window.confirm(`Delete camera "${camera.cameraId}"?`)) return
    setErr(null)
    setBusy(true)
    try {
      await deleteCamera(camera.id)
      onDeleted(camera.id)
    } catch (e) {
      setErr(extractApiError(e, 'Delete failed'))
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
            <NumberField value={form.latitude} onChange={(v) => setForm({ ...form, latitude: v })} step={0.0001} min={-90} max={90} disabled={busy} aria-label="Latitude" />
            <NumberField value={form.longitude} onChange={(v) => setForm({ ...form, longitude: v })} step={0.0001} min={-180} max={180} disabled={busy} aria-label="Longitude" />
          </div>
          {err && <div className="login-error" style={{ marginTop: 6 }}>{err}</div>}
        </td>
        <td>
          <NumberField
            value={form.headingDeg ?? 0}
            onChange={(v) => setForm({ ...form, headingDeg: v })}
            step={1} min={0} max={360}
            disabled={busy}
            title="Bearing the camera faces, degrees clockwise from north"
            aria-label="Heading"
          />
        </td>
        <td>
          <label className="admin-inline-check">
            <input type="checkbox" checked={form.active ?? true} onChange={(e) => setForm({ ...form, active: e.target.checked })} disabled={busy} /> active
          </label>
        </td>
        <td style={{ whiteSpace: 'nowrap' }}>
          <button type="button" className="btn btn-sm" onClick={onSave} disabled={busy}>Save</button>{' '}
          <button type="button" className="btn secondary btn-sm" onClick={() => setEditing(false)} disabled={busy}>Cancel</button>
        </td>
      </tr>
    )
  }

  return (
    <tr>
      <td><b>{camera.cameraId}</b></td>
      <td>{camera.name}</td>
      <td>{camera.site ?? '—'}</td>
      <td>{camera.latitude.toFixed(5)}, {camera.longitude.toFixed(5)}</td>
      <td>{camera.headingDeg != null ? `${camera.headingDeg.toFixed(0)}°` : '—'}</td>
      <td>
        <span className={`alert-status-badge status-${camera.active ? 'RESOLVED' : 'NEW'}`}>
          {camera.active ? 'Active' : 'Inactive'}
        </span>
      </td>
      <td style={{ whiteSpace: 'nowrap' }}>
        <button type="button" className="btn secondary btn-sm" onClick={startEdit} disabled={busy}>Edit</button>{' '}
        <button type="button" className="btn danger btn-sm" onClick={onDelete} disabled={busy}>Delete</button>
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

  function refresh() {
    setLoading(true)
    listCameras()
      .then(setCameras)
      .catch((e) => setError(extractApiError(e, 'Failed to load cameras')))
      .finally(() => setLoading(false))
  }

  useEffect(refresh, [])

  async function onCreate(e: React.FormEvent) {
    e.preventDefault()
    setFormError(null)
    setCreating(true)
    try {
      await createCamera(cleaned(form))
      setForm(BLANK)
      refresh()
    } catch (err) {
      setFormError(extractApiError(err, 'Failed to create camera'))
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

      <div className="card" style={{ marginBottom: 24 }}>
        <h3 style={{ marginBottom: 16 }}>New camera</h3>
        <form onSubmit={onCreate} className="admin-user-form">
          <label className="login-field">
            <span>Camera ID</span>
            <input value={form.cameraId} onChange={(e) => setForm({ ...form, cameraId: e.target.value })} required />
          </label>
          <label className="login-field">
            <span>Name</span>
            <input value={form.name} onChange={(e) => setForm({ ...form, name: e.target.value })} required />
          </label>
          <label className="login-field">
            <span>Site (optional)</span>
            <input value={form.site ?? ''} onChange={(e) => setForm({ ...form, site: e.target.value })} />
          </label>
          <label className="login-field">
            <span>Latitude</span>
            <NumberField value={form.latitude} onChange={(v) => setForm({ ...form, latitude: v })} step={0.0001} min={-90} max={90} required />
          </label>
          <label className="login-field">
            <span>Longitude</span>
            <NumberField value={form.longitude} onChange={(v) => setForm({ ...form, longitude: v })} step={0.0001} min={-180} max={180} required />
          </label>
          <label className="login-field">
            <span>Heading (° from north)</span>
            <NumberField
              value={form.headingDeg ?? 0}
              onChange={(v) => setForm({ ...form, headingDeg: v })}
              step={1} min={0} max={360}
              title="Which way the camera faces. Detections are placed relative to the camera using it."
            />
          </label>
          {formError && <p className="login-error">{formError}</p>}
          <button type="submit" className="btn" disabled={creating}>
            {creating ? 'Creating…' : 'Create camera'}
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
                <th>Camera ID</th>
                <th>Name</th>
                <th>Site</th>
                <th>Location (lat, lon)</th>
                <th>Heading</th>
                <th>Status</th>
                <th></th>
              </tr>
            </thead>
            <tbody>
              {cameras.map((c) => (
                <CameraRow
                  key={c.id}
                  camera={c}
                  onChanged={(u) => setCameras((prev) => prev.map((x) => (x.id === u.id ? u : x)))}
                  onDeleted={(id) => setCameras((prev) => prev.filter((x) => x.id !== id))}
                />
              ))}
            </tbody>
          </table>
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
