import { useEffect, useState } from 'react'
import { framesAt, type DetectionBox } from '../lib/detectionFeed'
import { useT } from '../lib/useT'

interface Props {
  /** Registry id the detector labels its frames with (e.g. CAM-LIVE-1). */
  cameraId: string
  /** Runtime key used to ask the player how far behind live it is. */
  cameraKey: string
  /** False while the stream is idle, connecting or errored. */
  active: boolean
  getLatencySec: (cameraKey: string) => number
}

/** How often to look at the buffer. Fast enough to feel live, slow enough
 *  not to re-render a text list on every animation frame. */
const POLL_MS = 300

/**
 * What the detector can see right now, written out one object per line.
 *
 * The boxes drawn over the video say *where* things are; this says *what*
 * they are, in a form that can be read at a glance and does not depend on
 * catching a rectangle as it passes. It restores the running read-out the
 * in-browser COCO-SSD detector used to print, now sourced from the
 * server-side YOLOv8 service.
 *
 * It reads the same buffered feed as the canvas overlay, and picks the
 * frame by the player's own latency for the same reason: the list must
 * describe the picture the operator is looking at, not a moment the video
 * has not reached yet.
 *
 * Rendering is guarded by a signature so the component only re-renders when
 * the text would actually change. Without it, a stationary scene would
 * re-render three times a second producing identical output, because the
 * underlying boxes jitter by a pixel or two even when nothing moves.
 */
export function DetectionList({ cameraId, cameraKey, active, getLatencySec }: Props) {
  const t = useT()
  const [boxes, setBoxes] = useState<DetectionBox[]>([])

  useEffect(() => {
    if (!active) {
      setBoxes([])
      return
    }
    const id = window.setInterval(() => {
      const frame = framesAt(cameraId, Date.now() - getLatencySec(cameraKey) * 1000)
      const next = frame?.detections ?? []
      setBoxes((prev) => (signature(prev) === signature(next) ? prev : next))
    }, POLL_MS)
    return () => window.clearInterval(id)
  }, [cameraId, cameraKey, active, getLatencySec])

  if (!active) return null

  return (
    <div className="detection-list">
      <span className="detection-list-title">{t('live.inView')}</span>
      {boxes.length === 0 ? (
        <span className="detection-list-empty">{t('live.nothingDetected')}</span>
      ) : (
        <ul>
          {boxes.map((box, i) => {
            const fouling = box.trackOverlap != null && box.trackOverlap >= 0.02
            return (
              <li key={`${box.label}-${i}`} className={fouling ? 'is-fouling' : ''}>
                <span className="detection-label">
                  {t(`object.${box.label}`)}
                </span>
                <span className="detection-confidence">
                  {Math.round(box.confidence * 100)}%
                </span>
                {box.trackOverlap != null && box.trackOverlap >= 0.02 && (
                  <span className="detection-track">
                    {t('live.onRails', { pct: Math.round(box.trackOverlap * 100) })}
                  </span>
                )}
              </li>
            )
          })}
        </ul>
      )}
    </div>
  )
}

/**
 * Identity of a list *as displayed*. Deliberately excludes position: the
 * text only shows label, rounded confidence and rounded overlap, so a box
 * drifting across the frame should not count as a change.
 */
function signature(boxes: DetectionBox[]): string {
  return boxes
    .map((b) =>
      `${b.label}:${Math.round(b.confidence * 100)}:${
        b.trackOverlap == null ? '-' : Math.round(b.trackOverlap * 100)
      }`,
    )
    .join('|')
}
