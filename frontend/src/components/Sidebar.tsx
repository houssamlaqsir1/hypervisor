import { NavLink } from 'react-router-dom'
import type { ComponentType } from 'react'
import type { ConnectionState } from '../context/LiveAlertsContext'
import { useLiveCameras } from '../context/LiveCamerasContext'
import { useAuth } from '../context/AuthContext'
import type { Role } from '../types/api'
import { useT } from '../lib/useT'
import {
  IconAnalytics,
  IconBrand,
  IconCamera,
  IconDashboard,
  IconGlobe,
  IconHistory,
  IconLive,
  IconLogOut,
  IconSettings,
  IconUsers,
  IconZones,
} from './icons'

interface Props {
  wsState: ConnectionState
}

/**
 * Each nav item optionally declares the minimum role required to see it.
 * Labels are translation keys rather than text, so the sidebar follows the
 * language switch like everything else.
 */
const NAV: {
  to: string
  labelKey: string
  Icon: ComponentType<{ size?: number }>
  end?: boolean
  min?: Role
}[] = [
  { to: '/', labelKey: 'nav.dashboard', Icon: IconDashboard, end: true },
  { to: '/live', labelKey: 'nav.live', Icon: IconLive },
  { to: '/map3d', labelKey: 'nav.map3d', Icon: IconGlobe },
  { to: '/history', labelKey: 'nav.history', Icon: IconHistory },
  { to: '/analytics', labelKey: 'nav.analytics', Icon: IconAnalytics },
  { to: '/settings', labelKey: 'nav.settings', Icon: IconSettings },
  { to: '/admin/users', labelKey: 'nav.users', Icon: IconUsers, min: 'ADMIN' },
  { to: '/admin/cameras', labelKey: 'nav.cameras', Icon: IconCamera, min: 'ADMIN' },
  { to: '/admin/zones', labelKey: 'nav.zones', Icon: IconZones, min: 'ADMIN' },
]

/**
 * The three seeded demo accounts carry a job title in their `fullName`
 * rather than a person's name, so they should follow the interface
 * language. Matched here against both spellings, since the accounts were
 * originally seeded in French and existing databases still hold those rows.
 *
 * Anything else is a real user's name and is shown exactly as entered — a
 * name is not a translatable string.
 */
const SEEDED_JOB_TITLES: Record<string, string> = {
  'administrateur technique': 'actor.ADMIN',
  'opérateur de supervision': 'actor.OPERATOR',
  'responsable sécurité': 'actor.VIEWER',
  'technical administrator': 'actor.ADMIN',
  'supervision operator': 'actor.OPERATOR',
  'security manager': 'actor.VIEWER',
}

function displayName(
  fullName: string | null | undefined,
  username: string,
  t: (key: string) => string,
): string {
  const key = SEEDED_JOB_TITLES[(fullName ?? '').trim().toLowerCase()]
  return key ? t(key) : (fullName || username)
}

export function Sidebar({ wsState }: Props) {
  const t = useT()
  const { cameras } = useLiveCameras()
  const { user, hasRole, logout } = useAuth()
  const liveCount = cameras.filter((c) => c.enabled && c.status === 'running').length
  const enabledCount = cameras.filter((c) => c.enabled).length
  const camsLive = liveCount === enabledCount && enabledCount > 0
  const nav = NAV.filter((item) => !item.min || hasRole(item.min))

  return (
    <aside className="sidebar">
      <div className="sidebar-inner">
        <div className="sidebar-brand">
          <div className="sidebar-brand-mark">
            <IconBrand size={20} />
          </div>
          <div className="sidebar-brand-text">
            <h1>Hypervisor</h1>
            <p>{t('brand.subtitle')}</p>
          </div>
        </div>

        <p className="sidebar-section-label">{t('nav.section')}</p>

        <nav>
          {nav.map((item) => (
            /* The label is hidden by CSS on a narrow viewport, where the
               rail collapses to icons — so each link carries its name as a
               title too, or the collapsed rail becomes nine mystery glyphs. */
            <NavLink
              key={item.to}
              to={item.to}
              end={item.end}
              title={t(item.labelKey)}
            >
              <span className="nav-icon">
                <item.Icon size={18} />
              </span>
              <span className="nav-label">{t(item.labelKey)}</span>
            </NavLink>
          ))}
        </nav>

        <div className="status-section">
          <div className={`status ${camsLive ? 'open' : 'connecting'}`}>
            <span className="dot" />
            {t('nav.camerasLive', { live: liveCount, total: cameras.length })}
          </div>
          <div className={`status ${wsState}`}>
            <span className="dot" />
            {t('nav.feed', {
              state:
                wsState === 'open'
                  ? t('nav.feed.connected')
                  : wsState === 'connecting'
                    ? t('nav.feed.connecting')
                    : t('nav.feed.closed'),
            })}
          </div>
        </div>

        {user && (
          <div className="sidebar-user">
            <div className="sidebar-user-info">
              <span className="sidebar-user-name">
                {displayName(user.fullName, user.username, t)}
              </span>
              <span className={`sidebar-user-role role-${user.role}`}>
                {t(`role.${user.role}`)}
              </span>
            </div>
            <button
              type="button"
              className="sidebar-signout"
              onClick={logout}
              title={t('nav.signOut')}
              aria-label={t('nav.signOut')}
            >
              <IconLogOut size={16} />
            </button>
          </div>
        )}
      </div>
    </aside>
  )
}
