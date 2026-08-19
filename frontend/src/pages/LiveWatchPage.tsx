import {
  useCallback,
  useEffect,
  useMemo,
  useRef,
  useState,
} from 'react'
import {
  getLiveStatus,
  pushGpsEvent,
  type LiveStatusSnapshot,
} from '../api/live'
import {
  CAMERAS as CAMERA_REGISTRY,
  useLiveCameras,
  type CameraRuntime,
} from '../context/LiveCamerasContext'
import { loadPrefs } from '../lib/prefs'
import { useT } from '../lib/useT'

type GpsState = 'idle' | 'starting' | 'running' | 'error'

const STATUS_POLL_MS = 5000

function statusLabel(c: CameraRuntime): string {
  if (!c.enabled) return 'disabled'
  switch (c.status) {
    case 'running':
      return 'live'
    case 'starting':
      return 'connecting'
    case 'idle':
      return 'idle'
    case 'error':
      return 'error'
  }
}

function relativeAgeMs(ts: number | null): string {
  if (ts == null) return 'never'
  const diff = Math.max(0, Math.round((Date.now() - ts) / 1000))
  if (diff < 5) return 'just now'
  if (diff < 60) return `${diff}s ago`
  if (diff < 3600) return `${Math.round(diff / 60)}m ago`
  return `${Math.round(diff / 3600)}h ago`
}

function relativeAgeISO(iso: string | null | undefined): string {
  if (!iso) return 'never'
  return relativeAgeMs(new Date(iso).getTime())
}

