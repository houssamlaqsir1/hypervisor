export type AlertSeverity = 'LOW' | 'MEDIUM' | 'HIGH' | 'CRITICAL'
export type AlertType =
  | 'INTRUSION'
  | 'OBJECT_ON_TRACK'
  | 'ESCALATION'
  | 'ANOMALY'
  | 'FUSION'
  | 'MANUAL'
export type CameraEventType =
  | 'HUMAN_DETECTED'
  | 'OBJECT_DETECTED'
  | 'INTRUSION'
  | 'ANOMALY'
export type ZoneType = 'RESTRICTED' | 'TRACK' | 'STATION' | 'NORMAL'

/**
 * Structured "why" stored on every alert. Empty / undefined for legacy
 * alerts; populated for FUSION alerts with the spatio-temporal score, the
 * metric distance / time delta, and the two event endpoints so the UI can
 * draw a real correlation receipt (and the 3D map can link the points).
 */
export interface AlertDetails {
  fusionScore?: number
  severity?: AlertSeverity
  trigger?: 'camera' | 'sig'
  zoneName?: string
  zoneType?: ZoneType
  zoneWeight?: number
  distanceM?: number
  timeDeltaSec?: number
  cameraConfidence?: number
  subScores?: {
    proximity?: number
    recency?: number
    confidence?: number
    zone?: number
  }
  camera?: {
    id?: string
    eventId?: number
    label?: string
    lat?: number
    lon?: number
    elevationM?: number
  }
  sig?: {
    sourceId?: string
    eventId?: number
    lat?: number
    lon?: number
    elevationM?: number
  }
  [key: string]: unknown
}

export interface Alert {
  id: number
  severity: AlertSeverity
  type: AlertType
  message: string
  latitude: number | null
  longitude: number | null
  zoneId: number | null
  zoneName: string | null
  cameraEventId: number | null
  sigEventId: number | null
  details: AlertDetails | null
  createdAt: string
  dispatched: boolean
  dispatchedAt: string | null
}

export interface Zone {
  id: number
  name: string
  type: ZoneType
  description: string | null
  centerLat: number
  centerLon: number
  radiusM: number
}

export interface CameraEventDto {
  id: number
  cameraId: string
  eventType: CameraEventType
  label: string | null
  confidence: number
  latitude: number
  longitude: number
  occurredAt: string
  receivedAt: string
}

export interface SigEventDto {
  id: number
  sourceId: string
  latitude: number
  longitude: number
  elevationM?: number | null
  trackLevel?: 'GROUND' | 'BRIDGE' | 'TUNNEL' | null
  zoneId: number | null
  zoneName: string | null
  occurredAt: string
  receivedAt: string
}

export interface AlertStats {
  total: number
  bySeverity: Record<AlertSeverity, number>
}

export interface IngestionResult<T> {
  event: T
  alerts: Alert[]
}

export type GeoJsonGeometry =
  | { type: 'LineString'; coordinates: number[][] }
  | { type: 'MultiLineString'; coordinates: number[][][] }
  | { type: 'Polygon'; coordinates: number[][][] }
  | { type: 'MultiPolygon'; coordinates: number[][][][] }

export interface GeoJsonFeature {
  type: 'Feature'
  properties: Record<string, unknown>
  geometry: GeoJsonGeometry
}

export interface GeoJsonFeatureCollection {
  type: 'FeatureCollection'
  features: GeoJsonFeature[]
}

export interface Sig3dResponse {
  terrain: Record<string, unknown>
  rail: GeoJsonFeatureCollection
  zones: GeoJsonFeatureCollection
  alerts: Alert[]
}
