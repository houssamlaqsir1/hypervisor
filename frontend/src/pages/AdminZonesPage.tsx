import { useEffect, useState } from 'react'
import {
  createZone,
  deleteZone,
  listZonesAdmin,
  updateZone,
  type ZoneInput,
} from '../api/admin'
import { extractApiError } from '../lib/apiError'
import type { Zone, ZoneType } from '../types/api'
import { useT } from '../lib/useT'
import { NumberField } from '../components/NumberField'

const ZONE_TYPES: ZoneType[] = ['STATION', 'TRACK', 'RESTRICTED', 'NORMAL']

const BLANK: ZoneInput = {
  name: '',
  type: 'STATION',
  description: '',
  centerLat: 33.5731,
  centerLon: -7.5898,
  radiusM: 120,
}

interface RowProps {
  zone: Zone
  onChanged: (z: Zone) => void
  onDeleted: (id: number) => void
}

function ZoneRow({ zone, onChanged, onDeleted }: RowProps) {
  const [editing, setEditing] = useState(false)
  const [form, setForm] = useState<ZoneInput>(toInput(zone))
  const [busy, setBusy] = useState(false)
  const [err, setErr] = useState<string | null>(null)

  function startEdit() {
    setForm(toInput(zone))
    setErr(null)
    setEditing(true)
  }

  async function onSave() {
    setErr(null)
    setBusy(true)
    try {
      onChanged(await updateZone(zone.id, cleaned(form)))
      setEditing(false)
    } catch (e) {
      setErr(extractApiError(e, 'Update failed'))
    } finally {
      setBusy(false)
    }
  }

  async function onDelete() {
    if (!window.confirm(`Delete zone "${zone.name}"?`)) return
    setErr(null)
    setBusy(true)
    try {
      await deleteZone(zone.id)
      onDeleted(zone.id)
    } catch (e) {
      setErr(extractApiError(e, 'Delete failed'))
      setBusy(false)
    }
  }

  if (editing) {
    return (
      <tr>
        <td><input value={form.name} onChange={(e) => setForm({ ...form, name: e.target.value })} disabled={busy} /></td>
        <td>
          <select value={form.type} onChange={(e) => setForm({ ...form, type: e.target.value as ZoneType })} disabled={busy}>
            {ZONE_TYPES.map((t) => <option key={t} value={t}>{t}</option>)}
          </select>
        </td>
        <td>
          <div className="admin-coord-pair">
            <NumberField value={form.centerLat} onChange={(v) => setForm({ ...form, centerLat: v })} step={0.0001} min={-90} max={90} disabled={busy} aria-label="Latitude" />
            <NumberField value={form.centerLon} onChange={(v) => setForm({ ...form, centerLon: v })} step={0.0001} min={-180} max={180} disabled={busy} aria-label="Longitude" />
          </div>
          {err && <div className="login-error" style={{ marginTop: 6 }}>{err}</div>}
        </td>
        <td><NumberField value={form.radiusM} onChange={(v) => setForm({ ...form, radiusM: v })} step={5} min={1} disabled={busy} aria-label="Radius (m)" /></td>
        <td style={{ whiteSpace: 'nowrap' }}>
          <button type="button" className="btn btn-sm" onClick={onSave} disabled={busy}>Save</button>{' '}
          <button type="button" className="btn secondary btn-sm" onClick={() => setEditing(false)} disabled={busy}>Cancel</button>
        </td>
      </tr>
    )
  }

  return (
    <tr>
      <td><b>{zone.name}</b></td>
      <td><span className={`zone-type-badge zt-${zone.type}`}>{zone.type}</span></td>
      <td>{zone.centerLat.toFixed(5)}, {zone.centerLon.toFixed(5)}</td>
      <td>{zone.radiusM} m</td>
      <td style={{ whiteSpace: 'nowrap' }}>
        <button type="button" className="btn secondary btn-sm" onClick={startEdit} disabled={busy}>Edit</button>{' '}
        <button type="button" className="btn danger btn-sm" onClick={onDelete} disabled={busy}>Delete</button>
        {err && <div className="login-error" style={{ marginTop: 6 }}>{err}</div>}
      </td>
    </tr>
  )
}

export function AdminZonesPage() {
  const t = useT()
  const [zones, setZones] = useState<Zone[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [form, setForm] = useState<ZoneInput>(BLANK)
  const [creating, setCreating] = useState(false)
  const [formError, setFormError] = useState<string | null>(null)

  function refresh() {
    setLoading(true)
    listZonesAdmin()
      .then(setZones)
      .catch((e) => setError(extractApiError(e, 'Failed to load zones')))
      .finally(() => setLoading(false))
  }

  useEffect(refresh, [])

  async function onCreate(e: React.FormEvent) {
    e.preventDefault()
    setFormError(null)
    setCreating(true)
    try {
      await createZone(cleaned(form))
      setForm(BLANK)
      refresh()
    } catch (err) {
      setFormError(extractApiError(err, 'Failed to create zone'))
    } finally {
      setCreating(false)
    }
  }

  return (
    <>
      <div className="page-header">
        <div>
          <h2>{t('admin.zones.title')}</h2>
          <p>{t('admin.zones.subtitle')}</p>
        </div>
      </div>

      <div className="card" style={{ marginBottom: 24 }}>
        <h3 style={{ marginBottom: 16 }}>New zone</h3>
        <form onSubmit={onCreate} className="admin-user-form">
          <label className="login-field">
            <span>Name</span>
            <input value={form.name} onChange={(e) => setForm({ ...form, name: e.target.value })} required />
          </label>
          <label className="login-field">
            <span>Type</span>
            <select value={form.type} onChange={(e) => setForm({ ...form, type: e.target.value as ZoneType })}>
              {ZONE_TYPES.map((t) => <option key={t} value={t}>{t}</option>)}
            </select>
          </label>
          <label className="login-field">
            <span>Latitude</span>
            <NumberField value={form.centerLat} onChange={(v) => setForm({ ...form, centerLat: v })} step={0.0001} min={-90} max={90} required />
          </label>
          <label className="login-field">
            <span>Longitude</span>
            <NumberField value={form.centerLon} onChange={(v) => setForm({ ...form, centerLon: v })} step={0.0001} min={-180} max={180} required />
          </label>
          <label className="login-field">
            <span>Radius (m)</span>
            <NumberField value={form.radiusM} onChange={(v) => setForm({ ...form, radiusM: v })} step={5} min={1} required />
          </label>
          {formError && <p className="login-error">{formError}</p>}
          <button type="submit" className="btn" disabled={creating}>
            {creating ? 'Creating…' : 'Create zone'}
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
                <th>Name</th>
                <th>Type</th>
                <th>Center (lat, lon)</th>
                <th>Radius</th>
                <th></th>
              </tr>
            </thead>
            <tbody>
              {zones.map((z) => (
                <ZoneRow
                  key={z.id}
                  zone={z}
                  onChanged={(u) => setZones((prev) => prev.map((x) => (x.id === u.id ? u : x)))}
                  onDeleted={(id) => setZones((prev) => prev.filter((x) => x.id !== id))}
                />
              ))}
            </tbody>
          </table>
        </div>
      )}
    </>
  )
}

function toInput(z: Zone): ZoneInput {
  return {
    name: z.name,
    type: z.type,
    description: z.description ?? '',
    centerLat: z.centerLat,
    centerLon: z.centerLon,
    radiusM: z.radiusM,
  }
}

function cleaned(input: ZoneInput): ZoneInput {
  return {
    ...input,
    name: input.name.trim(),
    description: input.description?.trim() || undefined,
  }
}
