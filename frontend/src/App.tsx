import { Suspense, lazy, type ReactNode } from 'react'
import { Navigate, Route, Routes } from 'react-router-dom'
import { Sidebar } from './components/Sidebar'
import {
  LiveAlertsProvider,
  useLiveAlertsContext,
} from './context/LiveAlertsContext'
import { LiveCamerasProvider } from './context/LiveCamerasContext'
import { NotificationsProvider } from './context/NotificationsProvider'
import { useAuth } from './context/AuthContext'
import { LoginPage } from './pages/LoginPage'
import type { Role } from './types/api'

const DashboardPage = lazy(() =>
  import('./pages/DashboardPage').then((m) => ({ default: m.DashboardPage })),
)
const LiveWatchPage = lazy(() =>
  import('./pages/LiveWatchPage').then((m) => ({ default: m.LiveWatchPage })),
)
const Map3DPage = lazy(() =>
  import('./pages/Map3DPage').then((m) => ({ default: m.Map3DPage })),
)
const HistoryPage = lazy(() =>
  import('./pages/HistoryPage').then((m) => ({ default: m.HistoryPage })),
)
const AnalyticsPage = lazy(() =>
  import('./pages/AnalyticsPage').then((m) => ({ default: m.AnalyticsPage })),
)
const SettingsPage = lazy(() =>
  import('./pages/SettingsPage').then((m) => ({ default: m.SettingsPage })),
)
const AdminUsersPage = lazy(() =>
  import('./pages/AdminUsersPage').then((m) => ({ default: m.AdminUsersPage })),
)
const AdminCamerasPage = lazy(() =>
  import('./pages/AdminCamerasPage').then((m) => ({ default: m.AdminCamerasPage })),
)
const AdminZonesPage = lazy(() =>
  import('./pages/AdminZonesPage').then((m) => ({ default: m.AdminZonesPage })),
)

/** Renders children only if the user's role is high enough, else redirects home. */
function RequireRole({ min, children }: { min: Role; children: ReactNode }) {
  const { hasRole } = useAuth()
  return hasRole(min) ? <>{children}</> : <Navigate to="/" replace />
}

function AuthedApp() {
  const { connectionState } = useLiveAlertsContext()

  return (
    <div className="app">
      <Sidebar wsState={connectionState} />
      <main className="main">
        <Suspense fallback={<p className="muted">Loading page…</p>}>
          <Routes>
            <Route path="/" element={<DashboardPage />} />
            <Route path="/live" element={<LiveWatchPage />} />
            <Route path="/map3d" element={<Map3DPage />} />
            <Route path="/history" element={<HistoryPage />} />
            <Route path="/analytics" element={<AnalyticsPage />} />
            <Route path="/settings" element={<SettingsPage />} />
            <Route
              path="/admin/users"
              element={
                <RequireRole min="ADMIN">
                  <AdminUsersPage />
                </RequireRole>
              }
            />
            <Route
              path="/admin/cameras"
              element={
                <RequireRole min="ADMIN">
                  <AdminCamerasPage />
                </RequireRole>
              }
            />
            <Route
              path="/admin/zones"
              element={
                <RequireRole min="ADMIN">
                  <AdminZonesPage />
                </RequireRole>
              }
            />
            <Route path="*" element={<Navigate to="/" replace />} />
          </Routes>
        </Suspense>
      </main>
    </div>
  )
}

function App() {
  const { user, ready } = useAuth()

  if (!ready) {
    return <p className="muted" style={{ padding: 24 }}>Loading…</p>
  }
  if (!user) {
    return (
      <Routes>
        <Route path="*" element={<LoginPage />} />
      </Routes>
    )
  }
  // Live feeds (WebSocket alerts + camera detection) only start once
  // authenticated, so the login screen never opens sockets or runs the AI loop.
  return (
    <LiveAlertsProvider>
      <LiveCamerasProvider>
        <NotificationsProvider>
          <AuthedApp />
        </NotificationsProvider>
      </LiveCamerasProvider>
    </LiveAlertsProvider>
  )
}

export default App
