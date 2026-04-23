import { api } from './client'
import type { Zone } from '../types/api'

export function listZones(): Promise<Zone[]> {
  return api.get<Zone[]>('/zones')
}
