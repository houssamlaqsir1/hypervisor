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
import type { Translate } from '../lib/i18n'
import { framesAt } from '../lib/detectionFeed'
import { DetectionList } from '../components/DetectionList'
import { IconLive } from '../components/icons'

type GpsState = 'idle' | 'starting' | 'running' | 'error'

const STATUS_POLL_MS = 5000

/** The word on a camera's state pill. */
function statusLabel(c: CameraRuntime, t: Translate): string {
  if (!c.enabled) return t('live.state.disabled')
  switch (c.status) {
    case 'running':
      return t('live.state.live')
    case 'starting':
      return t('live.state.connecting')
    case 'idle':
      return t('live.state.idle')
    case 'error':
      return t('live.state.error')
  }
}

function relativeAgeMs(ts: number | null, t: Translate): string {
  if (ts == null) return t('age.never')
  const diff = Math.max(0, Math.round((Date.now() - ts) / 1000))
  if (diff < 5) return t('age.justNow')
  if (diff < 60) return t('age.seconds', { n: diff })
  if (diff < 3600) return t('age.minutes', { n: Math.round(diff / 60) })
  return t('age.hours', { n: Math.round(diff / 3600) })
}

function relativeAgeISO(iso: string | null | undefined, t: Translate): string {
  if (!iso) return t('age.never')
  return relativeAgeMs(new Date(iso).getTime(), t)
}

