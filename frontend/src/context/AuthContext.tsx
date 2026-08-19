import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useState,
  type ReactNode,
} from 'react'
import { login as apiLogin, getMe } from '../api/auth'
import {
  getToken,
  setToken,
  setUnauthorizedHandler,
} from '../api/token'
import type { AuthUser, Role } from '../types/api'

const USER_KEY = 'hypervisor_user'

/** Access levels a role grants, from least to most privileged. */
const ROLE_RANK: Record<Role, number> = {
  VIEWER: 1,
  OPERATOR: 2,
  ADMIN: 3,
}

type AuthContextValue = {
  user: AuthUser | null
  /** True once we've finished checking any stored token on startup. */
  ready: boolean
  login: (username: string, password: string) => Promise<void>
  logout: () => void
  /** True if the current user's role is at least {@code min}. */
  hasRole: (min: Role) => boolean
}

const AuthContext = createContext<AuthContextValue | null>(null)

function loadStoredUser(): AuthUser | null {
  try {
    const raw = localStorage.getItem(USER_KEY)
    return raw ? (JSON.parse(raw) as AuthUser) : null
  } catch {
    return null
  }
}

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<AuthUser | null>(loadStoredUser)
  const [ready, setReady] = useState(false)

  const logout = useCallback(() => {
    setToken(null)
    try {
      localStorage.removeItem(USER_KEY)
    } catch {
      /* ignore */
    }
    setUser(null)
  }, [])

  // Wire the API client's 401 handler to force a logout on token expiry.
  useEffect(() => {
    setUnauthorizedHandler(logout)
    return () => setUnauthorizedHandler(null)
  }, [logout])

  // On startup, if we have a stored token, verify it's still valid.
  useEffect(() => {
    const token = getToken()
    if (!token) {
      setReady(true)
      return
    }
    let active = true
    getMe()
      .then((u) => {
        if (!active) return
        setUser(u)
        localStorage.setItem(USER_KEY, JSON.stringify(u))
      })
      .catch(() => {
        if (active) logout()
      })
      .finally(() => {
        if (active) setReady(true)
      })
    return () => {
      active = false
    }
  }, [logout])

  const login = useCallback(async (username: string, password: string) => {
    const res = await apiLogin(username, password)
    setToken(res.token)
    localStorage.setItem(USER_KEY, JSON.stringify(res.user))
    setUser(res.user)
  }, [])

  const hasRole = useCallback(
    (min: Role) => (user ? ROLE_RANK[user.role] >= ROLE_RANK[min] : false),
    [user],
  )

  const value = useMemo(
    () => ({ user, ready, login, logout, hasRole }),
    [user, ready, login, logout, hasRole],
  )

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}

export function useAuth(): AuthContextValue {
  const v = useContext(AuthContext)
  if (!v) throw new Error('useAuth must be used within AuthProvider')
  return v
}
