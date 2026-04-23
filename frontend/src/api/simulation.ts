import { api } from './client'
import type {
  Alert,
  CameraEventDto,
  IngestionResult,
  SigEventDto,
} from '../types/api'

export interface SimulationRequest {
  zoneId?: number | null
  latitude?: number | null
  longitude?: number | null
}

export function simulateCameraEvent(
  req: SimulationRequest = {},
): Promise<IngestionResult<CameraEventDto>> {
  return api.post('/simulation/camera', req)
}

export function simulateSigEvent(
  req: SimulationRequest = {},
): Promise<IngestionResult<SigEventDto>> {
  return api.post('/simulation/sig', req)
}

export function runIntrusionScenario(zoneId: number): Promise<Alert[]> {
  return api.post('/simulation/scenario/intrusion', { zoneId })
}
