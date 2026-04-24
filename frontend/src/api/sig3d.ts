import { api } from './client'
import type { Sig3dResponse } from '../types/api'

export function getSig3dScene(): Promise<Sig3dResponse> {
  return api.get<Sig3dResponse>('/sig/3d')
}
