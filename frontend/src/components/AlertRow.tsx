import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { acknowledgeAlert, resolveAlert } from '../api/alerts'
import { useLiveAlertsContext } from '../context/LiveAlertsContext'
import type { Alert, AlertDetails, AlertStatus } from '../types/api'

interface Props {
  alert: Alert
}

const STATUS_LABEL: Record<AlertStatus, string> = {
  NEW: 'New',
  ACKNOWLEDGED: 'Acknowledged',
  RESOLVED: 'Resolved',
}

function formatWhen(iso: string | null): string {
  return iso ? new Date(iso).toLocaleString() : '—'
}

/** Color the fusion score chip on a green→amber→red gradient. */
function scoreClass(score: number | undefined): string {
  if (score == null) return 'fusion-chip-neutral'
  if (score >= 0.85) return 'fusion-chip-critical'
  if (score >= 0.65) return 'fusion-chip-high'
  if (score >= 0.45) return 'fusion-chip-medium'
  return 'fusion-chip-low'
}

function formatMeters(m: number | undefined): string {
  if (m == null || Number.isNaN(m)) return '—'
  if (m >= 1000) return `${(m / 1000).toFixed(2)} km`
  return `${m.toFixed(0)} m`
}

function formatSeconds(s: number | undefined): string {
  if (s == null || Number.isNaN(s)) return '—'
  if (s < 60) return `${s}s`
  const m = Math.floor(s / 60)
  const r = s % 60
  return `${m}m ${r}s`
}

function FusionDetails({ details }: { details: AlertDetails }) {
  const score = details.fusionScore
  return (
    <div className="alert-fusion-chips">
      {score != null && (
        <span className={`fusion-chip ${scoreClass(score)}`}>
          score <b>{score.toFixed(2)}</b>
        </span>
      )}
      {details.distanceM != null && (
        <span className="fusion-chip">
          Δd <b>{formatMeters(details.distanceM)}</b>
        </span>
      )}
      {details.timeDeltaSec != null && (
        <span className="fusion-chip">
          Δt <b>{formatSeconds(details.timeDeltaSec)}</b>
        </span>
      )}
      {details.cameraConfidence != null && (
        <span className="fusion-chip">
          conf <b>{(details.cameraConfidence * 100).toFixed(0)}%</b>
        </span>
      )}
      {details.camera?.id && (
        <span className="fusion-chip fusion-chip-source">
          cam <b>{details.camera.id}</b>
          {details.camera.label ? ` · ${details.camera.label}` : ''}
        </span>
      )}
      {details.sig?.sourceId && (
        <span className="fusion-chip fusion-chip-source">
          SIG <b>{details.sig.sourceId}</b>
        </span>
      )}
    </div>
  )
}

export function AlertRow({ alert }: Props) {
  const navigate = useNavigate()
  const { updateAlert } = useLiveAlertsContext()
  const [busy, setBusy] = useState(false)
  const [showNote, setShowNote] = useState(false)
  const [note, setNote] = useState('')

  const ts = new Date(alert.createdAt).toLocaleString()
  const isFusion = alert.type === 'FUSION' && alert.details != null
  const hasLocation = alert.latitude != null && alert.longitude != null

  async function onAcknowledge() {
    setBusy(true)
    try {
      updateAlert(await acknowledgeAlert(alert.id))
    } catch (e) {
      console.error('acknowledge failed', e)
    } finally {
      setBusy(false)
    }
  }

  async function onResolve() {
    setBusy(true)
    try {
      updateAlert(await resolveAlert(alert.id, note.trim() || undefined))
      setShowNote(false)
      setNote('')
    } catch (e) {
      console.error('resolve failed', e)
    } finally {
      setBusy(false)
    }
  }

  return (
    <div className={`alert-row sev-${alert.severity} status-${alert.status}`}>
      <span className="sev">{alert.severity}</span>
      <div className="message">
        <strong>{alert.type.replace('_', ' ')}</strong>
        <small>{alert.message}</small>
        {alert.zoneName && (
          <small>
            Zone: <b>{alert.zoneName}</b>
          </small>
        )}
        {isFusion && alert.details && <FusionDetails details={alert.details} />}
        {alert.status === 'ACKNOWLEDGED' && (
          <small className="alert-lifecycle">
            Acknowledged by <b>{alert.acknowledgedBy ?? 'operator'}</b> ·{' '}
            {formatWhen(alert.acknowledgedAt)}
          </small>
        )}
        {alert.status === 'RESOLVED' && (
          <small className="alert-lifecycle">
            Resolved by <b>{alert.resolvedBy ?? 'operator'}</b> ·{' '}
            {formatWhen(alert.resolvedAt)}
            {alert.resolutionNote ? ` — “${alert.resolutionNote}”` : ''}
          </small>
        )}
        {showNote && (
          <div className="alert-resolve-note">
            <input
              type="text"
              placeholder="Resolution note (optional)"
              value={note}
              onChange={(e) => setNote(e.target.value)}
              disabled={busy}
            />
            <button
              type="button"
              className="btn btn-sm"
              onClick={onResolve}
              disabled={busy}
            >
              Confirm resolve
            </button>
            <button
              type="button"
              className="btn secondary btn-sm"
              onClick={() => setShowNote(false)}
              disabled={busy}
            >
              Cancel
            </button>
          </div>
        )}
      </div>
      <div className="alert-row-side">
        <span className={`alert-status-badge status-${alert.status}`}>
          {STATUS_LABEL[alert.status]}
        </span>
        <time>{ts}</time>
      </div>
      <div className="alert-row-actions">
        {hasLocation && (
          <button
            type="button"
            className="btn secondary btn-sm"
            title="Fly to where this alert happened on the 3D map"
            onClick={() =>
              navigate(`/map3d?lat=${alert.latitude}&lon=${alert.longitude}`)
            }
          >
            🗺 Map
          </button>
        )}
        {alert.status === 'NEW' && (
          <button
            type="button"
            className="btn secondary btn-sm"
            onClick={onAcknowledge}
            disabled={busy}
          >
            Acknowledge
          </button>
        )}
        {alert.status !== 'RESOLVED' && !showNote && (
          <button
            type="button"
            className="btn btn-sm"
            onClick={() => setShowNote(true)}
            disabled={busy}
          >
            Resolve
          </button>
        )}
      </div>
    </div>
  )
}
