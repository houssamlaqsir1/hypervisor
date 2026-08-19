import Hls from 'hls.js'
import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useRef,
  useState,
  type ReactNode,
} from 'react'
import type { Zone } from '../types/api'
import { listZones } from '../api/zones'

/* ------------------------------------------------------------------ */
/*  Camera registry                                                   */
/* ------------------------------------------------------------------ */

export type CameraKind = 'hls'

/**
 * A single AI camera the hypervisor watches. {@code id} is what gets
 * stored on every {@code CameraEvent} the backend persists — change it and
 * you orphan the historical data, so treat it as stable.
 */
export interface CameraConfig {
  key: string
  id: string
  label: string
  kind: CameraKind
  hlsUrl: string
}

function buildDefaultCameras(): CameraConfig[] {
  const hlsUrl =
    typeof window !== 'undefined'
      ? `${window.location.protocol}//${window.location.hostname}:8888/iphone/index.m3u8`
      : 'http://localhost:8888/iphone/index.m3u8'
  return [
    {
      key: 'cam-1',
      id: 'CAM-LIVE-1',
      label: 'Camera 1 — iPhone (Larix)',
      kind: 'hls',
      hlsUrl,
    },
  ]
}

export const CAMERAS: CameraConfig[] = buildDefaultCameras()

/* ------------------------------------------------------------------ */
/*  Runtime types                                                     */
/* ------------------------------------------------------------------ */

export type CameraStatus = 'idle' | 'starting' | 'running' | 'error'

export interface CameraRuntime extends CameraConfig {
  status: CameraStatus
  error: string | null
  /** Whether the operator wants this camera live; persisted in localStorage. */
  enabled: boolean
}

interface LiveCamerasContextValue {
  cameras: CameraRuntime[]
  zones: Zone[]
  setCameraEnabled: (cameraKey: string, enabled: boolean) => void
  /**
   * Hidden video element the provider drives. Use this to mirror the feed
   * in the operator preview pane via {@code captureStream()}.
   */
  getVideoElement: (cameraKey: string) => HTMLVideoElement | null
}

const LiveCamerasContext = createContext<LiveCamerasContextValue | null>(null)

/* ------------------------------------------------------------------ */
/*  Provider                                                          */
/* ------------------------------------------------------------------ */

/**
 * Owns the operator-facing video feeds only.
 *
 * <p>AI detection deliberately does <b>not</b> happen here. It runs
 * server-side in the YOLOv8 detector service, which reads the same
 * MediaMTX stream directly and posts events to the backend. That matters
 * for two reasons: YOLOv8 is substantially more accurate than the
 * in-browser COCO-SSD model this replaced, and detection no longer stops
 * the moment an operator closes the browser tab — which is the only
 * acceptable behaviour for a surveillance system.
 *
 * <p>So this provider's whole job is: keep the HLS streams alive, expose
 * their status, and hand out the video elements for preview.
 */

const ENABLED_KEY = 'hypervisor:live-cameras:enabled'

function loadEnabledMap(): Record<string, boolean> {
  if (typeof window === 'undefined') return {}
  try {
    const raw = window.localStorage.getItem(ENABLED_KEY)
    if (!raw) return {}
    const parsed = JSON.parse(raw) as Record<string, boolean>
    return parsed ?? {}
  } catch {
    return {}
  }
}

interface ProviderProps {
  children: ReactNode
}

