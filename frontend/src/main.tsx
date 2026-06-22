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

// Apply saved theme before first render to avoid flash
;(() => {
  try {
    const raw = localStorage.getItem('hypervisor_settings')
    const theme = raw ? JSON.parse(raw).theme : undefined
    const prefersDark = window.matchMedia('(prefers-color-scheme: dark)').matches
    const dark = theme === 'dark' || (!theme && prefersDark) || (theme === 'system' && prefersDark)
    if (dark) document.documentElement.classList.add('dark-mode')
  } catch { /* ignore */ }
})()

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
