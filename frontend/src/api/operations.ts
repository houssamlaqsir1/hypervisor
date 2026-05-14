import { api } from './client'
import type {
  Alert,
  CameraEventDto,
  IngestionResult,
  SigEventDto,
} from '../types/api'

export interface OperationsRequest {
  zoneId?: number | null
  latitude?: number | null
  longitude?: number | null
}

export function receiveCameraEvent(
  req: OperationsRequest = {},
): Promise<IngestionResult<CameraEventDto>> {
  return api.post('/operations/camera', req)
}

export function receiveSigEvent(
  req: OperationsRequest = {},
): Promise<IngestionResult<SigEventDto>> {
  return api.post('/operations/sig', req)
}

export function runIntrusionOperation(zoneId: number): Promise<Alert[]> {
  return api.post('/operations/scenario/intrusion', { zoneId })
}
