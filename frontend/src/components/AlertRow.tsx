import type { Alert, AlertDetails } from '../types/api'

interface Props {
  alert: Alert
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
  const ts = new Date(alert.createdAt).toLocaleString()
  const isFusion = alert.type === 'FUSION' && alert.details != null
  return (
    <div className={`alert-row sev-${alert.severity}`}>
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
      </div>
      <time>{ts}</time>
    </div>
  )
}
