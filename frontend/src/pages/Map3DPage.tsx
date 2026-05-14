import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import {
  BillboardGraphics,
  Entity,
  LabelGraphics,
  PolygonGraphics,
  PolylineGraphics,
  Viewer,
  useCesium,
} from 'resium'
import {
  Cartesian2,
  Cartesian3,
  Color,
  createOsmBuildingsAsync,
  createWorldTerrainAsync,
  Ion,
  PinBuilder,
  PolygonHierarchy,
  PolylineDashMaterialProperty,
  Rectangle,
  type TerrainProvider,
  type Viewer as CesiumViewer,
  type Cesium3DTileset,
  type PositionProperty,
  VerticalOrigin,
  NearFarScalar,
  LabelStyle,
  HorizontalOrigin,
} from 'cesium'
import type { CesiumComponentRef } from 'resium'
import { getSig3dScene } from '../api/sig3d'
import type { Alert, GeoJsonFeature, Sig3dResponse } from '../types/api'
import { useLiveAlertsContext } from '../context/LiveAlertsContext'
import { MapSearchBar, type MapSearchPick } from '../components/MapSearchBar'

const TOKEN = import.meta.env.VITE_CESIUM_ION_TOKEN as string | undefined

type RailEntity = {
  id: string
  positions: PositionProperty | Cartesian3[]
  color: Color
  isTunnel: boolean
  isBridge: boolean
}
type ZoneEntity = {
  id: string
  name: string
  zoneType: string
  hierarchy: PolygonHierarchy
  height: number
  extrudedHeight: number
  color: Color
  isTunnel: boolean
  isBridge: boolean
}

const SEV_COLOR: Record<Alert['severity'], Color> = {
  LOW: Color.GRAY,
  MEDIUM: Color.ORANGE,
  HIGH: Color.RED,
  CRITICAL: Color.DARKRED,
}

const ZONE_COLOR: Record<string, Color> = {
  RESTRICTED: Color.RED.withAlpha(0.35),
  TRACK: Color.ORANGE.withAlpha(0.35),
  STATION: Color.CORNFLOWERBLUE.withAlpha(0.35),
  NORMAL: Color.LIME.withAlpha(0.35),
}

function toPolylinePositions(feature: GeoJsonFeature): Cartesian3[] {
  const geometry = feature.geometry
  if (geometry.type === 'LineString') {
    return geometry.coordinates.map(([lon, lat, z = 0]) =>
      Cartesian3.fromDegrees(lon, lat, z),
    )
  }
  if (geometry.type === 'MultiLineString') {
    return geometry.coordinates.flatMap((line) =>
      line.map(([lon, lat, z = 0]) => Cartesian3.fromDegrees(lon, lat, z)),
    )
  }
  return []
}

function toPolygonHierarchy(feature: GeoJsonFeature): PolygonHierarchy | null {
  const geometry = feature.geometry
  const rings =
    geometry.type === 'Polygon'
      ? geometry.coordinates
      : geometry.type === 'MultiPolygon'
        ? geometry.coordinates[0]
        : null
  if (!rings || rings.length === 0) return null
  const outerRing = rings[0]
  return new PolygonHierarchy(
    outerRing.map(([lon, lat, z = 0]) => Cartesian3.fromDegrees(lon, lat, z)),
  )
}

/**
 * Resium only exposes {@link CesiumComponentRef#cesiumElement} after an internal `mounted` flag
 * flips; that does not re-render the parent, so reading `viewerRef.current` in a parent `useEffect`
 * often misses the viewer forever. Context updates inside {@link Viewer} do run — sync from here.
 */
function SyncCesiumViewerState({ onViewer }: { onViewer: (v: CesiumViewer | null) => void }) {
  const { viewer } = useCesium()
  useEffect(() => {
    if (viewer && !viewer.isDestroyed()) {
      onViewer(viewer)
      return () => {
        onViewer(null)
      }
    }
    onViewer(null)
    return undefined
  }, [viewer, onViewer])
  return null
}

