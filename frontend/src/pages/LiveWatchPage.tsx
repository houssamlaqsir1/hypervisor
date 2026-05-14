import {
  useCallback,
  useEffect,
  useMemo,
  useRef,
  useState,
} from 'react'
import type { Alert } from '../types/api'
import {
  getLiveStatus,
  pushGpsEvent,
  type LiveStatusSnapshot,
} from '../api/live'
import { useLiveAlertsContext } from '../context/LiveAlertsContext'
import {
  CAMERAS as CAMERA_REGISTRY,
  useLiveCameras,
  type CameraRuntime,
} from '../context/LiveCamerasContext'
import { AlertRow } from '../components/AlertRow'

type GpsState = 'idle' | 'starting' | 'running' | 'error'

const STATUS_POLL_MS = 5000

/**
 * COCO-SSD classes the provider treats as "relevant" — used here only to
 * color the overlay boxes (green for railway-relevant, grey otherwise).
 * Keep in sync with {@code RELEVANT_CLASSES} in {@link
 * '../context/LiveCamerasContext'}.
 */
const RELEVANT_LABELS = new Set([
  'person',
  'bicycle',
  'car',
  'motorcycle',
  'bus',
  'truck',
  'train',
  'backpack',
  'handbag',
  'suitcase',
  'dog',
])

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
  const {
    cameras,
    zones,
    setCameraZoneId,
    setCameraEnabled,
    getVideoElement,
  } = useLiveCameras()
  const { alerts } = useLiveAlertsContext()

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
   * {@code captureStream()}. This works for both webcam (MediaStream) and
   * HLS (MSE-backed video) without duplicating connections. */
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
      stream = hidden.captureStream?.() ?? null
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

  /* Redraw detection overlay on the preview canvas whenever the selected
   * camera's lastDetections updates (provider sets it ~1 fps per camera). */
  useEffect(() => {
    const cam = selectedCamera
    const canvas = overlayCanvasRef.current
    if (!cam || !canvas) return
    const hidden = getVideoElement(cam.key)
    const w = hidden?.videoWidth || 640
    const h = hidden?.videoHeight || 480
    if (canvas.width !== w) canvas.width = w
    if (canvas.height !== h) canvas.height = h
    const ctx = canvas.getContext('2d')
    if (!ctx) return
    ctx.clearRect(0, 0, canvas.width, canvas.height)
    if (cam.status !== 'running') return
    ctx.font = '14px Inter, sans-serif'
    for (const d of cam.lastDetections) {
      const [x, y, bw, bh] = d.bbox
      const known = RELEVANT_LABELS.has(d.class)
      ctx.strokeStyle = known ? '#22c55e' : '#9aa7c2'
      ctx.lineWidth = 2
      ctx.strokeRect(x, y, bw, bh)
      const label = `${d.class} ${(d.score * 100).toFixed(0)}%`
      const tw = ctx.measureText(label).width + 10
      ctx.fillStyle = known ? '#22c55e' : '#9aa7c2'
      ctx.fillRect(x, y - 18, tw, 18)
      ctx.fillStyle = '#0b1220'
      ctx.fillText(label, x + 5, y - 5)
    }
  }, [selectedCamera, getVideoElement])

  /* ---------------------------- aggregates -------------------------- */

  const activeCount = useMemo(
    () => cameras.filter((c) => c.enabled && c.status === 'running').length,
    [cameras],
  )
  const totalPosted = useMemo(
    () => cameras.reduce((acc, c) => acc + c.postedCount, 0),
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
      // eslint-disable-next-line react-hooks/set-state-in-effect
      setGpsZoneId(zones[0].id)
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

  /* ------------------------- Xeoma webhook card --------------------- */

  const defaultBackendBase = useMemo(() => {
    if (typeof window === 'undefined') return 'http://localhost:8080'
    const { protocol, hostname } = window.location
    return `${protocol}//${hostname}:8080`
  }, [])

  const [xeomaCameraKey, setXeomaCameraKey] = useState<string>(
    () =>
      CAMERA_REGISTRY.find((c) => c.kind === 'phone-hls')?.key ??
      CAMERA_REGISTRY[0]?.key ??
      '',
  )
  const [xeomaBackendBase, setXeomaBackendBase] = useState(defaultBackendBase)
  const [xeomaZoneId, setXeomaZoneId] = useState<number | ''>('')
  const [copied, setCopied] = useState<'get' | 'post' | null>(null)

  useEffect(() => {
    if (xeomaZoneId === '' && zones.length > 0) {
      // eslint-disable-next-line react-hooks/set-state-in-effect
      setXeomaZoneId(zones[0].id)
    }
  }, [zones, xeomaZoneId])

  const xeomaCamera = useMemo(
    () => cameras.find((c) => c.key === xeomaCameraKey),
    [cameras, xeomaCameraKey],
  )

  const xeomaBaseTrimmed = useMemo(
    () => xeomaBackendBase.replace(/\/+$/, ''),
    [xeomaBackendBase],
  )

  const xeomaGetUrl = useMemo(() => {
    const params = new URLSearchParams()
    params.set('cameraId', xeomaCamera?.id ?? 'PHONE-XEOMA')
    if (xeomaZoneId !== '') params.set('zoneId', String(xeomaZoneId))
    params.set('label', '%CLASS%')
    params.set('detector', '%DETECTOR_NAME%')
    params.set('detail', '%CAMERA_NAME%')
    params.set('source', 'xeoma')
    return `${xeomaBaseTrimmed}/api/live/ip-camera?${params.toString()}`
  }, [xeomaBaseTrimmed, xeomaCamera, xeomaZoneId])

  const xeomaPostBody = useMemo(
    () =>
      JSON.stringify(
        {
          cameraId: xeomaCamera?.id ?? 'PHONE-XEOMA',
          zoneId: xeomaZoneId === '' ? null : xeomaZoneId,
          label: '%CLASS%',
          detector: '%DETECTOR_NAME%',
          detail: '%CAMERA_NAME%',
          source: 'xeoma',
        },
        null,
        2,
      ),
    [xeomaCamera, xeomaZoneId],
  )
  const xeomaPostUrl = `${xeomaBaseTrimmed}/api/live/ip-camera`

  const copyToClipboard = useCallback(
    async (text: string, kind: 'get' | 'post') => {
      try {
        await navigator.clipboard.writeText(text)
        setCopied(kind)
        window.setTimeout(() => setCopied(null), 1500)
      } catch (e) {
        console.warn('clipboard write failed', e)
      }
    },
    [],
  )

  const xeomaWarnLocalhost = /^https?:\/\/(localhost|127\.0\.0\.1)/i.test(
    xeomaBaseTrimmed,
  )

  /* ---------------------------- alerts ----------------------------- */

  const fusionAlerts = useMemo(
    () => alerts.filter((a: Alert) => a.type === 'FUSION').slice(0, 8),
    [alerts],
  )
  const otherAlerts = useMemo(
    () => alerts.filter((a: Alert) => a.type !== 'FUSION').slice(0, 12),
    [alerts],
  )

  /* ----------------------------- render ----------------------------- */

  return (
    <>
      <div className="page-header">
        <div>
          <h2>Live Watch</h2>
          <p>
            Every enabled AI camera runs in the background as long as this app
            is open in your browser — no <b>Start</b> click required. The
            hypervisor receives detections, runs correlation, and emits alerts
            for <b>all</b> of them in parallel. Open this tab on a NOC
            monitor and the cameras will keep watching.
          </p>
          <p className="muted small" style={{ marginTop: 4 }}>
            <b>{activeCount}</b> camera{activeCount === 1 ? '' : 's'} live &middot;{' '}
            <b>{totalPosted}</b> event{totalPosted === 1 ? '' : 's'} posted this
            session
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
              controls={selectedCamera?.kind === 'phone-hls'}
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
                Events posted from this camera:{' '}
                <b>{selectedCamera.postedCount}</b> &middot; last AI hit:{' '}
                <b>{relativeAgeMs(selectedCamera.lastDetectionAt)}</b>
              </div>
              {selectedCamera.lastDetections.length > 0 && (
                <div>
                  Last frame:{' '}
                  {selectedCamera.lastDetections
                    .map((d) => `${d.class} ${(d.score * 100).toFixed(0)}%`)
                    .join(', ')}
                </div>
              )}
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
                    last AI hit <b>{relativeAgeMs(c.lastDetectionAt)}</b>
                  </span>
                  <span>
                    events <b>{c.postedCount}</b>
                  </span>
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
                  <select
                    value={c.zoneId}
                    onChange={(e) =>
                      setCameraZoneId(
                        c.key,
                        e.target.value ? Number(e.target.value) : '',
                      )
                    }
                  >
                    <option value="">(use my GPS)</option>
                    {zones.map((z) => (
                      <option key={z.id} value={z.id}>
                        {z.name} — {z.type}
                      </option>
                    ))}
                  </select>
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
            <span>Total webcam events (all sessions)</span>
            <b>{status?.webcamEventsTotal ?? 0}</b>
          </div>
          <div className="status-row">
            <span>Total GPS events (all sessions)</span>
            <b>{status?.gpsEventsTotal ?? 0}</b>
          </div>
        </div>

        <div className="card live-card">
          <div className="live-card-header">
            <h3>Phone / IP camera (Xeoma)</h3>
            <span className="pill pill-running">webhook</span>
          </div>
          <div className="muted small" style={{ marginBottom: 8 }}>
            Use Xeoma's <b>HTTP Request Sender</b> module — every time its
            on-board AI fires (motion, object, person, face…), it hits the
            URL below and a real CameraEvent enters the correlation engine.
            Works alongside the browser detection above, or instead of it.
          </div>

          <div className="form-row">
            <label>Camera</label>
            <select
              value={xeomaCameraKey}
              onChange={(e) => setXeomaCameraKey(e.target.value)}
            >
              {cameras.map((c) => (
                <option key={c.key} value={c.key}>
                  {c.label}
                </option>
              ))}
            </select>
          </div>
          <div className="form-row">
            <label>Bind to zone</label>
            <select
              value={xeomaZoneId}
              onChange={(e) =>
                setXeomaZoneId(e.target.value ? Number(e.target.value) : '')
              }
            >
              <option value="">(default location)</option>
              {zones.map((z) => (
                <option key={z.id} value={z.id}>
                  {z.name} — {z.type}
                </option>
              ))}
            </select>
          </div>
          <div className="form-row">
            <label>Backend URL</label>
            <input
              type="text"
              value={xeomaBackendBase}
              onChange={(e) => setXeomaBackendBase(e.target.value)}
              placeholder="http://192.168.1.42:8080"
            />
          </div>
          {xeomaWarnLocalhost && (
            <div className="muted small" style={{ color: 'var(--warn)' }}>
              Heads-up: <code>localhost</code> works only when Xeoma runs on
              this same machine. From your iPhone, replace it with the LAN IP
              of this PC (e.g. <code>192.168.x.x</code>).
            </div>
          )}

          <div className="webhook-block">
            <div className="webhook-block-header">
              <b>1. Easy: GET URL with placeholders</b>
              <button
                className="btn secondary btn-sm"
                onClick={() => copyToClipboard(xeomaGetUrl, 'get')}
              >
                {copied === 'get' ? 'Copied!' : 'Copy URL'}
              </button>
            </div>
            <code className="webhook-url">{xeomaGetUrl}</code>
          </div>

          <div className="webhook-block">
            <div className="webhook-block-header">
              <b>2. Or: POST JSON body</b>
              <button
                className="btn secondary btn-sm"
                onClick={() =>
                  copyToClipboard(
                    `${xeomaPostUrl}\n\n${xeomaPostBody}`,
                    'post',
                  )
                }
              >
                {copied === 'post' ? 'Copied!' : 'Copy URL + body'}
              </button>
            </div>
            <code className="webhook-url">{xeomaPostUrl}</code>
            <pre className="webhook-body">{xeomaPostBody}</pre>
          </div>

          <div className="status-row">
            <span>Last Xeoma / IP-camera event</span>
            <b>{relativeAgeISO(status?.lastIpCameraEventAt)}</b>
          </div>
          <div className="status-row">
            <span>Total Xeoma / IP-camera events</span>
            <b>{status?.ipCameraEventsTotal ?? 0}</b>
          </div>
          {status?.lastIpCameraSource && (
            <div className="muted small">
              Last source label: <code>{status.lastIpCameraSource}</code>
            </div>
          )}
          <div className="btn-row" style={{ marginTop: 8 }}>
            <button
              className="btn secondary btn-sm"
              onClick={() => window.open(xeomaGetUrl, '_blank', 'noopener')}
            >
              Test the URL in a new tab
            </button>
          </div>
        </div>
      </div>

      <h3 style={{ marginTop: 24 }}>Live FUSION alerts (camera × SIG)</h3>
      {fusionAlerts.length === 0 && (
        <p className="muted">
          No fusion yet. The cameras are watching — when both a camera
          detection and a SIG event land in the same zone within the fusion
          window, an alert appears here automatically.
        </p>
      )}
      <div className="alert-list">
        {fusionAlerts.map((a) => (
          <AlertRow key={a.id} alert={a} />
        ))}
      </div>

      <h3 style={{ marginTop: 20 }}>Other recent alerts</h3>
      <div className="alert-list">
        {otherAlerts.map((a) => (
          <AlertRow key={a.id} alert={a} />
        ))}
      </div>
    </>
  )
}