export function LiveWatchPage() {
  const t = useT()
  const {
    cameras,
    zones,
    setCameraEnabled,
    getVideoElement,
    getLatencySec,
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

  /*
   * Detection overlay.
   *
   * Detection itself runs server-side in the YOLOv8 service, so the browser
   * has no boxes of its own to draw. The detector instead publishes each
   * analysed frame's boxes to `/topic/detections`, which the live-alerts
   * socket buffers into `detectionFeed`; this loop reads from that buffer.
   *
   * Two details make the boxes actually land on the right pixels:
   *
   *  - the preview video is `object-fit: cover`, so it is scaled to fill and
   *    cropped. Drawing in raw frame coordinates would put every box off by
   *    the crop, so the same cover transform is recomputed here.
   *  - HLS runs seconds behind live. The newest boxes describe a moment the
   *    viewer has not reached, so the frame is chosen by the player's own
   *    reported latency instead — boxes then sit on the people they were
   *    measured from rather than racing ahead of them.
   */
  useEffect(() => {
    const cam = selectedCamera
    const canvas = overlayCanvasRef.current
    if (!cam || !canvas) return

    const ctx = canvas.getContext('2d')
    if (!ctx) return

    let raf = 0
    const draw = () => {
      raf = requestAnimationFrame(draw)

      // Match the canvas bitmap to its displayed size, so one canvas unit is
      // one CSS pixel and the cover maths below stays in one coordinate space.
      const dpr = window.devicePixelRatio || 1
      const cssW = canvas.clientWidth
      const cssH = canvas.clientHeight
      if (cssW === 0 || cssH === 0) return
      if (canvas.width !== Math.round(cssW * dpr) || canvas.height !== Math.round(cssH * dpr)) {
        canvas.width = Math.round(cssW * dpr)
        canvas.height = Math.round(cssH * dpr)
      }
      ctx.setTransform(dpr, 0, 0, dpr, 0, 0)
      ctx.clearRect(0, 0, cssW, cssH)

      if (cam.status !== 'running') return

      const shownAtMs = Date.now() - getLatencySec(cam.key) * 1000
      const frame = framesAt(cam.id, shownAtMs)
      if (!frame || frame.frameWidth === 0 || frame.frameHeight === 0) return

      // Replicate object-fit: cover — scale to fill, centre, let the
      // overflow fall outside the box.
      const scale = Math.max(cssW / frame.frameWidth, cssH / frame.frameHeight)
      const offsetX = (cssW - frame.frameWidth * scale) / 2
      const offsetY = (cssH - frame.frameHeight * scale) / 2

      ctx.lineWidth = 2
      ctx.font = '600 12px system-ui, sans-serif'
      ctx.textBaseline = 'bottom'

      for (const box of frame.detections) {
        const x = offsetX + box.x * scale
        const y = offsetY + box.y * scale
        const w = box.w * scale
        const h = box.h * scale

        // Colour carries the same meaning as the alert severity it would
        // produce: red once the object is squarely on the rails, amber while
        // it is only clipping them, neutral when it is clear of the track.
        const overlap = box.trackOverlap
        const colour =
          overlap != null && overlap >= 0.5 ? '#ef4444'
            : overlap != null && overlap >= 0.02 ? '#f59e0b'
              : '#3b82f6'

        ctx.strokeStyle = colour
        ctx.strokeRect(x, y, w, h)

        const caption = `${box.label} ${Math.round(box.confidence * 100)}%`
        const textW = ctx.measureText(caption).width
        ctx.fillStyle = colour
        ctx.fillRect(x, Math.max(0, y - 16), textW + 8, 16)
        ctx.fillStyle = '#0b1220'
        ctx.fillText(caption, x + 4, Math.max(14, y - 2))
      }
    }

    raf = requestAnimationFrame(draw)
    return () => cancelAnimationFrame(raf)
  }, [selectedCamera, getLatencySec])

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
      setGpsError(t('live.gpsOff'))
      setGpsState('error')
      return
    }
    if (!('geolocation' in navigator)) {
      setGpsError(t('live.gpsUnsupported'))
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
        setGpsError(err.message || t('live.gpsError'))
        setGpsState('error')
      },
      { enableHighAccuracy: true, maximumAge: 5_000, timeout: 15_000 },
    )
  }, [gpsState, selectedGpsZone, useDeviceGps, t])

  useEffect(() => () => stopGps(), [stopGps])

  /* ----------------------------- render ----------------------------- */

  return (
    <>
      <div className="page-header">
        <div>
          <h2>{t('live.title')}</h2>
          <p>{t('live.subtitle')}</p>
        </div>
        {/*
          The two running totals belong opposite the title, not under it:
          under a subtitle three lines long they read as a fourth line of
          prose, when they are the page's live numbers.
        */}
        <div className="page-actions">
          <span className={`pill ${activeCount > 0 ? 'pill-running' : 'pill-idle'}`}>
            {t('live.camerasLive', { live: activeCount, total: cameras.length })}
          </span>
          <span className="muted small tabular">
            {t('live.ingested', { count: status?.webcamEventsTotal ?? 0 })}
          </span>
        </div>
      </div>

      <div className="live-grid">
        <div className="card live-card">
          <div className="live-card-header">
            <h3>{t('live.camera')}</h3>
            <span className={`pill pill-${selectedCamera?.status ?? 'idle'}`}>
              {selectedCamera
                ? `${selectedCamera.label.split('—')[0].trim()} · ${statusLabel(selectedCamera, t)}`
                : t('live.noCamera')}
            </span>
          </div>

          <div className="form-row" style={{ marginBottom: 12 }}>
            <label htmlFor="live-preview-camera">{t('live.preview')}</label>
            <select
              id="live-preview-camera"
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
                <IconLive size={26} />
                <span>
                  {!selectedCamera.enabled && t('live.cameraOff')}
                  {selectedCamera.enabled && selectedCamera.status === 'idle' &&
                    t('live.waitingStream')}
                  {selectedCamera.enabled && selectedCamera.status === 'starting' &&
                    t('live.connecting')}
                  {selectedCamera.enabled && selectedCamera.status === 'error' &&
                    (selectedCamera.error ?? t('live.streamError'))}
                </span>
              </div>
            )}
          </div>

          {/* Directly under the stream, and in its own block rather than
              nested in the muted caption below: what the camera can see
              right now belongs beside the picture, and nesting it in that
              caption would inherit the dimmed, shrunken text and read as
              part of the grey footnote rather than as live data. */}
          {selectedCamera && (
            <DetectionList
              cameraId={selectedCamera.id}
              cameraKey={selectedCamera.key}
              active={selectedCamera.status === 'running'}
              getLatencySec={getLatencySec}
            />
          )}

          {selectedCamera && (
            <div className="muted small" style={{ marginTop: 10 }}>
              <div className="tabular">
                {t('live.ingested', { count: status?.webcamEventsTotal ?? 0 })} &middot;{' '}
                {t('live.lastDetection', {
                  when: relativeAgeMs(
                    status?.lastWebcamEventAt
                      ? Date.parse(status.lastWebcamEventAt)
                      : null,
                    t,
                  ),
                })}
              </div>
              <div>{t('live.analysedBy')}</div>
            </div>
          )}
        </div>

        <div className="card live-card">
          <div className="live-card-header">
            <h3>{t('live.cameras')}</h3>
            <span className={`pill ${activeCount > 0 ? 'pill-running' : 'pill-idle'}`}>
              {t('live.camerasLive', { live: activeCount, total: cameras.length })}
            </span>
          </div>
          <p className="muted small" style={{ margin: '0 0 12px' }}>
            {t('live.camerasHelp')}
          </p>

          <div className="live-camera-list">
            {cameras.map((c) => (
              <div key={c.key} className="live-camera-row">
                <div className="live-camera-row-head">
                  <span className={`pill pill-${c.enabled ? c.status : 'disabled'}`}>
                    {statusLabel(c, t)}
                  </span>
                  <span className="live-camera-row-name">{c.label}</span>
                </div>
                <div className="live-camera-row-stats muted small">
                  <span>
                    {t('live.id')} <b className="tabular">{c.id}</b>
                  </span>
                  {c.error && <span style={{ color: 'var(--danger)' }}>{c.error}</span>}
                </div>
                <div className="live-camera-row-controls">
                  <label className="live-camera-toggle">
                    <input
                      type="checkbox"
                      checked={c.enabled}
                      onChange={(e) => setCameraEnabled(c.key, e.target.checked)}
                    />
                    {t('live.enabled')}
                  </label>
                  <span className="muted small" title={t('live.fixedLocationTitle')}>
                    {t('live.fixedLocation')}
                  </span>
                </div>
              </div>
            ))}
          </div>
        </div>

        <div className="card live-card">
          <div className="live-card-header">
            <h3>{t('live.sig')}</h3>
            <span className={`pill pill-${gpsState}`}>
              {t(`live.state.${gpsState === 'starting' ? 'connecting' : gpsState === 'running' ? 'live' : gpsState}`)}
            </span>
          </div>

          <p className="muted small" style={{ margin: '0 0 10px' }}>
            {t('live.openSky')}
          </p>
          <div className="status-row">
            <span>{t('live.lastPoll')}</span>
            <b>{relativeAgeISO(status?.lastOpenSkyPollAt, t)}</b>
          </div>
          <div className="status-row">
            <span>{t('live.tracksIngested')}</span>
            <b>{status?.openSkyEventsTotal ?? 0}</b>
          </div>
          {status?.lastOpenSkyError && (
            <div className="muted small" style={{ color: 'var(--warn)', marginTop: 8 }}>
              {t('live.openSkyError', { message: status.lastOpenSkyError })}
            </div>
          )}

          <hr className="divider" />

          <div className="form-row">
            <label htmlFor="gps-zone">{t('live.gpsZone')}</label>
            <select
              id="gps-zone"
              value={gpsZoneId}
              onChange={(e) => setGpsZoneId(e.target.value ? Number(e.target.value) : '')}
            >
              <option value="">{t('live.gpsRealOnly')}</option>
              {zones.map((z) => (
                <option key={z.id} value={z.id}>
                  {z.name} — {t(`zoneType.${z.type}`)}
                </option>
              ))}
            </select>
          </div>
          {/* Not a .form-row: that grid reserves a label column, and a
              checkbox whose label is the whole control ends up with the box
              stranded in it, above its own text. */}
          <label className="live-camera-toggle" style={{ marginTop: 12 }}>
            <input
              type="checkbox"
              checked={useDeviceGps}
              onChange={(e) => setUseDeviceGps(e.target.checked)}
            />
            {t('live.gpsUseDevice')}
          </label>
          <p className="muted small" style={{ margin: '10px 0' }}>
            {t('live.gpsHelp')}
          </p>
          <div className="btn-row">
            {gpsState !== 'running' ? (
              <button type="button" className="btn secondary btn-sm" onClick={startGps}>
                {t('live.gpsStart')}
              </button>
            ) : (
              <button type="button" className="btn danger btn-sm" onClick={stopGps}>
                {t('live.gpsStop')}
              </button>
            )}
          </div>
          {gpsError && (
            <div className="muted small" style={{ color: 'var(--danger)', marginTop: 8 }}>
              {gpsError}
            </div>
          )}

          <hr className="divider" />

          <div className="status-row">
            <span>{t('live.gpsPosted')}</span>
            <b>{gpsPostedCount}</b>
          </div>
          <div className="status-row">
            <span>{t('live.totalCameraEvents')}</span>
            <b>{status?.webcamEventsTotal ?? 0}</b>
          </div>
          <div className="status-row">
            <span>{t('live.totalGpsEvents')}</span>
            <b>{status?.gpsEventsTotal ?? 0}</b>
          </div>
        </div>
      </div>
    </>
  )
}
