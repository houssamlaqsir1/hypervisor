import { Component, type ErrorInfo, type ReactNode } from 'react'
import { t } from '../lib/i18n'
import { IconAlertTriangle, IconRefresh } from './icons'

type Props = { children: ReactNode }
type State = { error: Error | null }

/**
 * Surfaces runtime errors instead of a silent dark screen (common when a
 * dependency throws during module init or first render).
 *
 * What it shows is deliberately two-layered. The person in front of this
 * screen is an operator whose console has just stopped working, and what
 * they need first is the plain fact of it and a way back — not a stack
 * trace, which reads as the software having come apart. The trace is still
 * here, one click away, because whoever they call next will ask for it.
 *
 * Styled with the application's own classes rather than inline colours: an
 * error page frozen at some hard-coded navy is exactly the screen that
 * quietly stops matching the rest of the interface. If the stylesheet
 * itself had failed to load, nothing would have rendered this far anyway.
 */
export class ErrorBoundary extends Component<Props, State> {
  state: State = { error: null }

  static getDerivedStateFromError(error: Error): State {
    return { error }
  }

  componentDidCatch(error: Error, info: ErrorInfo) {
    console.error('UI error:', error, info.componentStack)
  }

  render() {
    const { error } = this.state
    if (!error) return this.props.children

    return (
      <div className="crash-page" role="alert">
        <div className="crash-card">
          <div className="crash-icon">
            <IconAlertTriangle size={22} />
          </div>
          <h1>{t('crash.title')}</h1>
          <p>{t('crash.body')}</p>

          <button
            type="button"
            className="btn"
            onClick={() => window.location.reload()}
          >
            <IconRefresh size={15} />
            {t('crash.reload')}
          </button>

          <details className="crash-details">
            <summary>{t('crash.details')}</summary>
            <pre>{error.stack ?? error.message}</pre>
          </details>
        </div>
      </div>
    )
  }
}
