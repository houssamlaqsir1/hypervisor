import { api } from './client'
import type { AdminCamera, AdminUser, Role, Zone, ZoneType } from '../types/api'

export function listUsers(): Promise<AdminUser[]> {
  return api.get<AdminUser[]>('/admin/users')
}

export function createUser(input: {
  username: string
  fullName?: string
  password: string
  role: Role
}): Promise<AdminUser> {
  return api.post<AdminUser>('/admin/users', input)
}

export function setUserEnabled(id: number, enabled: boolean): Promise<AdminUser> {
  return api.patch<AdminUser>(`/admin/users/${id}/enabled`, { enabled })
}

export function updateUser(
  id: number,
  input: {
    username: string
    fullName?: string
    role: Role
    newPassword?: string
  },
): Promise<AdminUser> {
  return api.put<AdminUser>(`/admin/users/${id}`, input)
}

export function deleteUser(id: number): Promise<void> {
  return api.del<void>(`/admin/users/${id}`)
}

/* ─── cameras ─────────────────────────────────────────── */

export interface CameraInput {
  cameraId: string
  name: string
  site?: string
  latitude: number
  longitude: number
  elevationM?: number
  headingDeg?: number
  active?: boolean
}

export function listCameras(): Promise<AdminCamera[]> {
  return api.get<AdminCamera[]>('/admin/cameras')
}

export function createCamera(input: CameraInput): Promise<AdminCamera> {
  return api.post<AdminCamera>('/admin/cameras', input)
}

export function updateCamera(id: number, input: CameraInput): Promise<AdminCamera> {
  return api.put<AdminCamera>(`/admin/cameras/${id}`, input)
}

export function deleteCamera(id: number): Promise<void> {
  return api.del<void>(`/admin/cameras/${id}`)
}

/* ─── zones ───────────────────────────────────────────── */

export interface ZoneInput {
  name: string
  type: ZoneType
  description?: string
  centerLat: number
  centerLon: number
  radiusM: number
  elevationM?: number
  heightM?: number
  isTunnel?: boolean
  isBridge?: boolean
}

export function listZonesAdmin(): Promise<Zone[]> {
  return api.get<Zone[]>('/admin/zones')
}

export function createZone(input: ZoneInput): Promise<Zone> {
  return api.post<Zone>('/admin/zones', input)
}

export function updateZone(id: number, input: ZoneInput): Promise<Zone> {
  return api.put<Zone>(`/admin/zones/${id}`, input)
}

export function deleteZone(id: number): Promise<void> {
  return api.del<void>(`/admin/zones/${id}`)
}
