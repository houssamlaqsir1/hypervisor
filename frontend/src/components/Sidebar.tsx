import { NavLink } from 'react-router-dom'
import type { ConnectionState } from '../context/LiveAlertsContext'

interface Props {
  wsState: ConnectionState
}

const NAV = [
  { to: '/', label: 'Dashboard', end: true },
  { to: '/map', label: 'Map view' },
  { to: '/map3d', label: '3D map' },
  { to: '/simulate', label: 'Simulator' },
  { to: '/history', label: 'History' },
]

export function Sidebar({ wsState }: Props) {
  return (
    <aside className="sidebar">
      <h1>
        <span>■</span> Hypervisor
      </h1>
      <nav>
        {NAV.map((item) => (
          <NavLink key={item.to} to={item.to} end={item.end}>
            {item.label}
          </NavLink>
        ))}
      </nav>
      <div className={`status ${wsState}`}>
        <span className="dot" />
        Live feed: {wsState === 'open' ? 'connected' : wsState}
      </div>
    </aside>
  )
}
