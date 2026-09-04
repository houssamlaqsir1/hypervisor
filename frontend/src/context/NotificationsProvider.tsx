import { useEffect, useRef, type ReactNode } from 'react'
import { useNavigate } from 'react-router-dom'
import { Toaster, toast } from 'sonner'
import { useLiveAlertsContext } from './LiveAlertsContext'
import type { Alert, AlertSeverity } from '../types/api'
import { loadPrefs } from '../lib/prefs'
import { markSeenOnce } from '../lib/alertSeenLog'
import { t } from '../lib/i18n'
import { IconX } from '../components/icons'

/**
 * Turns the live alert stream into user-facing notifications: an in-app
 * toast + an OS/browser notification (both under the "Push notifications"
 * setting) and a beep ("Alert sounds"). "Critical only" suppresses anything
 * below HIGH.
 *
 * <p>The hard part isn't showing a notification — it's <b>not</b> showing
 * fifty. A camera watching a busy platform legitimately produces a stream of
 * distinct alerts, and naively announcing each one makes the console
 * unusable. Four independent guards, layered the way monitoring systems
 * (PagerDuty, Grafana) do it:
 *
 * <ol>
 *   <li><b>Per-alert dedup</b> — a shared bounded ledger
 *   ({@code lib/alertSeenLog}) means one alert id can never notify twice,
 *   even across StrictMode double-effects, WebSocket re-subscribes or a
 *   provider remount. The history-fetch path writes to the same ledger
 *   silently, so loading the past never sounds like the present.</li>
 *   <li><b>Signature suppression</b> — alerts of the same kind
 *   (type + severity + zone) collapse into one notification per minute, so
 *   a person lingering on a platform doesn't re-announce endlessly.</li>
 *   <li><b>Burst coalescing</b> — if several distinct alerts land at once,
 *   they become a single "N new alerts" summary rather than a wall of
 *   toasts.</li>
 *   <li><b>Sound throttle</b> — at most one beep every few seconds however
 *   many alerts arrive.</li>
 * </ol>
 *
 * <p>A warm-up window means the batch of historical alerts loaded right
 * after login is recorded silently. Toast rendering (positioning, stacking,
 * animation) is delegated to sonner; this component owns the domain logic.
 */

const WARMUP_MS = 3000
const TOAST_TTL_MS = 7000
const MAX_VISIBLE_TOASTS = 3
const NOTIFYABLE: AlertSeverity[] = ['HIGH', 'CRITICAL']

/** Same kind of alert (type+severity+zone) won't re-announce within this window. */
const SIGNATURE_SUPPRESS_MS = 60_000
/** Never beep more often than this, however many alerts arrive. */
const SOUND_MIN_GAP_MS = 3_000
/** More than this many alerts in one batch collapse into a single summary toast. */
const BURST_SUMMARY_THRESHOLD = 3

/** Last time each alert signature was announced. */
const lastAnnouncedBySignature = new Map<string, number>()
let lastSoundAt = 0

/** Alerts sharing a signature are "the same kind of thing happening again". */
function signatureOf(alert: Alert): string {
  return `${alert.type}|${alert.severity}|${alert.zoneId ?? '-'}`
}

interface NotifPrefs {
  /** Popup *and* sound — one operator decision, see lib/prefs.ts. */
  announce: boolean
  highAndCriticalOnly: boolean
}

function readPrefs(): NotifPrefs {
  const p = loadPrefs()
  return { announce: p.notifications, highAndCriticalOnly: p.notif_high_critical_only }
}

function beep() {
  const now = Date.now()
  if (now - lastSoundAt < SOUND_MIN_GAP_MS) return
  lastSoundAt = now
  try {
    const Ctx =
      window.AudioContext ||
      (window as unknown as { webkitAudioContext: typeof AudioContext }).webkitAudioContext
    const ctx = new Ctx()
    const osc = ctx.createOscillator()
    const gain = ctx.createGain()
    osc.type = 'sine'
    osc.frequency.value = 880
    osc.connect(gain)
    gain.connect(ctx.destination)
    const t = ctx.currentTime
    gain.gain.setValueAtTime(0.0001, t)
    gain.gain.exponentialRampToValueAtTime(0.15, t + 0.02)
    gain.gain.exponentialRampToValueAtTime(0.0001, t + 0.35)
    osc.start(t)
    osc.stop(t + 0.36)
    osc.onended = () => void ctx.close()
  } catch {
    /* audio unavailable — ignore */
  }
}

