import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { BrowserRouter } from 'react-router-dom'
import './index.css'
import App from './App'
import { LiveAlertsProvider } from './context/LiveAlertsContext'
import { LiveCamerasProvider } from './context/LiveCamerasContext'
import { ErrorBoundary } from './components/ErrorBoundary'
import 'cesium/Build/Cesium/Widgets/widgets.css'

;(window as Window & { CESIUM_BASE_URL?: string }).CESIUM_BASE_URL = '/cesium'

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <ErrorBoundary>
      <BrowserRouter>
        <LiveAlertsProvider>
          <LiveCamerasProvider>
            <App />
          </LiveCamerasProvider>
        </LiveAlertsProvider>
      </BrowserRouter>
    </ErrorBoundary>
  </StrictMode>,
)
