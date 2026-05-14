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
 * SockJS endpoint for STOMP. On {@code localhost} dev we hit Spring directly on
 * :8080. On any other host (Cloudflare tunnel, LAN IP) only Vite is reachable,
 * so we use same-origin {@code /ws} and let Vite proxy to Spring.
 */
function resolveWsUrl(): string {
  const fromEnv = import.meta.env.VITE_WS_URL as string | undefined
  if (fromEnv) return fromEnv
  if (import.meta.env.DEV && typeof window !== 'undefined') {
    const { hostname, origin } = window.location
    const isLocalhost = hostname === 'localhost' || hostname === '127.0.0.1'
    if (!isLocalhost) {
      return `${origin}/ws`
    }
    return `http://${hostname}:8080/ws`
  }
  return '/ws'
}

export const WS_URL = resolveWsUrl()
