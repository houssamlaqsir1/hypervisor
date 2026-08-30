/**
 * Buffer of per-frame detection boxes, for drawing over the video preview.
 *
 * Two things shape this module.
 *
 * **It is not React state.** Frames arrive at the detector's inference rate
 * — a couple per second, per camera, indefinitely. Putting that in a context
 * would re-render every subscriber twice a second for a canvas that is drawn
 * imperatively anyway. So frames land in a plain module-level ring buffer and
 * the canvas reads from it inside its own animation loop.
 *
 * **Frames are held, not shown immediately.** The preview is HLS, which runs
 * seconds behind live, while these boxes describe the moment they were
 * measured. Drawing the newest frame would put boxes where people *are* on
 * top of video showing where they *were* — visibly wrong as soon as anyone
 * moves. {@link framesAt} instead looks up the frame matching the moment
 * currently on screen, which the caller derives from the player's own
 * reported latency.
 */

export interface DetectionBox {
  label: string
  confidence: number
  x: number
  y: number
  w: number
  h: number
  /** Fraction of the box on the rails, or null if this camera has no track polygon. */
  trackOverlap: number | null
}

export interface DetectionFrame {
  cameraId: string
  capturedAt: string
  frameWidth: number
  frameHeight: number
  detections: DetectionBox[]
  /** Parsed once on arrival — `framesAt` runs on every animation frame. */
  capturedAtMs: number
}

/**
 * Roughly fifteen seconds at two frames per second, which comfortably spans
 * any plausible HLS delay while staying small enough to scan linearly.
 */
const BUFFER_SIZE = 30

/** Beyond this gap the nearest frame is too stale to be the one on screen. */
const MAX_MATCH_GAP_MS = 2_000

const buffers = new Map<string, DetectionFrame[]>()

export function pushFrame(raw: Omit<DetectionFrame, 'capturedAtMs'>) {
  const capturedAtMs = Date.parse(raw.capturedAt)
  if (!Number.isFinite(capturedAtMs)) return

  const frame: DetectionFrame = { ...raw, capturedAtMs }
  const buffer = buffers.get(raw.cameraId) ?? []
  buffer.push(frame)
  if (buffer.length > BUFFER_SIZE) buffer.shift()
  buffers.set(raw.cameraId, buffer)
}

/**
 * The frame closest to `atMs`, or null if nothing in the buffer is near
 * enough to be what the viewer is currently looking at.
 *
 * Returning null rather than the newest frame is deliberate: with no
 * detections arriving, stale boxes frozen on screen would misrepresent a
 * live feed as still tracking something.
 */
export function framesAt(cameraId: string, atMs: number): DetectionFrame | null {
  const buffer = buffers.get(cameraId)
  if (!buffer || buffer.length === 0) return null

  let best: DetectionFrame | null = null
  let bestGap = Number.POSITIVE_INFINITY
  for (const frame of buffer) {
    const gap = Math.abs(frame.capturedAtMs - atMs)
    if (gap < bestGap) {
      bestGap = gap
      best = frame
    }
  }
  return bestGap <= MAX_MATCH_GAP_MS ? best : null
}

/** Forgets a camera's history — used when its stream stops. */
export function clearFrames(cameraId: string) {
  buffers.delete(cameraId)
}
