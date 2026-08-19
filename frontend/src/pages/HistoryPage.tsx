import { useEffect, useMemo, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { deleteAlert, deleteAllAlerts, listAlerts } from '../api/alerts'
import { useAuth } from '../context/AuthContext'
import { useLiveAlertsContext } from '../context/LiveAlertsContext'
import { extractApiError } from '../lib/apiError'
import type { Alert, AlertSeverity, AlertStatus } from '../types/api'
import { useT } from '../lib/useT'

const SEVS: ('' | AlertSeverity)[] = ['', 'LOW', 'MEDIUM', 'HIGH', 'CRITICAL']
const STATUSES: ('' | AlertStatus)[] = ['', 'NEW', 'ACKNOWLEDGED', 'RESOLVED']

export function HistoryPage() {
  const t = useT()
  const navigate = useNavigate()
  const { hasRole } = useAuth()
  const { removeAlerts } = useLiveAlertsContext()
  const [severity, setSeverity] = useState<'' | AlertSeverity>('')
  const [status, setStatus] = useState<'' | AlertStatus>('')
  const [alerts, setAlerts] = useState<Alert[]>([])
  const [loading, setLoading] = useState(true)
  const [busyId, setBusyId] = useState<number | null>(null)
  const [clearing, setClearing] = useState(false)
  const [error, setError] = useState<string | null>(null)

  // Deleting an alert destroys the incident record, so it is an
  // administrator's call — an operator closes incidents by resolving them.
  const canDelete = hasRole('ADMIN')

  useEffect(() => {
    setLoading(true)
    listAlerts({ severity: severity || undefined, limit: 500 })
      .then(setAlerts)
      .finally(() => setLoading(false))
  }, [severity])

  const visible = useMemo(
    () => (status ? alerts.filter((a) => a.status === status) : alerts),
    [alerts, status],
  )

  const resolvedCount = useMemo(
    () => alerts.filter((a) => a.status === 'RESOLVED').length,
    [alerts],
  )

  async function onDelete(alert: Alert) {
    const label = `[${t(`severity.${alert.severity}`)}] ${t(`alertType.${alert.type}`)}`
    if (!window.confirm(t('history.confirmDelete', { label }))) return

    setError(null)
    setBusyId(alert.id)
    try {
      await deleteAlert(alert.id)
      setAlerts((prev) => prev.filter((a) => a.id !== alert.id))
      removeAlerts([alert.id]) // keep the Dashboard's live list honest
    } catch (e) {
      setError(extractApiError(e, t('history.deleteFailed')))
    } finally {
      setBusyId(null)
    }
  }

  async function onClear(onlyResolved: boolean) {
    const what = onlyResolved
      ? t('history.confirmClearResolved', { count: resolvedCount })
      : t('history.confirmClearAll')
    const consequence = onlyResolved ? t('history.keepsOpen') : t('history.wipesAll')
    if (!window.confirm(`${what}\n\n${consequence}`)) return

    setError(null)
    setClearing(true)
    try {
      await deleteAllAlerts(onlyResolved)
      const remaining = onlyResolved ? alerts.filter((a) => a.status !== 'RESOLVED') : []
      setAlerts(remaining)
      removeAlerts(onlyResolved ? alerts.filter((a) => a.status === 'RESOLVED').map((a) => a.id) : 'all')
    } catch (e) {
      setError(extractApiError(e, t('history.clearFailed')))
    } finally {
      setClearing(false)
    }
  }

  return (
    <>
      <div className="page-header">
        <div>
          <h2>{t('history.title')}</h2>
          <p>{t('history.subtitle')}</p>
        </div>
        {canDelete && (
          <div style={{ display: 'flex', gap: 8, alignItems: 'center' }}>
            <button
              type="button"
              className="btn secondary btn-sm"
              disabled={clearing || resolvedCount === 0}
              title={t('history.clearResolvedTitle')}
              onClick={() => onClear(true)}
            >
              {t('history.clearResolved')}{resolvedCount > 0 ? ` (${resolvedCount})` : ''}
            </button>
            <button
              type="button"
              className="btn danger btn-sm"
              disabled={clearing || alerts.length === 0}
              title={t('history.deleteAllTitle')}
              onClick={() => onClear(false)}
            >
              {t('history.deleteAll')}
            </button>
          </div>
        )}
      </div>

      {error && <p className="login-error">{error}</p>}

      <div className="card" style={{ marginBottom: 20 }}>
        <div className="form-row">
          <label>{t('common.severity')}</label>
          <select value={severity} onChange={(e) => setSeverity(e.target.value as AlertSeverity | '')}>
            {SEVS.map((s) => (
              <option key={s} value={s}>
                {s ? t(`severity.${s}`) : t('common.all')}
              </option>
            ))}
          </select>
          <label style={{ marginLeft: 16 }}>{t('common.status')}</label>
          <select value={status} onChange={(e) => setStatus(e.target.value as AlertStatus | '')}>
            {STATUSES.map((s) => (
              <option key={s} value={s}>
                {s ? t(`status.${s}`) : t('common.all')}
              </option>
            ))}
          </select>
        </div>
      </div>

      {loading && <p className="muted">{t('common.loading')}</p>}
      {!loading && visible.length === 0 && <p className="muted">{t('history.empty')}</p>}

      <div className="timeline">
        {visible.map((a) => (
          <div key={a.id} className={`timeline-item sev-${a.severity}`}>
            <div className="card">
              <div style={{ display: 'flex', justifyContent: 'space-between', gap: 8 }}>
                <b>
                  [{t(`severity.${a.severity}`)}] {t(`alertType.${a.type}`)}
                </b>
                <span style={{ display: 'flex', gap: 8, alignItems: 'center', whiteSpace: 'nowrap' }}>
                  <span className={`alert-status-badge status-${a.status}`}>
                    {t(`status.${a.status}`)}
                  </span>
                  <time className="muted">
                    {new Date(a.createdAt).toLocaleString()}
                  </time>
                </span>
              </div>
              <div style={{ marginTop: 6 }}>{a.message}</div>
              {a.zoneName && (
                <div className="muted" style={{ marginTop: 4 }}>
                  {t('alert.zone')} {a.zoneName}
                </div>
              )}
              {a.status === 'ACKNOWLEDGED' && a.acknowledgedAt && (
                <div className="muted" style={{ marginTop: 4, fontStyle: 'italic' }}>
                  {t('history.acknowledgedBy', {
                    who: a.acknowledgedBy ?? t('alert.by'),
                    when: new Date(a.acknowledgedAt).toLocaleString(),
                  })}
                </div>
              )}
              {a.status === 'RESOLVED' && a.resolvedAt && (
                <div className="muted" style={{ marginTop: 4, fontStyle: 'italic' }}>
                  {t('history.resolvedBy', {
                    who: a.resolvedBy ?? t('alert.by'),
                    when: new Date(a.resolvedAt).toLocaleString(),
                  })}
                  {a.resolutionNote ? ` — “${a.resolutionNote}”` : ''}
                </div>
              )}
              <div style={{ display: 'flex', gap: 8, marginTop: 8 }}>
                {a.latitude != null && a.longitude != null && (
                  <button
                    type="button"
                    className="btn secondary btn-sm"
                    title={t('alert.mapTitle')}
                    onClick={() => navigate(`/map3d?lat=${a.latitude}&lon=${a.longitude}`)}
                  >
                    {t('history.viewOnMap')}
                  </button>
                )}
                {canDelete && (
                  <button
                    type="button"
                    className="btn danger btn-sm"
                    disabled={busyId === a.id}
                    title={t('history.deleteTitle')}
                    onClick={() => onDelete(a)}
                  >
                    {busyId === a.id ? t('common.deleting') : `🗑 ${t('common.delete')}`}
                  </button>
                )}
              </div>
            </div>
          </div>
        ))}
      </div>
    </>
  )
}