export function NotificationsProvider({ children }: { children: ReactNode }) {
  const { alerts } = useLiveAlertsContext()
  const navigate = useNavigate()
  const startedAt = useRef<number>(Date.now())

  useEffect(() => {
    const prefs = readPrefs()
    const warmedUp = Date.now() - startedAt.current > WARMUP_MS
    const now = Date.now()

    // 1. Per-alert dedup — only ids nobody has recorded yet, which after
    //    the fetch path marks its own means only WebSocket arrivals.
    const fresh: Alert[] = []
    for (const alert of alerts) {
      if (!markSeenOnce(alert.id)) continue
      if (!warmedUp) continue // historical / seed load — recorded, not announced
      if (prefs.highAndCriticalOnly && !NOTIFYABLE.includes(alert.severity)) continue
      fresh.push(alert)
    }
    if (fresh.length === 0) return

    // 2. Signature suppression — collapse repeats of the same kind of alert.
    const announceable = fresh.filter((alert) => {
      const sig = signatureOf(alert)
      const last = lastAnnouncedBySignature.get(sig) ?? 0
      if (now - last < SIGNATURE_SUPPRESS_MS) return false
      lastAnnouncedBySignature.set(sig, now)
      return true
    })
    if (announceable.length === 0) return

    if (prefs.announce) {
      // 3. Burst coalescing — one summary beats a wall of toasts.
      if (announceable.length > BURST_SUMMARY_THRESHOLD) {
        pushSummaryToast(announceable, navigate)
        fireOsNotification(
          `${announceable.length} new alerts`,
          announceable
            .slice(0, 3)
            .map((a) => `${a.severity}: ${a.type.replace(/_/g, ' ')}`)
            .join('\n'),
          `alert-burst-${announceable[0].id}`,
          () => navigate('/'),
        )
      } else {
        for (const alert of announceable) {
          pushToast(alert, navigate)
          fireOsNotification(
            `${alert.severity} · ${alert.type.replace(/_/g, ' ')}`,
            alert.message,
            `alert-${alert.id}`,
            () => goToAlert(alert, navigate),
          )
        }
      }
    }
    // 4. Sound throttle lives inside beep().
    if (prefs.announce) beep()
  }, [alerts, navigate])

  return (
    <>
      {children}
      {/* sonner renders/positions/animates the toasts; unstyled so our own
          .toast classes fully control the look and match the app's theme. */}
      <Toaster position="top-right" visibleToasts={MAX_VISIBLE_TOASTS} toastOptions={{ unstyled: true }} />
    </>
  )
}

type Navigate = ReturnType<typeof useNavigate>

function goToAlert(alert: Alert, navigate: Navigate) {
  if (alert.latitude != null && alert.longitude != null) {
    navigate(`/map3d?lat=${alert.latitude}&lon=${alert.longitude}`)
  }
}

/** Highest severity in a group, for colouring the summary toast. */
function topSeverity(alerts: Alert[]): AlertSeverity {
  const rank: Record<AlertSeverity, number> = { LOW: 1, MEDIUM: 2, HIGH: 3, CRITICAL: 4 }
  return alerts.reduce<AlertSeverity>(
    (worst, a) => (rank[a.severity] > rank[worst] ? a.severity : worst),
    'LOW',
  )
}

function pushToast(alert: Alert, navigate: Navigate) {
  const toastId = `alert-${alert.id}`
  toast.custom(
    () => (
      <div
        className={`toast sev-${alert.severity}`}
        role="alert"
        onClick={() => {
          goToAlert(alert, navigate)
          toast.dismiss(toastId)
        }}
      >
        <span className="toast-sev">{t(`severity.${alert.severity}`)}</span>
        <div className="toast-body">
          <strong>{t(`alertType.${alert.type}`)}</strong>
          <small>{alert.message}</small>
        </div>
        <button
          type="button"
          className="toast-close"
          onClick={(e) => {
            e.stopPropagation()
            toast.dismiss(toastId)
          }}
          aria-label={t('common.dismiss')}
        >
          <IconX size={13} />
        </button>
      </div>
    ),
    // Stable id → sonner collapses any repeat call onto the same toast.
    { id: toastId, duration: TOAST_TTL_MS },
  )
}

function pushSummaryToast(alerts: Alert[], navigate: Navigate) {
  const toastId = `alert-burst-${alerts[0].id}`
  const severity = topSeverity(alerts)
  toast.custom(
    () => (
      <div
        className={`toast sev-${severity}`}
        role="alert"
        onClick={() => {
          navigate('/')
          toast.dismiss(toastId)
        }}
      >
        <span className="toast-sev">{alerts.length}×</span>
        <div className="toast-body">
          <strong>{t('toast.newAlerts', { count: alerts.length })}</strong>
          <small>
            {t('toast.highest', {
              severity: t(`severity.${severity}`),
              type: t(`alertType.${alerts[0].type}`),
            })}
          </small>
        </div>
        <button
          type="button"
          className="toast-close"
          onClick={(e) => {
            e.stopPropagation()
            toast.dismiss(toastId)
          }}
          aria-label={t('common.dismiss')}
        >
          <IconX size={13} />
        </button>
      </div>
    ),
    { id: toastId, duration: TOAST_TTL_MS },
  )
}

function fireOsNotification(title: string, body: string, tag: string, onClick: () => void) {
  if (typeof Notification === 'undefined' || Notification.permission !== 'granted') return
  try {
    const n = new Notification(title, { body, tag }) // same tag → OS replaces rather than stacks
    n.onclick = () => {
      window.focus()
      onClick()
      n.close()
    }
  } catch {
    /* Notification construction can throw on some platforms — ignore */
  }
}
