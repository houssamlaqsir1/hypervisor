import { Component, type ErrorInfo, type ReactNode } from 'react'

type Props = { children: ReactNode }
type State = { error: Error | null }

/**
 * Surfaces runtime errors instead of a silent dark screen (common when a
 * dependency throws during module init or first render).
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
    if (this.state.error) {
      return (
        <div
          style={{
            minHeight: '100vh',
            padding: 24,
            background: '#0b1220',
            color: '#e5edff',
            fontFamily: 'system-ui, sans-serif',
          }}
        >
          <h1 style={{ color: '#f87171' }}>Something went wrong</h1>
          <pre
            style={{
              marginTop: 16,
              padding: 16,
              background: '#121a2e',
              borderRadius: 8,
              overflow: 'auto',
              fontSize: 13,
            }}
          >
            {this.state.error.stack ?? this.state.error.message}
          </pre>
          <p style={{ color: '#9aa7c2', marginTop: 16 }}>
            Open the browser devtools console (F12) for the full trace. After
            fixing the code, reload the page.
          </p>
        </div>
      )
    }
    return this.props.children
  }
}
