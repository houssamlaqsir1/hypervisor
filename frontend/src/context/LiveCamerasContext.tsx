import Hls from 'hls.js'
import * as cocoSsd from '@tensorflow-models/coco-ssd'
// Side-effect import — registers the WebGL backend coco-ssd uses.
import '@tensorflow/tfjs'
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
import type { CameraEventType, Zone } from '../types/api'
import { listZones } from '../api/zones'
import { pushWebcamEvent } from '../api/live'

/* ------------------------------------------------------------------ */
/*  Camera registry                                                   */
/* ------------------------------------------------------------------ */

export type CameraKind = 'pc-webcam' | 'phone-hls'

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
  /** Only populated for phone-hls cameras. */
  hlsUrl?: string
}

function buildDefaultCameras(): CameraConfig[] {
  const phoneHlsUrl =
    typeof window !== 'undefined'
      ? `${window.location.protocol}//${window.location.hostname}:8888/iphone/index.m3u8`
      : 'http://localhost:8888/iphone/index.m3u8'
  return [
    {
      key: 'cam-1',
      id: 'CAM-LIVE-1',
      label: 'Camera 1 — PC webcam (browser AI)',
      kind: 'pc-webcam',
    },
    {
      key: 'cam-2',
      id: 'PHONE-XEOMA',
      label: 'Camera 2 — iPhone (Larix / Xeoma)',
      kind: 'phone-hls',
      hlsUrl: phoneHlsUrl,
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
  /** Zone the operator has bound this camera to (events are pinned to its center). */
  zoneId: number | ''
  /** Whether the operator wants this camera live; persisted in localStorage. */
  enabled: boolean
  lastDetectionAt: number | null
  postedCount: number
  lastDetections: cocoSsd.DetectedObject[]
}

interface LiveCamerasContextValue {
  cameras: CameraRuntime[]
  zones: Zone[]
  setCameraZoneId: (cameraKey: string, zoneId: number | '') => void
  setCameraEnabled: (cameraKey: string, enabled: boolean) => void
  /**
   * Hidden video element the provider drives. Use this to mirror the feed
   * in the operator preview pane via {@code captureStream()}.
   */
  getVideoElement: (cameraKey: string) => HTMLVideoElement | null
}

const LiveCamerasContext = createContext<LiveCamerasContextValue | null>(null)

/* ------------------------------------------------------------------ */
/*  AI detection config                                               */
/* ------------------------------------------------------------------ */

/** Total detection ticks per second, shared across all running cameras. */
const TOTAL_DETECTION_FPS = 2
/** Don't spam the backend with the same class from the same camera. */
const POST_COOLDOWN_MS = 4_000
const MIN_CONFIDENCE = 0.55

/** COCO-SSD classes worth correlating against. Everything else gets dropped. */
const RELEVANT_CLASSES: Record<
  string,
  { label: string; type: CameraEventType }
> = {
  person: { label: 'person', type: 'HUMAN_DETECTED' },
  bicycle: { label: 'bicycle', type: 'OBJECT_DETECTED' },
  car: { label: 'car', type: 'OBJECT_DETECTED' },
  motorcycle: { label: 'motorcycle', type: 'OBJECT_DETECTED' },
  bus: { label: 'bus', type: 'OBJECT_DETECTED' },
  truck: { label: 'truck', type: 'OBJECT_DETECTED' },
  train: { label: 'train', type: 'OBJECT_DETECTED' },
  backpack: { label: 'backpack', type: 'OBJECT_DETECTED' },
  handbag: { label: 'handbag', type: 'OBJECT_DETECTED' },
  suitcase: { label: 'suitcase', type: 'OBJECT_DETECTED' },
  dog: { label: 'dog', type: 'OBJECT_DETECTED' },
}

/** Casablanca city center — used when no zone is bound and geolocation fails. */
const FALLBACK_COORDS: [number, number] = [33.5731, -7.5898]

/* ------------------------------------------------------------------ */
/*  Provider                                                          */
/* ------------------------------------------------------------------ */

const ENABLED_KEY = 'hypervisor:live-cameras:enabled'
const ZONE_KEY = 'hypervisor:live-cameras:zone'

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

function loadZoneMap(): Record<string, number | ''> {
  if (typeof window === 'undefined') return {}
  try {
    const raw = window.localStorage.getItem(ZONE_KEY)
    if (!raw) return {}
    const parsed = JSON.parse(raw) as Record<string, number | ''>
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
  const streamRefs = useRef<Record<string, MediaStream | null>>({})
  /** Per-camera detection bookkeeping — refs to avoid render churn. */
  const lastPostedRef = useRef<Record<string, Map<string, number>>>({})
  const roundRobinIdxRef = useRef<number>(0)
  const detectIntervalRef = useRef<number | null>(null)
  const detectingRef = useRef<boolean>(false)
  const modelRef = useRef<cocoSsd.ObjectDetection | null>(null)
  const modelLoadingRef = useRef<Promise<cocoSsd.ObjectDetection> | null>(null)
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
  const zoneInitial = useMemo(() => {
    const stored = loadZoneMap()
    return Object.fromEntries(
      CAMERAS.map((c) => [c.key, stored[c.key] ?? '']),
    ) as Record<string, number | ''>
  }, [])

  const [runtimes, setRuntimes] = useState<Record<string, CameraRuntime>>(
    () => {
      const out: Record<string, CameraRuntime> = {}
      for (const c of CAMERAS) {
        out[c.key] = {
          ...c,
          status: 'idle',
          error: null,
          zoneId: zoneInitial[c.key],
          enabled: enabledInitial[c.key],
          lastDetectionAt: null,
          postedCount: 0,
          lastDetections: [],
        }
      }
      return out
    },
  )

  /** Keep a ref to runtimes for the detection loop (which runs outside React). */
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
        if (cancelled) return
        setZones(zs)
        // If no zone is bound and we have one, default to the first.
        setRuntimes((prev) => {
          if (zs.length === 0) return prev
          let next = prev
          for (const c of CAMERAS) {
            if (next[c.key]?.zoneId === '' && zoneInitial[c.key] === '') {
              if (next === prev) next = { ...prev }
              next[c.key] = { ...next[c.key], zoneId: zs[0].id }
            }
          }
          return next
        })
      })
      .catch((e) => console.warn('LiveCameras: zones fetch failed', e))
    return () => {
      cancelled = true
    }
  }, [zoneInitial])

  /* --------------------------- model load -------------------------- */

  const ensureModel = useCallback(async (): Promise<cocoSsd.ObjectDetection> => {
    if (modelRef.current) return modelRef.current
    if (modelLoadingRef.current) return modelLoadingRef.current
    modelLoadingRef.current = cocoSsd
      .load({ base: 'lite_mobilenet_v2' })
      .then((m) => {
        modelRef.current = m
        return m
      })
      .catch((e) => {
        modelLoadingRef.current = null
        throw e
      })
    return modelLoadingRef.current
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
        if (cam.kind === 'pc-webcam') {
          if (!window.isSecureContext) {
            throw new Error(
              'Webcam requires a secure context (HTTPS or localhost).',
            )
          }
          const stream = await navigator.mediaDevices.getUserMedia({
            video: { width: 640, height: 480, facingMode: 'environment' },
            audio: false,
          })
          streamRefs.current[key] = stream
          videoEl.srcObject = stream
          await videoEl.play().catch(() => {
            /* autoplay denied is fine; coco-ssd reads frames either way */
          })
          updateCamera(key, { status: 'running' })
        } else if (cam.kind === 'phone-hls') {
          const url = cam.hlsUrl
          if (!url) throw new Error('Phone camera has no HLS URL configured.')

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
                /* autoplay policy — irrelevant for headless detection */
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
            videoEl.onerror = () =>
              updateCamera(key, {
                status: 'error',
                error: 'Native HLS failed — try a Chromium browser.',
              })
            void videoEl.play().catch(() => {
              /* autoplay blocked */
            })
          } else {
            throw new Error('HLS is not supported in this browser.')
          }
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
      const stream = streamRefs.current[key]
      if (stream) {
        stream.getTracks().forEach((t) => t.stop())
        streamRefs.current[key] = null
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
      updateCamera(key, { status: 'idle', error: null, lastDetections: [] })
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

  const persistZones = useCallback((map: Record<string, number | ''>) => {
    try {
      window.localStorage.setItem(ZONE_KEY, JSON.stringify(map))
    } catch {
      /* ignore */
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

  const setCameraZoneId = useCallback(
    (cameraKey: string, zoneId: number | '') => {
      setRuntimes((prev) => {
        const cur = prev[cameraKey]
        if (!cur) return prev
        const next = { ...prev, [cameraKey]: { ...cur, zoneId } }
        persistZones(
          Object.fromEntries(
            Object.values(next).map((c) => [c.key, c.zoneId]),
          ),
        )
        return next
      })
    },
    [persistZones],
  )

  /* -------------------------- auto-start --------------------------- */

  useEffect(() => {
    // Load the model in the background so the first detection isn't delayed.
    void ensureModel().catch((e) =>
      console.warn('LiveCameras: model load failed', e),
    )
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

  /* ------------------------ detection loop ------------------------- */

  const resolveCameraCoords = useCallback(
    (cam: CameraRuntime): [number, number] => {
      if (cam.zoneId !== '') {
        const z = zones.find((z) => z.id === cam.zoneId)
        if (z) return [z.centerLat, z.centerLon]
      }
      return FALLBACK_COORDS
    },
    [zones],
  )

  const detectTick = useCallback(async () => {
    if (detectingRef.current) return
    const running = Object.values(runtimesRef.current).filter(
      (c) => c.status === 'running' && c.enabled,
    )
    if (running.length === 0) return
    const model = modelRef.current
    if (!model) return

    detectingRef.current = true
    try {
      const idx = roundRobinIdxRef.current % running.length
      roundRobinIdxRef.current = (idx + 1) % running.length
      const cam = running[idx]
      const video = videoRefs.current[cam.key]
      if (!video || video.readyState < 2) return
      if (!video.videoWidth || !video.videoHeight) return

      let detections: cocoSsd.DetectedObject[] = []
      try {
        detections = await model.detect(video, 6)
      } catch (e) {
        console.warn(`LiveCameras: detect failed for ${cam.key}`, e)
        return
      }

      updateCamera(cam.key, {
        lastDetections: detections,
        lastDetectionAt: detections.length > 0 ? Date.now() : cam.lastDetectionAt,
      })

      // Forward relevant detections to the backend.
      const now = Date.now()
      const camMap =
        lastPostedRef.current[cam.key] ?? (lastPostedRef.current[cam.key] = new Map())
      for (const d of detections) {
        const known = RELEVANT_CLASSES[d.class]
        if (!known) continue
        if (d.score < MIN_CONFIDENCE) continue
        const lastSent = camMap.get(d.class) ?? 0
        if (now - lastSent < POST_COOLDOWN_MS) continue
        camMap.set(d.class, now)

        const [lat, lon] = resolveCameraCoords(cam)
        try {
          await pushWebcamEvent({
            cameraId: cam.id,
            eventType: known.type,
            label: known.label,
            confidence: Number(d.score.toFixed(3)),
            latitude: lat,
            longitude: lon,
            elevationM: 0,
            rawPayload: {
              source: cam.kind === 'phone-hls' ? 'phone_hls' : 'browser_webcam',
              model: 'coco-ssd',
              bbox: d.bbox,
              class: d.class,
              cameraKey: cam.key,
              userAgent: navigator.userAgent,
            },
          })
          updateCamera(cam.key, {
            postedCount: (runtimesRef.current[cam.key]?.postedCount ?? 0) + 1,
          })
        } catch (err) {
          console.warn(`LiveCameras: push failed for ${cam.key}`, err)
        }
      }
    } finally {
      detectingRef.current = false
    }
  }, [resolveCameraCoords, updateCamera])

  useEffect(() => {
    if (detectIntervalRef.current != null) {
      window.clearInterval(detectIntervalRef.current)
    }
    detectIntervalRef.current = window.setInterval(
      () => void detectTick(),
      Math.max(50, Math.round(1000 / TOTAL_DETECTION_FPS)),
    )
    return () => {
      if (detectIntervalRef.current != null) {
        window.clearInterval(detectIntervalRef.current)
        detectIntervalRef.current = null
      }
    }
  }, [detectTick])

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
      setCameraZoneId,
      setCameraEnabled,
      getVideoElement,
    }),
    [camerasList, zones, setCameraZoneId, setCameraEnabled, getVideoElement],
  )

  return (
    <LiveCamerasContext.Provider value={value}>
      {children}
      {/*
        Hidden DOM-attached videos the provider drives. They keep playing
        and feeding detection even when the operator is on another route.
        Width/height = 1px so getUserMedia / hls.js stay healthy (browsers
        sometimes pause truly-detached or 0-size videos).
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
            // autoPlay handled imperatively so HLS / getUserMedia can start cleanly.
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
