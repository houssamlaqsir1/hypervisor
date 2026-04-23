import type { Alert } from '../types/api'

interface Props {
  alert: Alert
}

export function AlertRow({ alert }: Props) {
  const ts = new Date(alert.createdAt).toLocaleString()
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
      </div>
      <time>{ts}</time>
    </div>
  )
}
