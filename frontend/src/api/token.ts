/**
 * Small standalone token store so both the low-level API client and the
 * React auth context can read/write the JWT without a circular import.
 */
const TOKEN_KEY = 'hypervisor_token'

let onUnauthorized: (() => void) | null = null

export function getToken(): string | null {
  try {
    return localStorage.getItem(TOKEN_KEY)
  } catch {
    return null
  }
}

export function setToken(token: string | null) {
  try {
    if (token) localStorage.setItem(TOKEN_KEY, token)
    else localStorage.removeItem(TOKEN_KEY)
  } catch {
    /* private mode — ignore */
  }
}

/** Registered by the auth context so a 401 anywhere forces a logout. */
export function setUnauthorizedHandler(fn: (() => void) | null) {
  onUnauthorized = fn
}

export function notifyUnauthorized() {
  if (onUnauthorized) onUnauthorized()
}
