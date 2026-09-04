import { useCallback, useEffect, useState } from 'react'
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
import type { Translate } from '../lib/i18n'
import { NumberField } from '../components/NumberField'
import {
  IconAlertCircle,
  IconCheck,
  IconPencil,
  IconPlus,
  IconTrash,
  IconX,
  IconZones,
} from '../components/icons'

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
  t: Translate
  onChanged: (z: Zone) => void
  onDeleted: (id: number) => void
}

function ZoneRow({ zone, t, onChanged, onDeleted }: RowProps) {
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
      setErr(extractApiError(e, t('admin.updateFailed')))
    } finally {
      setBusy(false)
    }
  }

  async function onDelete() {
    if (!window.confirm(t('admin.zones.confirmDelete', { name: zone.name }))) return
    setErr(null)
    setBusy(true)
    try {
      await deleteZone(zone.id)
      onDeleted(zone.id)
    } catch (e) {
      setErr(extractApiError(e, t('admin.deleteFailed')))
      setBusy(false)
    }
  }

  if (editing) {
    return (
      <tr>
        <td><input value={form.name} onChange={(e) => setForm({ ...form, name: e.target.value })} disabled={busy} /></td>
        <td>
          <select value={form.type} onChange={(e) => setForm({ ...form, type: e.target.value as ZoneType })} disabled={busy}>
            {ZONE_TYPES.map((zt) => (
              <option key={zt} value={zt}>{t(`zoneType.${zt}`)}</option>
            ))}
          </select>
        </td>
        <td>
          <div className="admin-coord-pair">
            <NumberField value={form.centerLat} onChange={(v) => setForm({ ...form, centerLat: v })} step={0.0001} min={-90} max={90} disabled={busy} aria-label={t('field.latitude')} />
            <NumberField value={form.centerLon} onChange={(v) => setForm({ ...form, centerLon: v })} step={0.0001} min={-180} max={180} disabled={busy} aria-label={t('field.longitude')} />
          </div>
          {err && <div className="login-error" style={{ marginTop: 6 }}>{err}</div>}
        </td>
        <td><NumberField value={form.radiusM} onChange={(v) => setForm({ ...form, radiusM: v })} step={5} min={1} disabled={busy} aria-label={t('admin.zones.radius')} /></td>
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
      <td><b>{zone.name}</b></td>
      <td><span className={`zone-type-badge zt-${zone.type}`}>{t(`zoneType.${zone.type}`)}</span></td>
      <td className="cell-mono cell-num">
        {zone.centerLat.toFixed(5)}, {zone.centerLon.toFixed(5)}
      </td>
      <td className="cell-num">{zone.radiusM} m</td>
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

export function AdminZonesPage() {
  const t = useT()
  const [zones, setZones] = useState<Zone[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [form, setForm] = useState<ZoneInput>(BLANK)
  const [creating, setCreating] = useState(false)
  const [formError, setFormError] = useState<string | null>(null)

  /* Memoised on `t` — see the note in AdminUsersPage. */
  const refresh = useCallback(() => {
    setLoading(true)
    listZonesAdmin()
      .then(setZones)
      .catch((e) => setError(extractApiError(e, t('admin.zones.loadFailed'))))
      .finally(() => setLoading(false))
  }, [t])

  useEffect(() => { refresh() }, [refresh])

  async function onCreate(e: React.FormEvent) {
    e.preventDefault()
    setFormError(null)
    setCreating(true)
    try {
      await createZone(cleaned(form))
      setForm(BLANK)
      refresh()
    } catch (err) {
      setFormError(extractApiError(err, t('admin.zones.createFailed')))
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

      <div className="card" style={{ marginBottom: 18 }}>
        <h3>{t('admin.zones.new')}</h3>
        <form onSubmit={onCreate} className="admin-user-form">
          <label className="login-field">
            <span>{t('admin.zones.name')}</span>
            <input value={form.name} onChange={(e) => setForm({ ...form, name: e.target.value })} required />
          </label>
          <label className="login-field">
            <span>{t('admin.zones.type')}</span>
            <select value={form.type} onChange={(e) => setForm({ ...form, type: e.target.value as ZoneType })}>
              {ZONE_TYPES.map((zt) => (
                <option key={zt} value={zt}>{t(`zoneType.${zt}`)}</option>
              ))}
            </select>
          </label>
          <label className="login-field">
            <span>{t('field.latitude')}</span>
            <NumberField value={form.centerLat} onChange={(v) => setForm({ ...form, centerLat: v })} step={0.0001} min={-90} max={90} required />
          </label>
          <label className="login-field">
            <span>{t('field.longitude')}</span>
            <NumberField value={form.centerLon} onChange={(v) => setForm({ ...form, centerLon: v })} step={0.0001} min={-180} max={180} required />
          </label>
          <label className="login-field">
            <span>{t('admin.zones.radius')}</span>
            <NumberField value={form.radiusM} onChange={(v) => setForm({ ...form, radiusM: v })} step={5} min={1} required />
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
              {creating ? t('admin.zones.creating') : t('admin.zones.create')}
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

      {!loading && zones.length === 0 && !error && (
        <div className="empty-state">
          <IconZones size={26} />
          <span>{t('common.none')}</span>
        </div>
      )}

      {!loading && zones.length > 0 && (
        <div className="card table-card">
          <div className="table-scroll">
            <table className="admin-user-table">
              <thead>
                <tr>
                  <th>{t('admin.zones.name')}</th>
                  <th>{t('admin.zones.type')}</th>
                  <th>{t('admin.zones.center')}</th>
                  <th>{t('admin.zones.colRadius')}</th>
                  <th aria-label={t('common.actions')} />
                </tr>
              </thead>
              <tbody>
                {zones.map((z) => (
                  <ZoneRow
                    key={z.id}
                    zone={z}
                    t={t}
                    onChanged={(u) => setZones((prev) => prev.map((x) => (x.id === u.id ? u : x)))}
                    onDeleted={(id) => setZones((prev) => prev.filter((x) => x.id !== id))}
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