export function LiveWatchPage() {
  const t = useT()
  const {
    cameras,
    zones,
    setCameraEnabled,
    getVideoElement,
  } = useLiveCameras()

  /* ----------------------- preview selection ----------------------- */

  const [selectedKey, setSelectedKey] = useState<string>(
    () => CAMERA_REGISTRY[0]?.key ?? '',
  )
  const selectedCamera = useMemo<CameraRuntime | null>(
    () => cameras.find((c) => c.key === selectedKey) ?? cameras[0] ?? null,
    [cameras, selectedKey],
  )

  const previewVideoRef = useRef<HTMLVideoElement | null>(null)
  const overlayCanvasRef = useRef<HTMLCanvasElement | null>(null)

  /* Mirror the provider's hidden video into the visible preview via
   * {@code captureStream()} without duplicating the HLS connection. */
  useEffect(() => {
    const visible = previewVideoRef.current
    if (!visible) return
    if (!selectedCamera || selectedCamera.status !== 'running') {
      visible.srcObject = null
      return
    }
    const hidden = getVideoElement(selectedCamera.key)
    if (!hidden) {
      visible.srcObject = null
      return
    }
    let stream: MediaStream | null = null
    try {
      // captureStream() is widely supported but still absent from TS's
      // HTMLVideoElement lib types, hence the narrow local cast.
      const capturable = hidden as HTMLVideoElement & {
        captureStream?: () => MediaStream
      }
      stream = capturable.captureStream?.() ?? null
    } catch (e) {
      console.warn('captureStream() unavailable on selected camera', e)
    }
    visible.srcObject = stream
    if (stream) {
      void visible.play().catch(() => {
        /* autoplay denied — operator can click the video to play */
      })
    }
  }, [selectedCamera, getVideoElement])

  /* Keep the overlay canvas clear and correctly sized. Detection runs
   * server-side in the YOLOv8 service now, so the browser never sees
   * bounding boxes — there is nothing to draw here. The canvas is kept so
   * the preview layout is unchanged and a future server-supplied overlay
   * has somewhere to render. */
  useEffect(() => {
    const cam = selectedCamera
    const canvas = overlayCanvasRef.current
    if (!cam || !canvas) return
    const hidden = getVideoElement(cam.key)
    const w = hidden?.videoWidth || 640
    const h = hidden?.videoHeight || 480
    if (canvas.width !== w) canvas.width = w
    if (canvas.height !== h) canvas.height = h
    canvas.getContext('2d')?.clearRect(0, 0, canvas.width, canvas.height)
  }, [selectedCamera, getVideoElement])

  /* ---------------------------- aggregates -------------------------- */

  const activeCount = useMemo(
    () => cameras.filter((c) => c.enabled && c.status === 'running').length,
    [cameras],
  )

  /* --------------------------- live status -------------------------- */

  const [status, setStatus] = useState<LiveStatusSnapshot | null>(null)
  useEffect(() => {
    let active = true
    const tick = () => {
      getLiveStatus()
        .then((s) => {
          if (active) setStatus(s)
        })
        .catch(() => {
          /* tolerated */
        })
    }
    tick()
    const id = window.setInterval(tick, STATUS_POLL_MS)
    return () => {
      active = false
      window.clearInterval(id)
    }
  }, [])

  /* --------------------------- GPS streaming ------------------------ */

  const gpsWatchIdRef = useRef<number | null>(null)
  const lastGpsPostRef = useRef<number>(0)
  const [gpsState, setGpsState] = useState<GpsState>('idle')
  const [gpsError, setGpsError] = useState<string | null>(null)
  const [gpsPostedCount, setGpsPostedCount] = useState(0)
  const [useDeviceGps, setUseDeviceGps] = useState(true)
  const [gpsZoneId, setGpsZoneId] = useState<number | ''>('')

  useEffect(() => {
    if (gpsZoneId === '' && zones.length > 0) {
      const rabat = zones.find((z) => /rabat/i.test(z.name))
      // eslint-disable-next-line react-hooks/set-state-in-effect
      setGpsZoneId(rabat?.id ?? zones[0].id)
    }
  }, [zones, gpsZoneId])

  const selectedGpsZone = useMemo(
    () => zones.find((z) => z.id === gpsZoneId) ?? null,
    [zones, gpsZoneId],
  )

  const stopGps = useCallback(() => {
    if (gpsWatchIdRef.current != null && 'geolocation' in navigator) {
      navigator.geolocation.clearWatch(gpsWatchIdRef.current)
      gpsWatchIdRef.current = null
    }
    setGpsState('idle')
  }, [])

  const startGps = useCallback(() => {
    if (gpsState === 'running' || gpsState === 'starting') return
    // Geolocation is consented to once, in Settings.
    if (!loadPrefs().location_tracking) {
      setGpsError('Location tracking is off — enable it in Settings first.')
      setGpsState('error')
      return
    }
    if (!('geolocation' in navigator)) {
      setGpsError('Geolocation not supported in this browser')
      setGpsState('error')
      return
    }
    setGpsState('starting')
    setGpsError(null)
    gpsWatchIdRef.current = navigator.geolocation.watchPosition(
      async (pos) => {
        setGpsState('running')
        const now = Date.now()
        if (now - lastGpsPostRef.current < 5000) return
        lastGpsPostRef.current = now

        const useReal = useDeviceGps
        const [lat, lon] = useReal
          ? [pos.coords.latitude, pos.coords.longitude]
          : selectedGpsZone
            ? [selectedGpsZone.centerLat, selectedGpsZone.centerLon]
            : [pos.coords.latitude, pos.coords.longitude]

        try {
          await pushGpsEvent({
            sourceId: 'GPS-DEVICE',
            latitude: lat,
            longitude: lon,
            elevationM: pos.coords.altitude ?? 0,
            zoneId: selectedGpsZone?.id ?? null,
            metadata: {
              source: 'browser_geolocation',
              accuracyM: pos.coords.accuracy,
              speed: pos.coords.speed,
              heading: pos.coords.heading,
              real_position: useReal,
            },
          })
          setGpsPostedCount((c) => c + 1)
        } catch (err) {
          console.warn('gps push failed', err)
        }
      },
      (err) => {
        setGpsError(err.message || 'Geolocation error')
        setGpsState('error')
      },
      { enableHighAccuracy: true, maximumAge: 5_000, timeout: 15_000 },
    )
  }, [gpsState, selectedGpsZone, useDeviceGps])

  useEffect(() => () => stopGps(), [stopGps])

  /* ----------------------------- render ----------------------------- */

  return (
    <>
      <div className="page-header">
        <div>
          <h2>{t('live.title')}</h2>
          <p>{t('live.subtitle')}</p>
          <p className="muted small" style={{ marginTop: 4 }}>
            <b>{activeCount}</b> camera{activeCount === 1 ? '' : 's'} live &middot;{' '}
            <b>{status?.webcamEventsTotal ?? 0}</b> detection
            {status?.webcamEventsTotal === 1 ? '' : 's'} ingested
          </p>
        </div>
      </div>

      <div className="live-grid">
        <div className="card live-card live-camera-merged">
          <div className="live-card-header">
            <h3>Live camera</h3>
            <span
              className={`pill pill-${
                selectedCamera?.status ?? 'idle'
              }`}
            >
              {selectedCamera
                ? `${selectedCamera.label.split('—')[0].trim()} · ${statusLabel(selectedCamera)}`
                : 'no camera'}
            </span>
          </div>

          <div className="form-row">
            <label>Preview</label>
            <select
              value={selectedKey}
              onChange={(e) => setSelectedKey(e.target.value)}
            >
              {cameras.map((c) => (
                <option key={c.key} value={c.key}>
                  {c.label}
                </option>
              ))}
            </select>
          </div>

          <div className="webcam-wrap">
            <video
              ref={previewVideoRef}
              muted
              playsInline
              className="webcam-video"
              controls
            />
            <canvas ref={overlayCanvasRef} className="webcam-overlay" />
            {selectedCamera && selectedCamera.status !== 'running' && (
              <div className="webcam-placeholder">
                {!selectedCamera.enabled && 'Camera disabled below'}
                {selectedCamera.enabled && selectedCamera.status === 'idle' &&
                  'Waiting for stream…'}
                {selectedCamera.enabled && selectedCamera.status === 'starting' &&
                  'Connecting…'}
                {selectedCamera.enabled && selectedCamera.status === 'error' &&
                  (selectedCamera.error ?? 'Stream error')}
              </div>
            )}
          </div>

          {selectedCamera && (
            <div className="muted small" style={{ marginTop: 8 }}>
              <div>
                Detections ingested: <b>{status?.webcamEventsTotal ?? 0}</b>{' '}
                &middot; last detection:{' '}
                <b>
                  {relativeAgeMs(
                    status?.lastWebcamEventAt
                      ? Date.parse(status.lastWebcamEventAt)
                      : null,
                  )}
                </b>
              </div>
              <div>
                Analysed server-side by the YOLOv8 detector service.
              </div>
            </div>
          )}
        </div>

        <div className="card live-card">
          <div className="live-card-header">
            <h3>AI cameras</h3>
            <span className="pill pill-running">
              {activeCount}/{cameras.length} live
            </span>
          </div>
          <p className="muted small" style={{ marginTop: 0 }}>
            Toggle a camera off to stop ingestion from it; bind to a zone so
            every detection is reported at that zone's center for fusion. Both
            settings persist across reloads.
          </p>

          <div className="live-camera-list">
            {cameras.map((c) => (
              <div key={c.key} className="live-camera-row">
                <div className="live-camera-row-head">
                  <span className={`pill pill-${c.status}`}>
                    {statusLabel(c)}
                  </span>
                  <span className="live-camera-row-name">{c.label}</span>
                </div>
                <div className="live-camera-row-stats muted small">
                  <span>
                    stream <b>{statusLabel(c)}</b>
                  </span>
                  <span>id <b>{c.id}</b></span>
                  {c.error && (
                    <span style={{ color: 'var(--danger)' }}>{c.error}</span>
                  )}
                </div>
                <div className="live-camera-row-controls">
                  <label className="live-camera-toggle">
                    <input
                      type="checkbox"
                      checked={c.enabled}
                      onChange={(e) =>
                        setCameraEnabled(c.key, e.target.checked)
                      }
                    />{' '}
                    enabled
                  </label>
                  <span className="muted small" title="Configured once when the camera was registered — not picked here.">
                    location: fixed at registration
                  </span>
                </div>
              </div>
            ))}
          </div>
        </div>

        <div className="card live-card">
          <div className="live-card-header">
            <h3>SIG sources</h3>
            <span className={`pill pill-${gpsState}`}>{gpsState}</span>
          </div>
          <div className="muted small" style={{ marginBottom: 10 }}>
            <b>OpenSky Network</b> — backend polls aircraft over Morocco every
            few seconds, no setup required.
          </div>
          <div className="status-row">
            <span>Last OpenSky poll</span>
            <b>{relativeAgeISO(status?.lastOpenSkyPollAt)}</b>
          </div>
          <div className="status-row">
            <span>OpenSky tracks ingested</span>
            <b>{status?.openSkyEventsTotal ?? 0}</b>
          </div>
          {status?.lastOpenSkyError && (
            <div className="muted small" style={{ color: 'var(--warn)' }}>
              OpenSky error: {status.lastOpenSkyError}
            </div>
          )}

          <hr className="divider" />

          <div className="form-row">
            <label>GPS bound to zone</label>
            <select
              value={gpsZoneId}
              onChange={(e) =>
                setGpsZoneId(e.target.value ? Number(e.target.value) : '')
              }
            >
              <option value="">(my real GPS only)</option>
              {zones.map((z) => (
                <option key={z.id} value={z.id}>
                  {z.name} — {z.type}
                </option>
              ))}
            </select>
          </div>
          <div className="form-row">
            <label>
              <input
                type="checkbox"
                checked={useDeviceGps}
                onChange={(e) => setUseDeviceGps(e.target.checked)}
              />{' '}
              Use my real device GPS
            </label>
          </div>
          <div className="muted small" style={{ marginBottom: 10 }}>
            When on, pushes a SIG event every ~5 s using browser geolocation.
            When off, snaps to the selected zone center (handy when you want
            to test fusion from a desk).
          </div>
          <div className="btn-row">
            {gpsState !== 'running' ? (
              <button className="btn secondary" onClick={startGps}>
                Start GPS streaming
              </button>
            ) : (
              <button className="btn danger" onClick={stopGps}>
                Stop GPS streaming
              </button>
            )}
          </div>
          {gpsError && (
            <div className="muted small" style={{ color: 'var(--danger)' }}>
              {gpsError}
            </div>
          )}
          <div className="muted small">
            GPS events posted this session: <b>{gpsPostedCount}</b>
          </div>
          <div className="status-row">
            <span>Total camera AI events (all sessions)</span>
            <b>{status?.webcamEventsTotal ?? 0}</b>
          </div>
          <div className="status-row">
            <span>Total GPS events (all sessions)</span>
            <b>{status?.gpsEventsTotal ?? 0}</b>
          </div>
        </div>
      </div>
    </>
  )
}
