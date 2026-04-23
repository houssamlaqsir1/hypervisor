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

export type ConnectionState = 'connecting' | 'open' | 'closed'

type LiveAlertsContextValue = {
  alerts: Alert[]
  /** Merge server-fetched alerts into the live list (deduped by id). */
  seedAlerts: (next: Alert[]) => void
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
              if (prev.some((a) => a.id === alert.id)) return prev
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
    () => ({ alerts, seedAlerts, connectionState }),
    [alerts, seedAlerts, connectionState],
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