export function Map3DPage() {
  const { alerts, seedAlerts } = useLiveAlertsContext()
  const [scene, setScene] = useState<Sig3dResponse | null>(null)
  const viewerRef = useRef<CesiumComponentRef<CesiumViewer>>(null)
  const [viewer, setViewer] = useState<CesiumViewer | null>(null)
  const [terrainProvider, setTerrainProvider] = useState<TerrainProvider | undefined>(undefined)
  const [buildings, setBuildings] = useState<Cesium3DTileset | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [showTracks, setShowTracks] = useState(true)
  const [showTunnels, setShowTunnels] = useState(true)
  const [showBridges, setShowBridges] = useState(true)
  const [showZones, setShowZones] = useState(true)
  const [showAlerts, setShowAlerts] = useState(true)
  const [showFusionLinks, setShowFusionLinks] = useState(true)
  const [windowMinutes, setWindowMinutes] = useState(30)
  /** Start minimized so the globe stays clear; expand for search and filters. */
  const [filtersOpen, setFiltersOpen] = useState(false)
  const [alertsPanelOpen, setAlertsPanelOpen] = useState(false)
  const [pendingPick, setPendingPick] = useState<MapSearchPick | null>(null)
  /** Persistent pin for “My location”. */
  const [myLocationPin, setMyLocationPin] = useState<{ lat: number; lon: number } | null>(null)
  /** Persistent pin for the last place chosen from search. */
  const [searchPin, setSearchPin] = useState<{
    lat: number
    lon: number
    title: string
  } | null>(null)

  useEffect(() => {
    getSig3dScene()
      .then((data) => {
        setScene(data)
        seedAlerts(data.alerts)
      })
      .catch((e: unknown) =>
        setError(e instanceof Error ? e.message : 'Failed to load 3D SIG scene'),
      )
      .finally(() => setLoading(false))
  }, [seedAlerts])

  useEffect(() => {
    if (!viewer) return
    if (buildings) return
    const v = viewer
    let disposed = false
    void (async () => {
      const terrain = await createWorldTerrainAsync()
      if (disposed) return
      setTerrainProvider(terrain)
      const osmBuildings = await createOsmBuildingsAsync()
      if (disposed) return
      v.scene.primitives.add(osmBuildings)
      setBuildings(osmBuildings)
    })()
    return () => {
      disposed = true
      if (buildings) {
        v.scene.primitives.remove(buildings)
      }
    }
  }, [viewer, buildings])

  const flyToPlace = useCallback(
    (p: MapSearchPick) => {
      if (!viewer || viewer.isDestroyed()) return
      const lat = Number(p.lat)
      const lon = Number(p.lon)
      if (!Number.isFinite(lat) || !Number.isFinite(lon)) return
      const west = p.west != null ? Number(p.west) : NaN
      const south = p.south != null ? Number(p.south) : NaN
      const east = p.east != null ? Number(p.east) : NaN
      const north = p.north != null ? Number(p.north) : NaN
      if (
        Number.isFinite(west) &&
        Number.isFinite(south) &&
        Number.isFinite(east) &&
        Number.isFinite(north)
      ) {
        viewer.camera.flyTo({
          destination: Rectangle.fromDegrees(west, south, east, north),
          duration: 2.0,
        })
      } else {
        viewer.camera.flyTo({
          destination: Cartesian3.fromDegrees(lon, lat, 22_000),
          duration: 1.8,
        })
      }
    },
    [viewer],
  )

  const applyPick = useCallback(
    (p: MapSearchPick) => {
      const lat = Number(p.lat)
      const lon = Number(p.lon)
      if (!Number.isFinite(lat) || !Number.isFinite(lon)) return
      if (p.pickKind === 'geolocation') {
        setMyLocationPin({ lat, lon })
      } else {
        setSearchPin({
          lat,
          lon,
          title: p.displayName?.trim() || `${lat.toFixed(4)}, ${lon.toFixed(4)}`,
        })
      }
      if (viewer && !viewer.isDestroyed()) {
        flyToPlace(p)
      } else {
        setPendingPick(p)
      }
    },
    [viewer, flyToPlace],
  )

  useEffect(() => {
    if (!viewer || !pendingPick) return
    flyToPlace(pendingPick)
    setPendingPick(null)
  }, [viewer, pendingPick, flyToPlace])

  const pinDataUrls = useMemo(() => {
    const pb = new PinBuilder()
    return {
      LOW: pb.fromColor(SEV_COLOR.LOW, 40).toDataURL(),
      MEDIUM: pb.fromColor(SEV_COLOR.MEDIUM, 40).toDataURL(),
      HIGH: pb.fromColor(SEV_COLOR.HIGH, 40).toDataURL(),
      CRITICAL: pb.fromColor(SEV_COLOR.CRITICAL, 40).toDataURL(),
      MY_LOCATION: pb.fromColor(Color.DEEPSKYBLUE, 42).toDataURL(),
      SEARCH: pb.fromColor(Color.MEDIUMSPRINGGREEN, 42).toDataURL(),
    } as const
  }, [])

  const railEntities = useMemo<RailEntity[]>(() => {
    if (!scene) return []
    return scene.rail.features
      .map((f, idx) => {
        const positions = toPolylinePositions(f)
        return {
          id: `rail-${idx}`,
          positions,
          color: Color.GOLD.withAlpha(0.95),
          isTunnel: Boolean(f.properties.isTunnel),
          isBridge: Boolean(f.properties.isBridge),
        }
      })
      .filter((r) => Array.isArray(r.positions) && r.positions.length > 1)
  }, [scene])

  const zoneEntities = useMemo<ZoneEntity[]>(() => {
    if (!scene) return []
    return scene.zones.features
      .map((f, idx) => {
        const hierarchy = toPolygonHierarchy(f)
        const p = f.properties
        const zoneType = String(p.type ?? 'NORMAL')
        const elevationM = Number(p.elevationM ?? 0)
        const heightM = Number(p.heightM ?? 8)
        return {
          id: `zone-${idx}`,
          name: String(p.name ?? `Zone ${idx + 1}`),
          zoneType,
          hierarchy: hierarchy!,
          height: elevationM,
          extrudedHeight: elevationM + Math.max(heightM, 4),
          color: ZONE_COLOR[zoneType] ?? Color.LIME.withAlpha(0.35),
          isTunnel: Boolean(p.isTunnel),
          isBridge: Boolean(p.isBridge),
        }
      })
      .filter((z) => !!z.hierarchy)
  }, [scene])

  const filteredRail = useMemo(
    () =>
      railEntities.filter((r) => {
        if (!showTracks) return false
        if (!showTunnels && r.isTunnel) return false
        if (!showBridges && r.isBridge) return false
        return true
      }),
    [railEntities, showTracks, showTunnels, showBridges],
  )

  const filteredZones = useMemo(
    () =>
      zoneEntities.filter((z) => {
        if (!showZones) return false
        if (!showTracks && z.zoneType === 'TRACK') return false
        if (!showTunnels && z.isTunnel) return false
        if (!showBridges && z.isBridge) return false
        return true
      }),
    [zoneEntities, showZones, showTracks, showTunnels, showBridges],
  )

  const filteredAlerts = useMemo(
    () =>
      alerts
        .filter((a) => a.latitude != null && a.longitude != null)
        .filter((a) => {
          const ageMs = Date.now() - new Date(a.createdAt).getTime()
          return ageMs <= windowMinutes * 60 * 1000
        })
        .map((a) => ({
          id: `alert-${a.id}`,
          source: a,
          title: `[${a.severity}] ${a.type}`,
          message: a.message,
          createdAt: new Date(a.createdAt).toLocaleString(),
          position: Cartesian3.fromDegrees(a.longitude!, a.latitude!, 8),
          image: pinDataUrls[a.severity],
        })),
    [alerts, pinDataUrls, windowMinutes],
  )

  const clusteredAlertEntities = useMemo(() => {
    if (filteredAlerts.length <= 100) {
      return filteredAlerts.map((a) => ({
        id: a.id,
        kind: 'single' as const,
        position: a.position,
        image: a.image,
        title: a.title,
        description: `${a.message}<br/><small>${a.createdAt}</small>`,
        source: a.source,
      }))
    }
    const buckets = new Map<string, typeof filteredAlerts>()
    for (const a of filteredAlerts) {
      const lat = a.source.latitude!
      const lon = a.source.longitude!
      const key = `${Math.round(lat * 400)}:${Math.round(lon * 400)}`
      const list = buckets.get(key) ?? []
      list.push(a)
      buckets.set(key, list)
    }
    return Array.from(buckets.entries()).map(([key, list]) => {
      const avgLat = list.reduce((acc, x) => acc + x.source.latitude!, 0) / list.length
      const avgLon = list.reduce((acc, x) => acc + x.source.longitude!, 0) / list.length
      const latest = list
        .slice()
        .sort(
          (a, b) =>
            new Date(b.source.createdAt).getTime() - new Date(a.source.createdAt).getTime(),
        )[0]
      return {
        id: `cluster-${key}`,
        kind: list.length > 1 ? ('cluster' as const) : ('single' as const),
        count: list.length,
        position: Cartesian3.fromDegrees(avgLon, avgLat, 10),
        image: latest.image,
        title: latest.title,
        description:
          list.length > 1
            ? `${list.length} alerts clustered in this area`
            : `${latest.message}<br/><small>${latest.createdAt}</small>`,
        source: latest.source,
      }
    })
  }, [filteredAlerts])

  /**
   * Visualise each FUSION alert as a dashed polyline joining the camera
   * detection point to the SIG event point. Color encodes the fusion score
   * so the operator can tell a tight (score 0.9) correlation from a loose
   * (score 0.6) one at a glance.
   */
  const fusionLinks = useMemo(() => {
    return filteredAlerts
      .filter((a) => a.source.type === 'FUSION')
      .map((a) => {
        const d = a.source.details
        const cam = d?.camera
        const sig = d?.sig
        if (
          cam == null ||
          sig == null ||
          cam.lat == null ||
          cam.lon == null ||
          sig.lat == null ||
          sig.lon == null
        )
          return null
        const camPos = Cartesian3.fromDegrees(
          cam.lon,
          cam.lat,
          (cam.elevationM ?? 0) + 6,
        )
        const sigPos = Cartesian3.fromDegrees(
          sig.lon,
          sig.lat,
          (sig.elevationM ?? 0) + 6,
        )
        const score = typeof d?.fusionScore === 'number' ? d.fusionScore : 0
        const baseColor =
          score >= 0.85
            ? Color.RED
            : score >= 0.65
              ? Color.ORANGE
              : Color.YELLOW
        return {
          id: `fusion-link-${a.source.id}`,
          positions: [camPos, sigPos],
          color: baseColor.withAlpha(0.95),
          score,
          camId: cam.id ?? 'camera',
          sigId: sig.sourceId ?? 'sig',
          distanceM: typeof d?.distanceM === 'number' ? d.distanceM : null,
          timeDeltaSec:
            typeof d?.timeDeltaSec === 'number' ? d.timeDeltaSec : null,
          zoneName: d?.zoneName ?? a.source.zoneName ?? '—',
        }
      })
      .filter((x): x is NonNullable<typeof x> => x != null)
  }, [filteredAlerts])

  const flyToAlert = (alert: Alert) => {
    if (!viewer || alert.latitude == null || alert.longitude == null) return
    viewer.camera.flyTo({
      destination: Cartesian3.fromDegrees(alert.longitude, alert.latitude, 1200),
      duration: 1.2,
    })
  }

  const latestAlertsPanel = useMemo(
    () =>
      filteredAlerts
        .slice()
        .sort(
          (a, b) =>
            new Date(b.source.createdAt).getTime() - new Date(a.source.createdAt).getTime(),
        )
        .slice(0, 8)
        .map((a) => a.source),
    [filteredAlerts],
  )

  if (!TOKEN) {
    return (
      <div className="card">
        <h3>Cesium token missing</h3>
        <p className="muted">
          Set <code>VITE_CESIUM_ION_TOKEN</code> in <code>frontend/.env</code> then restart Vite.
        </p>
      </div>
    )
  }

  Ion.defaultAccessToken = TOKEN

  if (loading) return <p className="muted">Loading 3D map…</p>
  if (error) return <p className="muted">3D map error: {error}</p>

  return (
    <div className="map3d-page">
      <div className="map3d-viewport">
        <Viewer
          ref={viewerRef}
          full
          terrainProvider={terrainProvider}
          timeline={false}
          animation={false}
          geocoder={false}
          baseLayerPicker={false}
          navigationHelpButton={false}
          homeButton={true}
          sceneModePicker={true}
        >
          <SyncCesiumViewerState onViewer={setViewer} />
          {myLocationPin && (
            <Entity
              name="My location"
              description="Position from your browser (GPS). This pin stays until you use “My location” again."
              position={Cartesian3.fromDegrees(myLocationPin.lon, myLocationPin.lat, 20)}
            >
              <BillboardGraphics
                image={pinDataUrls.MY_LOCATION}
                verticalOrigin={VerticalOrigin.BOTTOM}
                scaleByDistance={new NearFarScalar(500, 1.0, 30000, 0.55)}
              />
            </Entity>
          )}
          {searchPin && (
            <Entity
              name="Search result"
              description={searchPin.title}
              position={Cartesian3.fromDegrees(searchPin.lon, searchPin.lat, 20)}
            >
              <BillboardGraphics
                image={pinDataUrls.SEARCH}
                verticalOrigin={VerticalOrigin.BOTTOM}
                scaleByDistance={new NearFarScalar(500, 1.0, 30000, 0.55)}
              />
            </Entity>
          )}
          {filteredRail.map((rail) => (
            <Entity key={rail.id} name="Rail line">
              <PolylineGraphics
                positions={rail.positions}
                width={4}
                material={rail.color}
                clampToGround={false}
              />
            </Entity>
          ))}

          {filteredZones.map((zone) => (
            <Entity key={zone.id} name={zone.name}>
              <PolygonGraphics
                hierarchy={zone.hierarchy}
                material={zone.color}
                perPositionHeight={true}
                height={zone.height}
                extrudedHeight={zone.extrudedHeight}
                outline={true}
                outlineColor={Color.WHITE.withAlpha(0.4)}
              />
            </Entity>
          ))}

          {showAlerts &&
            clusteredAlertEntities.map((a) => (
              <Entity
                key={a.id}
                name={a.title}
                description={a.description}
                position={a.position}
              >
                <BillboardGraphics
                  image={a.image}
                  verticalOrigin={VerticalOrigin.BOTTOM}
                  scaleByDistance={new NearFarScalar(500, 1.0, 30000, 0.5)}
                />
                {a.kind === 'cluster' && (
                  <LabelGraphics
                    text={`${a.count}`}
                    style={LabelStyle.FILL_AND_OUTLINE}
                    fillColor={Color.WHITE}
                    outlineColor={Color.BLACK}
                    outlineWidth={2}
                    horizontalOrigin={HorizontalOrigin.CENTER}
                    pixelOffset={new Cartesian2(0, -42)}
                    scale={0.6}
                  />
                )}
              </Entity>
            ))}

          {showAlerts &&
            showFusionLinks &&
            fusionLinks.map((link) => (
              <Entity
                key={link.id}
                name={`FUSION ${link.camId} ↔ ${link.sigId}`}
                description={
                  `Fusion score <b>${link.score.toFixed(2)}</b><br/>` +
                  `Zone: ${link.zoneName}<br/>` +
                  (link.distanceM != null
                    ? `Distance: ${link.distanceM >= 1000 ? `${(link.distanceM / 1000).toFixed(2)} km` : `${link.distanceM.toFixed(0)} m`}<br/>`
                    : '') +
                  (link.timeDeltaSec != null
                    ? `Δt: ${link.timeDeltaSec}s<br/>`
                    : '') +
                  `<small>Dashed line links the camera detection to the SIG event that confirmed it.</small>`
                }
              >
                <PolylineGraphics
                  positions={link.positions}
                  width={3}
                  material={
                    new PolylineDashMaterialProperty({
                      color: link.color,
                      dashLength: 16,
                    })
                  }
                  clampToGround={false}
                />
              </Entity>
            ))}
        </Viewer>

        <div className="map3d-hud" aria-label="3D map controls">
          <div
            className={`card map3d-hud-panel map3d-hud-top ${
              filtersOpen ? 'map3d-hud-panel--open' : 'map3d-hud-panel--collapsed'
            }`}
          >
            <div className="map3d-hud-panel-header">
              <span className="map3d-hud-panel-title">Filters &amp; replay</span>
              <button
                type="button"
                className="map3d-hud-toggle"
                aria-expanded={filtersOpen}
                aria-label={filtersOpen ? 'Collapse filters' : 'Expand filters'}
                title={filtersOpen ? 'Collapse' : 'Expand'}
                onClick={() => setFiltersOpen((o) => !o)}
              >
                <span className="map3d-hud-chevron" aria-hidden>
                  {filtersOpen ? '▲' : '▼'}
                </span>
              </button>
            </div>
            {filtersOpen && (
              <>
                <div className="map3d-filter-tools-row">
                  <MapSearchBar
                    onPick={applyPick}
                    suggest
                    className="map3d-filter-tools-search"
                    mapReadyHint={
                      viewer
                        ? null
                        : 'Globe is still loading — your search or location will apply when it is ready.'
                    }
                  />
                </div>
                <p className="muted map-toolbar-hint">
                  Search uses OpenStreetMap Nominatim via your backend. Your last search and “My
                  location” each leave a pin on the globe until replaced.
                </p>
                <p className="map3d-hud-sub muted">
                  Layer toggles and time window for alert pins on the globe.
                </p>
                <div className="map3d-filters">
                  <label>
                    <input
                      type="checkbox"
                      checked={showTracks}
                      onChange={(e) => setShowTracks(e.target.checked)}
                    />{' '}
                    Tracks
                  </label>
                  <label>
                    <input
                      type="checkbox"
                      checked={showTunnels}
                      onChange={(e) => setShowTunnels(e.target.checked)}
                    />{' '}
                    Tunnels
                  </label>
                  <label>
                    <input
                      type="checkbox"
                      checked={showBridges}
                      onChange={(e) => setShowBridges(e.target.checked)}
                    />{' '}
                    Bridges
                  </label>
                  <label>
                    <input
                      type="checkbox"
                      checked={showZones}
                      onChange={(e) => setShowZones(e.target.checked)}
                    />{' '}
                    Zones
                  </label>
                  <label>
                    <input
                      type="checkbox"
                      checked={showAlerts}
                      onChange={(e) => setShowAlerts(e.target.checked)}
                    />{' '}
                    Alerts
                  </label>
                  <label
                    title="Dashed line linking the camera detection point to the SIG event point that confirmed it, for every FUSION alert."
                  >
                    <input
                      type="checkbox"
                      checked={showFusionLinks}
                      onChange={(e) => setShowFusionLinks(e.target.checked)}
                      disabled={!showAlerts}
                    />{' '}
                    Fusion links
                  </label>
                </div>
                <div className="map3d-time">
                  <label htmlFor="alertWindow">
                    Replay window: last {windowMinutes} min
                  </label>
                  <input
                    id="alertWindow"
                    type="range"
                    min={1}
                    max={180}
                    value={windowMinutes}
                    onChange={(e) => setWindowMinutes(Number(e.target.value))}
                  />
                </div>
              </>
            )}
          </div>

          <div
            className={`card map3d-hud-panel map3d-hud-bottom ${
              alertsPanelOpen ? 'map3d-hud-panel--open' : 'map3d-hud-panel--collapsed'
            }`}
          >
            <div className="map3d-hud-panel-header">
              <span className="map3d-hud-panel-title">
                Latest alerts
                <span className="map3d-hud-badge">({latestAlertsPanel.length})</span>
              </span>
              <button
                type="button"
                className="map3d-hud-toggle"
                aria-expanded={alertsPanelOpen}
                aria-label={alertsPanelOpen ? 'Collapse alerts' : 'Expand alerts'}
                title={alertsPanelOpen ? 'Collapse' : 'Expand'}
                onClick={() => setAlertsPanelOpen((o) => !o)}
              >
                <span className="map3d-hud-chevron" aria-hidden>
                  {alertsPanelOpen ? '▼' : '▲'}
                </span>
              </button>
            </div>
            {alertsPanelOpen && (
              <>
                <p className="map3d-hud-sub muted">Click an alert to fly the camera.</p>
                {latestAlertsPanel.length === 0 ? (
                  <p className="muted">No alerts in selected time window.</p>
                ) : (
                  <div className="map3d-alert-list">
                    {latestAlertsPanel.map((a) => (
                      <button
                        key={a.id}
                        type="button"
                        className="btn secondary"
                        onClick={() => flyToAlert(a)}
                      >
                        [{a.severity}] {a.type} —{' '}
                        {new Date(a.createdAt).toLocaleTimeString()}
                      </button>
                    ))}
                  </div>
                )}
              </>
            )}
          </div>
        </div>
      </div>
    </div>
  )
}
