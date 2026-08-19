import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { BrowserRouter } from 'react-router-dom'
import './index.css'
import App from './App'
import { AuthProvider } from './context/AuthContext'
import { ErrorBoundary } from './components/ErrorBoundary'
import { applyTheme, loadPrefs } from './lib/prefs'
import { initLocationFromPrefs } from './lib/operatorLocation'
import { initLanguage } from './lib/i18n'
import 'cesium/Build/Cesium/Widgets/widgets.css'

;(window as Window & { CESIUM_BASE_URL?: string }).CESIUM_BASE_URL = '/cesium'

// Apply saved theme and language before first render, to avoid a flash of
// the wrong theme or a frame of untranslated interface.
applyTheme(loadPrefs().theme)
initLanguage()

// Resume location tracking if the operator already opted in — the browser
// won't re-prompt, so this just restarts the watch on a fresh page load.
initLocationFromPrefs()

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <ErrorBoundary>
      <BrowserRouter>
        <AuthProvider>
          <App />
        </AuthProvider>
      </BrowserRouter>
    </ErrorBoundary>
  </StrictMode>,
)
