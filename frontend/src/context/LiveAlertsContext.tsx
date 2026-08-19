import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useState,
  type ReactNode,
} from 'react'
import { Client } from '@stomp/stompjs'
import SockJS from 'sockjs-client'
import type { Alert } from '../types/api'
import { WS_URL } from '../api/client'
import { markSeenSilently } from '../lib/alertSeenLog'

export type ConnectionState = 'connecting' | 'open' | 'closed'

type LiveAlertsContextValue = {
  alerts: Alert[]
  /** Merge server-fetched alerts into the live list (deduped by id). */
  seedAlerts: (next: Alert[]) => void
  /** Replace one alert in the live list by id (e.g. after acknowledge/resolve). */
  updateAlert: (next: Alert) => void
  /**
   * Drop alerts from the live list after they've been deleted server-side.
   * Without this the Dashboard would keep showing an alert that no longer
   * exists until the next page load, since deletions aren't broadcast over
   * the alert topic the way new ones are.
   */
  removeAlerts: (ids: number[] | 'all') => void
  connectionState: ConnectionState
}

const LiveAlertsContext = createContext<LiveAlertsContextValue | null>(null)

/**
 * Single STOMP/SockJS connection for the whole app. Previously every
 * {@code useLiveAlerts} call opened its own socket (App + pages), which is
 * wasteful and can confuse the broker during dev HMR.
 */
export function LiveAlertsProvider({ children }: { children: ReactNode }) {
  const [alerts, setAlerts] = useState<Alert[]>([])
  const [connectionState, setConnectionState] =
    useState<ConnectionState>('connecting')

  const seedAlerts = useCallback((next: Alert[]) => {
    // These came from a history fetch, not from the live topic — they may be
    // hours old and the operator has already seen them. Record them as
    // accounted for *before* they enter the list, or the notification layer
    // (which only sees the merged array) would announce a page load as if
    // seven alerts had just fired.
    markSeenSilently(next.map((a) => a.id))
    setAlerts((prev) => {
      const byId = new Map<number, Alert>()
      for (const a of prev) byId.set(a.id, a)
      for (const a of next) byId.set(a.id, a)
      return Array.from(byId.values()).sort(
        (a, b) =>
          new Date(b.createdAt).getTime() - new Date(a.createdAt).getTime(),
      )
    })
  }, [])

  const updateAlert = useCallback((next: Alert) => {
    setAlerts((prev) =>
      prev.map((a) => (a.id === next.id ? next : a)),
    )
  }, [])

  const removeAlerts = useCallback((ids: number[] | 'all') => {
    if (ids === 'all') {
      setAlerts([])
      return
    }
    const doomed = new Set(ids)
    setAlerts((prev) => prev.filter((a) => !doomed.has(a.id)))
  }, [])

  useEffect(() => {
    const client = new Client({
      webSocketFactory: () => new SockJS(WS_URL) as unknown as WebSocket,
      reconnectDelay: 5000,
      heartbeatIncoming: 10000,
      heartbeatOutgoing: 10000,
      onConnect: () => {
        setConnectionState('open')
        client.subscribe('/topic/alerts', (msg) => {
          try {
            const alert = JSON.parse(msg.body) as Alert
            setAlerts((prev) => {
              // Re-broadcast of an existing alert (status change) → replace
              // it in place; a genuinely new alert → prepend.
              if (prev.some((a) => a.id === alert.id)) {
                return prev.map((a) => (a.id === alert.id ? alert : a))
              }
              return [alert, ...prev].slice(0, 500)
            })
          } catch (e) {
            console.warn('bad alert payload', e)
          }
        })
      },
      onWebSocketClose: () => setConnectionState('closed'),
      onStompError: () => setConnectionState('closed'),
    })
    client.activate()
    return () => {
      void client.deactivate()
    }
  }, [])

  const value = useMemo(
    () => ({ alerts, seedAlerts, updateAlert, removeAlerts, connectionState }),
    [alerts, seedAlerts, updateAlert, removeAlerts, connectionState],
  )

  return (
    <LiveAlertsContext.Provider value={value}>
      {children}
    </LiveAlertsContext.Provider>
  )
}

export function useLiveAlertsContext(): LiveAlertsContextValue {
  const v = useContext(LiveAlertsContext)
  if (!v) {
    throw new Error('useLiveAlertsContext must be used within LiveAlertsProvider')
  }
  return v
}
