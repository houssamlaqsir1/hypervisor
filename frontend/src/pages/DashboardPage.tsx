import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import { listAlerts, getAlertStats } from '../api/alerts'
import type { AlertStats } from '../types/api'
import { AlertRow } from '../components/AlertRow'
import { useLiveAlertsContext } from '../context/LiveAlertsContext'
import { useAuth } from '../context/AuthContext'
import { useT } from '../lib/useT'

const STAT_CARDS = [
  {
    key: 'total'    as const,
    labelKey: 'dashboard.totalAlerts',
    icon: '🔔',
    iconBg: 'rgba(37,99,235,0.25)',
    iconColor: '#3b82f6',
    accent: '#3b82f6',
  },
  {
    key: 'CRITICAL' as const,
    labelKey: 'severity.CRITICAL',
    icon: '🔴',
    iconBg: 'rgba(185,28,28,0.25)',
    iconColor: '#dc2626',
    accent: '#dc2626',
  },
  {
    key: 'HIGH'     as const,
    labelKey: 'severity.HIGH',
    icon: '🔥',
    iconBg: 'rgba(239,68,68,0.2)',
    iconColor: '#ef4444',
    accent: '#ef4444',
  },
  {
    key: 'MEDIUM'   as const,
    labelKey: 'severity.MEDIUM',
    icon: '⚠️',
    iconBg: 'rgba(245,158,11,0.2)',
    iconColor: '#f59e0b',
    accent: '#f59e0b',
  },
  {
    key: 'LOW'      as const,
    labelKey: 'severity.LOW',
    icon: 'ℹ️',
    iconBg: 'rgba(99,102,241,0.2)',
    iconColor: '#6366f1',
    accent: '#6366f1',
  },
]

export function DashboardPage() {
  const t = useT()
  const { user } = useAuth()
  const [stats, setStats] = useState<AlertStats | null>(null)
  const [loading, setLoading] = useState(true)
  const { alerts, seedAlerts } = useLiveAlertsContext()
  const didInitialLoad = useRef(false)

  const refreshStats = useCallback(() => {
    getAlertStats()
      .then(setStats)
      .catch((e) => console.error('Dashboard stats refresh failed', e))
  }, [])

  useEffect(() => {
    let active = true
    Promise.all([listAlerts({ limit: 50 }), getAlertStats()])
      .then(([list, s]) => {
        if (!active) return
        seedAlerts(list)
        setStats(s)
      })
      .catch((e) => {
        console.error('Dashboard load failed', e)
        if (active) setStats(null)
      })
      .finally(() => { if (active) setLoading(false) })
    return () => { active = false }
  }, [seedAlerts])

  /**
   * The stat cards must reflect the backend's real counts, not the local
   * alert list — that list is capped (only the most recent ~50-500, reset
   * on every reload), so deriving "total"/severity counts from it made the
   * dashboard snap back down to ~50 any time the true count was higher.
   * Instead, re-pull the real totals whenever a new alert streams in live.
   */
  useEffect(() => {
    if (loading) return
    if (!didInitialLoad.current) {
      didInitialLoad.current = true
      return
    }
    refreshStats()
  }, [alerts.length, loading, refreshStats])

  function getValue(key: typeof STAT_CARDS[number]['key']) {
    if (key === 'total') return stats?.total ?? 0
    return stats?.bySeverity?.[key] ?? 0
  }

  /**
   * Resolved alerts stay in the database and in History — but a resolved
   * alert is, by definition, handled, so it has no reason to keep taking up
   * space on the live operator feed. Filtered here only (Dashboard's own
   * view), not in the shared context, so the 3D map's alert pins and other
   * consumers of the live alert list are unaffected.
   */
  const visibleAlerts = useMemo(
    () => alerts.filter((a) => a.status !== 'RESOLVED'),
    [alerts],
  )

  return (
    <>
      <div className="page-header">
        <div>
          {/*
            The heading names the actor, not the page: an administrator was
            previously greeted by "Operator Dashboard" while the sidebar
            badge beside it read "Administrator". The viewer's variant also
            says the view is read-only, which is a real difference — the
            acknowledge and resolve controls are not rendered for that role.
          */}
          <h2>{t(`dashboard.title.${user?.role ?? 'OPERATOR'}`)}</h2>
          <p>{t(`dashboard.subtitle.${user?.role ?? 'OPERATOR'}`)}</p>
        </div>
      </div>

      <div className="dash-stat-row">
        {STAT_CARDS.map((c) => (
          <div
            key={c.key}
            className="dash-stat-card"
            style={{ '--card-accent': c.accent } as React.CSSProperties}
          >
            <div className="dash-stat-top" />
            <div className="dash-stat-body">
              <div className="dash-stat-icon" style={{ background: c.iconBg }}>
                <span>{c.icon}</span>
              </div>
              <div className="dash-stat-info">
                <span className="dash-stat-value">{getValue(c.key)}</span>
                <span className="dash-stat-label">{t(c.labelKey)}</span>
              </div>
            </div>
          </div>
        ))}
      </div>

      <h3>{t('dashboard.latest')}</h3>

      {loading && <p className="muted">{t('common.loading')}</p>}
      {!loading && alerts.length === 0 && (
        <p className="muted">{t('dashboard.empty')}</p>
      )}
      {!loading && alerts.length > 0 && visibleAlerts.length === 0 && (
        <p className="muted">{t('dashboard.allResolved')}</p>
      )}
      <div className="alert-list">
        {visibleAlerts.slice(0, 30).map((a) => (
          <AlertRow key={a.id} alert={a} />
        ))}
      </div>
    </>
  )
}
