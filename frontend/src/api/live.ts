import { api } from './client'
import type {
  CameraEventDto,
  CameraEventType,
  IngestionResult,
  SigEventDto,
} from '../types/api'

/** Mirror of {@code LiveStatusService.Snapshot} on the backend. */
export interface LiveStatusSnapshot {
  lastOpenSkyPollAt: string | null
  lastOpenSkyError: string | null
  openSkyEventsTotal: number
  lastOpenSkyEventAt: string | null
  webcamEventsTotal: number
  lastWebcamEventAt: string | null
  gpsEventsTotal: number
  lastGpsEventAt: string | null
  ipCameraEventsTotal: number
  lastIpCameraEventAt: string | null
  lastIpCameraSource: string | null
}

export interface WebcamEventInput {
  cameraId: string
  eventType: CameraEventType
  label: string
  confidence: number
  latitude: number
  longitude: number
  elevationM?: number | null
  occurredAt?: string
  rawPayload?: Record<string, unknown>
}

export interface GpsEventInput {
  sourceId: string
  latitude: number
  longitude: number
  elevationM?: number | null
  trackLevel?: 'GROUND' | 'BRIDGE' | 'TUNNEL' | null
  zoneId?: number | null
  metadata?: Record<string, unknown>
  occurredAt?: string
}

export function getLiveStatus(): Promise<LiveStatusSnapshot> {
  return api.get('/live/status')
}

export function pushWebcamEvent(
  input: WebcamEventInput,
): Promise<IngestionResult<CameraEventDto>> {
  return api.post('/live/webcam', {
    occurredAt: new Date().toISOString(),
    elevationM: 0,
    rawPayload: {},
    ...input,
  })
}

export function pushGpsEvent(
  input: GpsEventInput,
): Promise<IngestionResult<SigEventDto>> {
  return api.post('/live/gps', {
    occurredAt: new Date().toISOString(),
    elevationM: 0,
    trackLevel: 'GROUND',
    metadata: {},
    ...input,
  })
}
