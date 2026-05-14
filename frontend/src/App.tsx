import { Suspense, lazy } from 'react'
import { Route, Routes } from 'react-router-dom'
import { Sidebar } from './components/Sidebar'
import { useLiveAlertsContext } from './context/LiveAlertsContext'

const DashboardPage = lazy(() =>
  import('./pages/DashboardPage').then((m) => ({ default: m.DashboardPage })),
)
const LiveWatchPage = lazy(() =>
  import('./pages/LiveWatchPage').then((m) => ({ default: m.LiveWatchPage })),
)
const MapPage = lazy(() =>
  import('./pages/MapPage').then((m) => ({ default: m.MapPage })),
)
const Map3DPage = lazy(() =>
  import('./pages/Map3DPage').then((m) => ({ default: m.Map3DPage })),
)
const OperationsPage = lazy(() =>
  import('./pages/OperationsPage').then((m) => ({ default: m.OperationsPage })),
)
const HistoryPage = lazy(() =>
  import('./pages/HistoryPage').then((m) => ({ default: m.HistoryPage })),
)

function App() {
  const { connectionState } = useLiveAlertsContext()

  return (
    <div className="app">
      <Sidebar wsState={connectionState} />
      <main className="main">
        <Suspense fallback={<p className="muted">Loading page…</p>}>
          <Routes>
            <Route path="/" element={<DashboardPage />} />
            <Route path="/live" element={<LiveWatchPage />} />
            <Route path="/map" element={<MapPage />} />
            <Route path="/map3d" element={<Map3DPage />} />
            <Route path="/operations" element={<OperationsPage />} />
            <Route path="/history" element={<HistoryPage />} />
          </Routes>
        </Suspense>
      </main>
    </div>
  )
}

export default App
