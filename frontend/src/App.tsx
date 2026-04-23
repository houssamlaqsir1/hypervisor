import { Route, Routes } from 'react-router-dom'
import { Sidebar } from './components/Sidebar'
import { DashboardPage } from './pages/DashboardPage'
import { MapPage } from './pages/MapPage'
import { SimulatorPage } from './pages/SimulatorPage'
import { HistoryPage } from './pages/HistoryPage'
import { useLiveAlertsContext } from './context/LiveAlertsContext'

function App() {
  const { connectionState } = useLiveAlertsContext()

  return (
    <div className="app">
      <Sidebar wsState={connectionState} />
      <main className="main">
        <Routes>
          <Route path="/" element={<DashboardPage />} />
          <Route path="/map" element={<MapPage />} />
          <Route path="/simulate" element={<SimulatorPage />} />
          <Route path="/history" element={<HistoryPage />} />
        </Routes>
      </main>
    </div>
  )
}

export default App
