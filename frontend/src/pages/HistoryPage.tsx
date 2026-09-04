import { useEffect, useMemo, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { deleteAlert, deleteAllAlerts, listAlerts } from '../api/alerts'
import { useAuth } from '../context/AuthContext'
import { useLiveAlertsContext } from '../context/LiveAlertsContext'
import { extractApiError } from '../lib/apiError'
import type { Alert, AlertSeverity, AlertStatus } from '../types/api'
import { useT } from '../lib/useT'
import {
  IconAlertCircle,
  IconCheck,
  IconHistory,
  IconNavigation,
  IconTrash,
} from '../components/icons'

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
          <div className="page-actions">
            <button
              type="button"
              className="btn secondary btn-sm"
              disabled={clearing || resolvedCount === 0}
              title={t('history.clearResolvedTitle')}
              onClick={() => onClear(true)}
            >
              <IconCheck size={14} />
              {t('history.clearResolved')}{resolvedCount > 0 ? ` (${resolvedCount})` : ''}
            </button>
            <button
              type="button"
              className="btn danger btn-sm"
              disabled={clearing || alerts.length === 0}
              title={t('history.deleteAllTitle')}
              onClick={() => onClear(false)}
            >
              <IconTrash size={14} />
              {t('history.deleteAll')}
            </button>
          </div>
        )}
      </div>

      {error && (
        <p className="login-error" role="alert">
          <IconAlertCircle size={15} />
          <span>{error}</span>
        </p>
      )}

      {/* Filters are chrome above the results, not a card of their own —
          a full panel here competed with the incidents underneath it. */}
      <div className="filter-bar">
        <div className="form-row">
          <label htmlFor="filter-severity">{t('common.severity')}</label>
          <select
            id="filter-severity"
            value={severity}
            onChange={(e) => setSeverity(e.target.value as AlertSeverity | '')}
          >
            {SEVS.map((s) => (
              <option key={s} value={s}>
                {s ? t(`severity.${s}`) : t('common.all')}
              </option>
            ))}
          </select>
        </div>
        <div className="form-row">
          <label htmlFor="filter-status">{t('common.status')}</label>
          <select
            id="filter-status"
            value={status}
            onChange={(e) => setStatus(e.target.value as AlertStatus | '')}
          >
            {STATUSES.map((s) => (
              <option key={s} value={s}>
                {s ? t(`status.${s}`) : t('common.all')}
              </option>
            ))}
          </select>
        </div>
        {!loading && (
          <span className="filter-bar-count">
            {t('history.count', { count: visible.length })}
          </span>
        )}
      </div>

      {loading && <p className="muted">{t('common.loading')}</p>}
      {!loading && visible.length === 0 && (
        <div className="empty-state">
          <IconHistory size={26} />
          <span>{t('history.empty')}</span>
        </div>
      )}

      <div className="timeline">
        {visible.map((a) => (
          <div key={a.id} className={`timeline-item sev-${a.severity}`}>
            <div className="card timeline-card">
              <div className="timeline-card-head">
                {/*
                  Severity was previously spelled into the heading as
                  "[Critical] Object on track". It is a fixed set of four
                  values, so it belongs in the badge that already carries
                  the row's colour — leaving the heading to say only what
                  happened.
                */}
                <span className="timeline-card-title">
                  <span className={`alert-status-badge sev-badge sev-${a.severity}`}>
                    {t(`severity.${a.severity}`)}
                  </span>
                  {t(`alertType.${a.type}`)}
                </span>
                <span className="timeline-card-meta">
                  <span className={`alert-status-badge status-${a.status}`}>
                    {t(`status.${a.status}`)}
                  </span>
                  <time className="muted small">
                    {new Date(a.createdAt).toLocaleString()}
                  </time>
                </span>
              </div>

              <div className="timeline-card-body">{a.message}</div>

              {a.zoneName && (
                <div className="muted small" style={{ marginTop: 4 }}>
                  {t('alert.zone')} <b>{a.zoneName}</b>
                </div>
              )}
              {a.status === 'ACKNOWLEDGED' && a.acknowledgedAt && (
                <div className="timeline-card-note">
                  {t('history.acknowledgedBy', {
                    who: a.acknowledgedBy ?? t('alert.by'),
                    when: new Date(a.acknowledgedAt).toLocaleString(),
                  })}
                </div>
              )}
              {a.status === 'RESOLVED' && a.resolvedAt && (
                <div className="timeline-card-note">
                  {t('history.resolvedBy', {
                    who: a.resolvedBy ?? t('alert.by'),
                    when: new Date(a.resolvedAt).toLocaleString(),
                  })}
                  {a.resolutionNote ? ` — “${a.resolutionNote}”` : ''}
                </div>
              )}

              {(canDelete || (a.latitude != null && a.longitude != null)) && (
                <div className="timeline-card-actions">
                  {a.latitude != null && a.longitude != null && (
                    <button
                      type="button"
                      className="btn ghost btn-sm"
                      title={t('alert.mapTitle')}
                      onClick={() => navigate(`/map3d?lat=${a.latitude}&lon=${a.longitude}`)}
                    >
                      <IconNavigation size={14} />
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
                      <IconTrash size={14} />
                      {busyId === a.id ? t('common.deleting') : t('common.delete')}
                    </button>
                  )}
                </div>
              )}
            </div>
          </div>
        ))}
      </div>
    </>
  )
}
