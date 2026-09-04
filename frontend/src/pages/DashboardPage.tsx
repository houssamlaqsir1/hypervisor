import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import { listAlerts, getAlertStats } from '../api/alerts'
import type { AlertStats } from '../types/api'
import { AlertRow } from '../components/AlertRow'
import { useLiveAlertsContext } from '../context/LiveAlertsContext'
import { useAuth } from '../context/AuthContext'
import { useT } from '../lib/useT'
import {
  IconAlertCircle,
  IconAlertOctagon,
  IconAlertTriangle,
  IconBell,
  IconCheckCircle,
  IconInfo,
} from '../components/icons'
import { AnimatedNumber } from '../components/AnimatedNumber'
import { AlertListSkeleton, StatRowSkeleton } from '../components/Skeleton'

/**
 * The five counters across the top of the console.
 *
 * Each one names a CSS variable rather than a literal colour, so the row
 * follows the theme: the severity reds that read on a white page are too
 * dark to see on the dark one, and were previously frozen into this file
 * as hex values that only suited the light theme.
 *
 * Shape carries the severity as well as colour — octagon, triangle,
 * circle, in that descending order of urgency — so the row still ranks
 * correctly for an operator who cannot separate the reds from the ambers.
 */
const STAT_CARDS = [
  { key: 'total'    as const, labelKey: 'dashboard.totalAlerts', Icon: IconBell,          accent: 'var(--accent)',   soft: 'var(--accent-soft)' },
  { key: 'CRITICAL' as const, labelKey: 'severity.CRITICAL',     Icon: IconAlertOctagon,  accent: 'var(--critical)', soft: 'var(--danger-soft)' },
  { key: 'HIGH'     as const, labelKey: 'severity.HIGH',         Icon: IconAlertTriangle, accent: 'var(--danger)',   soft: 'var(--danger-soft)' },
  { key: 'MEDIUM'   as const, labelKey: 'severity.MEDIUM',       Icon: IconAlertCircle,   accent: 'var(--warn)',     soft: 'var(--warn-soft)' },
  { key: 'LOW'      as const, labelKey: 'severity.LOW',          Icon: IconInfo,          accent: 'var(--neutral)',  soft: 'var(--neutral-soft)' },
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

      {loading ? (
        <StatRowSkeleton />
      ) : (
        <div className="dash-stat-row">
          {STAT_CARDS.map((c) => (
            <div
              key={c.key}
              className="dash-stat-card"
              style={
                {
                  '--card-accent': c.accent,
                  '--card-accent-soft': c.soft,
                } as React.CSSProperties
              }
            >
              <div className="dash-stat-icon">
                <c.Icon size={20} />
              </div>
              <div className="dash-stat-info">
                {/*
                  These figures move on their own as alerts stream in, so
                  they count rather than snap — a digit that simply swaps
                  is easy to miss on a screen nobody is staring at.
                */}
                <AnimatedNumber className="dash-stat-value" value={getValue(c.key)} />
                <span className="dash-stat-label">{t(c.labelKey)}</span>
              </div>
            </div>
          ))}
        </div>
      )}

      <h3 className="section-title">
        {t('dashboard.latest')}
        {!loading && visibleAlerts.length > 0 && (
          <span className="count">{visibleAlerts.length}</span>
        )}
      </h3>

      {loading && <AlertListSkeleton />}

      {/* Nothing outstanding is the state this console spends most of its
          time in, so it is drawn as a result rather than left as a bare
          grey sentence floating where the feed should be. */}
      {!loading && visibleAlerts.length === 0 && (
        <div className="empty-state animate-pop">
          <IconCheckCircle size={26} />
          <span>
            {alerts.length === 0 ? t('dashboard.empty') : t('dashboard.allResolved')}
          </span>
        </div>
      )}

      <div className="alert-list">
        {visibleAlerts.slice(0, 30).map((a) => (
          <AlertRow key={a.id} alert={a} />
        ))}
      </div>
    </>
  )
}
