const BASE = import.meta.env.VITE_API_BASE ?? '/api'

async function request<T>(
  method: string,
  path: string,
  body?: unknown,
): Promise<T> {
  const res = await fetch(`${BASE}${path}`, {
    method,
    headers: body ? { 'Content-Type': 'application/json' } : undefined,
    body: body !== undefined ? JSON.stringify(body) : undefined,
  })
  if (!res.ok) {
    const text = await res.text().catch(() => '')
    throw new Error(`${res.status} ${res.statusText}: ${text}`)
  }
  if (res.status === 204) return undefined as T
  return (await res.json()) as T
}

export const api = {
  get: <T,>(p: string) => request<T>('GET', p),
  post: <T,>(p: string, body?: unknown) => request<T>('POST', p, body),
  del: <T,>(p: string) => request<T>('DELETE', p),
}

/**
 * SockJS + STOMP does long-polling and rapid reconnects. Proxied through Vite's
 * `/ws` proxy that causes noisy `ECONNABORTED` in the terminal (not an app bug).
 * In dev we talk straight to Spring on port 8080; in prod use same-origin `/ws`
 * or set {@code VITE_WS_URL} at build time.
 */
function resolveWsUrl(): string {
  const fromEnv = import.meta.env.VITE_WS_URL as string | undefined
  if (fromEnv) return fromEnv
  if (import.meta.env.DEV && typeof window !== 'undefined') {
    const host = window.location.hostname
    return `http://${host}:8080/ws`
  }
  return '/ws'
}

export const WS_URL = resolveWsUrl()
