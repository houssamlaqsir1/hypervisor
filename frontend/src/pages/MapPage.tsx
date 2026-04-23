import { useEffect, useState } from 'react'
import { MapContainer, TileLayer, Circle, Marker, Popup } from 'react-leaflet'
import L from 'leaflet'
import { listAlerts } from '../api/alerts'
import { listZones } from '../api/zones'
import type { Alert, Zone } from '../types/api'
import { useLiveAlertsContext } from '../context/LiveAlertsContext'

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
  const { alerts, seedAlerts } = useLiveAlertsContext()

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
      <div className="map-container">
        <MapContainer center={center} zoom={15} style={{ height: '100%', width: '100%' }}>
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
