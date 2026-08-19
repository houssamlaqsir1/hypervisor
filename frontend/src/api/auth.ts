import { api } from './client'
import type { AuthUser, LoginResponse } from '../types/api'

export function login(
  username: string,
  password: string,
): Promise<LoginResponse> {
  return api.post<LoginResponse>('/auth/login', { username, password })
}

export function getMe(): Promise<AuthUser> {
  return api.get<AuthUser>('/auth/me')
}
