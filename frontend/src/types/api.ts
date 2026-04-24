export type AlertSeverity = 'LOW' | 'MEDIUM' | 'HIGH' | 'CRITICAL'
export type AlertType =
  | 'INTRUSION'
  | 'OBJECT_ON_TRACK'
  | 'ESCALATION'
  | 'ANOMALY'
  | 'MANUAL'
export type CameraEventType =
  | 'HUMAN_DETECTED'
  | 'OBJECT_DETECTED'
  | 'INTRUSION'
  | 'ANOMALY'
export type ZoneType = 'RESTRICTED' | 'TRACK' | 'STATION' | 'NORMAL'

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
