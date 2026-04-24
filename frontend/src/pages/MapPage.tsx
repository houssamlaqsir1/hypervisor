import { useCallback, useEffect, useState } from 'react'
import { MapContainer, TileLayer, Circle, Marker, Popup, useMap } from 'react-leaflet'
import L from 'leaflet'
import { listAlerts } from '../api/alerts'
import { listZones } from '../api/zones'
import type { Alert, Zone } from '../types/api'
import { useLiveAlertsContext } from '../context/LiveAlertsContext'
import { MapSearchBar, type MapSearchPick } from '../components/MapSearchBar'

function MapReady({ onMap }: { onMap: (m: L.Map) => void }) {
  const map = useMap()
  useEffect(() => {
    onMap(map)
  }, [map, onMap])
  return null
}

const ZONE_COLORS: Record<Zone['type'], string> = {
  RESTRICTED: '#ef4444',
  TRACK: '#f59e0b',
  STATION: '#3b82f6',
  NORMAL: '#22c55e',
}

const SEV_COLORS: Record<Alert['severity'], string> = {
  LOW: '#6b7280',
  MEDIUM: '#f59e0b',
  HIGH: '#ef4444',
  CRITICAL: '#b91c1c',
}

function alertIcon(severity: Alert['severity']): L.DivIcon {
  return L.divIcon({
    className: 'alert-marker',
    html: `<div style="
      width:18px;height:18px;border-radius:50%;
      background:${SEV_COLORS[severity]};
      border:3px solid #fff;
      box-shadow:0 0 10px ${SEV_COLORS[severity]};"></div>`,
    iconSize: [18, 18],
    iconAnchor: [9, 9],
  })
}

export function MapPage() {
  const [zones, setZones] = useState<Zone[]>([])
  const [loading, setLoading] = useState(true)
  const [leafletMap, setLeafletMap] = useState<L.Map | null>(null)
  const [pendingPick, setPendingPick] = useState<MapSearchPick | null>(null)
  const { alerts, seedAlerts } = useLiveAlertsContext()

  const goToPlace = useCallback(
    (p: MapSearchPick) => {
      if (!leafletMap) return
      if (
        p.west != null &&
        p.south != null &&
        p.east != null &&
        p.north != null
      ) {
        leafletMap.fitBounds(
          [
            [p.south, p.west],
            [p.north, p.east],
          ],
          { padding: [28, 28], maxZoom: 16 },
        )
      } else {
        leafletMap.flyTo([p.lat, p.lon], 13)
      }
    },
    [leafletMap],
  )

  const handleMapPick = useCallback(
    (p: MapSearchPick) => {
      if (leafletMap) goToPlace(p)
      else setPendingPick(p)
    },
    [leafletMap, goToPlace],
  )

  useEffect(() => {
    if (!leafletMap || !pendingPick) return
    goToPlace(pendingPick)
    setPendingPick(null)
  }, [leafletMap, pendingPick, goToPlace])

  useEffect(() => {
    Promise.all([listZones(), listAlerts({ limit: 200 })])
      .then(([z, a]) => {
        setZones(z)
        seedAlerts(a)
      })
      .catch((e) => console.error('Map load failed', e))
      .finally(() => setLoading(false))
  }, [seedAlerts])

  const center: [number, number] = zones.length
    ? [zones[0].centerLat, zones[0].centerLon]
    : [33.5971, -7.5811]

  if (loading) return <p className="muted">Loading map…</p>

  return (
    <>
      <div className="page-header">
        <div>
          <h2>Map view</h2>
          <p>SIG zones and alert locations in real time.</p>
        </div>
      </div>
      <div className="card map-toolbar">
        <MapSearchBar
          onPick={handleMapPick}
          suggest
          mapReadyHint={
            leafletMap ? null : 'Map is still loading — your search or location will apply when it is ready.'
          }
        />
        <p className="muted map-toolbar-hint">
          Search uses OpenStreetMap Nominatim via your backend (not Google). “My location” uses the browser GPS.
        </p>
      </div>
      <div className="map-container">
        <MapContainer center={center} zoom={15} style={{ height: '100%', width: '100%' }}>
          <MapReady onMap={setLeafletMap} />
          <TileLayer
            attribution='&copy; OpenStreetMap contributors'
            url="https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png"
          />
          {zones.map((z) => (
            <Circle
              key={z.id}
              center={[z.centerLat, z.centerLon]}
              radius={z.radiusM}
              pathOptions={{
                color: ZONE_COLORS[z.type],
                fillColor: ZONE_COLORS[z.type],
                fillOpacity: 0.15,
                weight: 2,
              }}
            >
              <Popup>
                <b>{z.name}</b>
                <br />
                Type: {z.type}
                <br />
                {z.description}
              </Popup>
            </Circle>
          ))}

          {alerts
            .filter((a) => a.latitude != null && a.longitude != null)
            .map((a) => (
              <Marker
                key={a.id}
                position={[a.latitude!, a.longitude!]}
                icon={alertIcon(a.severity)}
              >
                <Popup>
                  <b>
                    [{a.severity}] {a.type}
                  </b>
                  <br />
                  {a.message}
                  <br />
                  <small>{new Date(a.createdAt).toLocaleString()}</small>
                </Popup>
              </Marker>
            ))}
        </MapContainer>
      </div>
    </>
  )
}
