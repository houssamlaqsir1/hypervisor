import { NavLink } from 'react-router-dom'
import type { ConnectionState } from '../context/LiveAlertsContext'
import { useLiveCameras } from '../context/LiveCamerasContext'

interface Props {
  wsState: ConnectionState
}

const NAV = [
  { to: '/', label: 'Dashboard', icon: '⊞', end: true },
  { to: '/live', label: 'Live Watch', icon: '◉' },
  { to: '/map', label: 'Map View', icon: '⊕' },
  { to: '/map3d', label: '3D Map', icon: '◈' },
  { to: '/operations', label: 'Operations', icon: '⊛' },
  { to: '/history', label: 'History', icon: '◷' },
  { to: '/settings', label: 'Settings', icon: '⚙' },
]

export function Sidebar({ wsState }: Props) {
  const { cameras } = useLiveCameras()
  const liveCount = cameras.filter((c) => c.enabled && c.status === 'running').length
  const enabledCount = cameras.filter((c) => c.enabled).length
  const camsLive = liveCount === enabledCount && enabledCount > 0

  return (
    <aside className="sidebar">
      {/* decorative animated bubbles */}
      <div className="sidebar-bubble sidebar-bubble--1" />
      <div className="sidebar-bubble sidebar-bubble--2" />
      <div className="sidebar-bubble sidebar-bubble--3" />
      <div className="sidebar-bubble sidebar-bubble--4" />

      <div className="sidebar-inner">
        <div className="sidebar-brand">
          <div className="sidebar-brand-icon">📡</div>
          <div className="sidebar-brand-text">
            <h1>Hypervisor</h1>
            <p>ONCF Security Platform</p>
          </div>
        </div>

        <p className="sidebar-section-label">Navigation</p>

        <nav>
          {NAV.map((item) => (
            <NavLink key={item.to} to={item.to} end={item.end}>
              <span className="nav-icon">{item.icon}</span>
              {item.label}
            </NavLink>
          ))}
        </nav>

        <div className="status-section">
          <div className={`status ${camsLive ? 'open' : 'connecting'}`}>
            <span className="dot" />
            AI cameras: {liveCount}/{cameras.length} live
          </div>
          <div className={`status ${wsState}`}>
            <span className="dot" />
            Live feed: {wsState === 'open' ? 'connected' : wsState}
          </div>
        </div>
      </div>
    </aside>
  )
}