export function LiveCamerasProvider({ children }: ProviderProps) {
  /** Refs are the source of truth for video DOM nodes (rendered hidden below). */
  const videoRefs = useRef<Record<string, HTMLVideoElement | null>>({})
  const hlsRefs = useRef<Record<string, Hls | null>>({})
  /** Forward-declaration ref so HLS retry handlers can call startCamera. */
  const startCameraRef = useRef<(key: string) => Promise<void>>(
    async () => undefined,
  )

  const [zones, setZones] = useState<Zone[]>([])
  const enabledInitial = useMemo(() => {
    const stored = loadEnabledMap()
    return Object.fromEntries(
      CAMERAS.map((c) => [c.key, stored[c.key] ?? true]),
    ) as Record<string, boolean>
  }, [])
  const [runtimes, setRuntimes] = useState<Record<string, CameraRuntime>>(
    () => {
      const out: Record<string, CameraRuntime> = {}
      for (const c of CAMERAS) {
        out[c.key] = {
          ...c,
          status: 'idle',
          error: null,
          enabled: enabledInitial[c.key],
        }
      }
      return out
    },
  )

  /** Keep a ref to runtimes for callbacks that run outside React's flow. */
  const runtimesRef = useRef(runtimes)
  useEffect(() => {
    runtimesRef.current = runtimes
  }, [runtimes])

  /** Patch one camera's runtime — async-safe (works from outside React). */
  const updateCamera = useCallback(
    (key: string, patch: Partial<CameraRuntime>) => {
      setRuntimes((prev) => {
        const current = prev[key]
        if (!current) return prev
        return { ...prev, [key]: { ...current, ...patch } }
      })
    },
    [],
  )

  /* ----------------------------- zones ----------------------------- */

  useEffect(() => {
    let cancelled = false
    listZones()
      .then((zs) => {
        if (!cancelled) setZones(zs)
      })
      .catch((e) => console.warn('LiveCameras: zones fetch failed', e))
    return () => {
      cancelled = true
    }
  }, [])

  /* ----------------------- stream lifecycle ------------------------ */

  /**
   * Start the underlying media source for a camera. Idempotent — calling
   * twice while already running is a no-op.
   */
  const startCamera = useCallback(
    async (key: string) => {
      const cam = runtimesRef.current[key]
      if (!cam) return
      if (cam.status === 'starting' || cam.status === 'running') return
      const videoEl = videoRefs.current[key]
      if (!videoEl) return

      updateCamera(key, { status: 'starting', error: null })

      try {
        const url = cam.hlsUrl
        if (!url) throw new Error('Camera has no HLS URL configured.')

        if (Hls.isSupported()) {
          const hls = new Hls({
            enableWorker: false,
            lowLatencyMode: true,
            manifestLoadingTimeOut: 25_000,
            fragLoadingTimeOut: 25_000,
          })
          hlsRefs.current[key] = hls
          hls.loadSource(url)
          hls.attachMedia(videoEl)
          hls.on(Hls.Events.MANIFEST_PARSED, () => {
            void videoEl.play().catch(() => {
              /* autoplay policy — the preview pane handles playback itself */
            })
            updateCamera(key, { status: 'running' })
          })
          hls.on(Hls.Events.ERROR, (_, data) => {
            if (!data.fatal) return
            const details =
              typeof data.details === 'string' ? `: ${data.details}` : ''
            const msg = `HLS ${data.type}${details}. Check MediaMTX / Larix.`
            hls.destroy()
            hlsRefs.current[key] = null
            updateCamera(key, { status: 'error', error: msg })
            // Auto-retry HLS after a delay — phones drop in and out.
            window.setTimeout(() => {
              if (runtimesRef.current[key]?.enabled) {
                updateCamera(key, { status: 'idle' })
                void startCameraRef.current(key)
              }
            }, 15_000)
          })
        } else if (videoEl.canPlayType('application/vnd.apple.mpegurl')) {
          videoEl.src = url
          videoEl.onloadeddata = () => updateCamera(key, { status: 'running' })
          videoEl.onerror = () => {
            updateCamera(key, {
              status: 'error',
              error: 'Native HLS failed — retrying…',
            })
            // Same auto-retry as the hls.js branch above — without this, a
            // dropped native-HLS stream (Safari) never recovers on its own
            // and the preview stays dead until the page is reloaded.
            window.setTimeout(() => {
              if (runtimesRef.current[key]?.enabled) {
                updateCamera(key, { status: 'idle' })
                void startCameraRef.current(key)
              }
            }, 15_000)
          }
          void videoEl.play().catch(() => {
            /* autoplay blocked */
          })
        } else {
          throw new Error('HLS is not supported in this browser.')
        }
      } catch (e) {
        console.error(`LiveCameras: failed to start ${key}`, e)
        updateCamera(key, {
          status: 'error',
          error: e instanceof Error ? e.message : String(e),
        })
      }
    },
    [updateCamera],
  )

  // Keep the forward-declaration ref in sync so retry callbacks always
  // call the latest startCamera closure.
  useEffect(() => {
    startCameraRef.current = startCamera
  }, [startCamera])

  const stopCamera = useCallback(
    (key: string) => {
      const hls = hlsRefs.current[key]
      if (hls) {
        hls.destroy()
        hlsRefs.current[key] = null
      }
      const video = videoRefs.current[key]
      if (video) {
        video.pause()
        video.removeAttribute('src')
        video.srcObject = null
        video.onloadeddata = null
        video.onerror = null
        video.load()
      }
      updateCamera(key, { status: 'idle', error: null })
    },
    [updateCamera],
  )

  /* ------------------------ enable / disable ----------------------- */

  const persistEnabled = useCallback((map: Record<string, boolean>) => {
    try {
      window.localStorage.setItem(ENABLED_KEY, JSON.stringify(map))
    } catch {
      /* private mode — ignore */
    }
  }, [])

  const setCameraEnabled = useCallback(
    (cameraKey: string, enabled: boolean) => {
      setRuntimes((prev) => {
        const cur = prev[cameraKey]
        if (!cur) return prev
        const next = { ...prev, [cameraKey]: { ...cur, enabled } }
        persistEnabled(
          Object.fromEntries(
            Object.values(next).map((c) => [c.key, c.enabled]),
          ),
        )
        return next
      })
      if (enabled) {
        void startCamera(cameraKey)
      } else {
        stopCamera(cameraKey)
      }
    },
    [persistEnabled, startCamera, stopCamera],
  )

  /* -------------------------- auto-start --------------------------- */

  useEffect(() => {
    // Start every enabled camera once the videos are in the DOM.
    for (const c of CAMERAS) {
      if (runtimesRef.current[c.key]?.enabled) {
        void startCamera(c.key)
      }
    }
    return () => {
      for (const c of CAMERAS) stopCamera(c.key)
    }
    // We intentionally run this once on mount — start/stop are stable refs.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  /* --------------------------- context ----------------------------- */

  const getVideoElement = useCallback(
    (cameraKey: string) => videoRefs.current[cameraKey] ?? null,
    [],
  )

  const camerasList = useMemo(
    () => CAMERAS.map((c) => runtimes[c.key]).filter(Boolean),
    [runtimes],
  )

  const value = useMemo<LiveCamerasContextValue>(
    () => ({
      cameras: camerasList,
      zones,
      setCameraEnabled,
      getVideoElement,
    }),
    [camerasList, zones, setCameraEnabled, getVideoElement],
  )

  return (
    <LiveCamerasContext.Provider value={value}>
      {children}
      {/*
        Hidden DOM-attached videos the provider drives, so the preview pane
        can mirror them from any route. Width/height = 1px so hls.js stays
        healthy (browsers sometimes pause truly-detached or 0-size videos).
      */}
      <div
        aria-hidden
        style={{
          position: 'fixed',
          left: 0,
          top: 0,
          width: 1,
          height: 1,
          overflow: 'hidden',
          opacity: 0,
          pointerEvents: 'none',
          zIndex: -1,
        }}
      >
        {CAMERAS.map((c) => (
          <video
            key={c.key}
            ref={(el) => {
              videoRefs.current[c.key] = el
            }}
            muted
            playsInline
            // autoPlay handled imperatively so HLS can start cleanly.
          />
        ))}
      </div>
    </LiveCamerasContext.Provider>
  )
}

/* ------------------------------------------------------------------ */
/*  Hook                                                              */
/* ------------------------------------------------------------------ */

export function useLiveCameras(): LiveCamerasContextValue {
  const ctx = useContext(LiveCamerasContext)
  if (!ctx) {
    throw new Error(
      'useLiveCameras must be used inside a LiveCamerasProvider (wrap your app).',
    )
  }
  return ctx
}
