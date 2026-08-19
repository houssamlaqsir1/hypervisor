/**
 * The operator's own position, and the consent to use it.
 *
 * Browser geolocation has a rule that shapes this whole module: the
 * permission prompt appears **only** when the page actually asks for a
 * position, and the browser remembers the answer forever. So a settings
 * switch cannot "ask again" on every flip — it can only ask the first
 * time, and after that the browser's own site settings are the place to
 * change your mind. The status reported here says which of those
 * situations you are in, because "nothing happened when I turned it on"
 * is otherwise indistinguishable from a bug.
 *
 * Turning the switch on starts a live watch and keeps it running while
 * the app is open; turning it off stops the watch and drops the last fix.
 * Features that use location (the map's "my location", Live Watch's GPS
 * reporting) gate on {@link locationAllowed} so the switch genuinely
 * governs them rather than decorating the settings page.
 */

import { loadPrefs } from './prefs'

export type LocationStatus =
  | 'off'          // switched off by the operator
  | 'requesting'   // waiting on the browser prompt / first fix
  | 'active'       // tracking, position available
  | 'denied'       // the browser blocked it — only site settings can undo this
  | 'unavailable'  // no geolocation support, or the device can't get a fix

export interface OperatorLocation {
  status: LocationStatus
  latitude: number | null
  longitude: number | null
  accuracyM: number | null
  updatedAt: number | null
  message: string | null
}

const OFF: OperatorLocation = {
  status: 'off',
  latitude: null,
  longitude: null,
  accuracyM: null,
  updatedAt: null,
  message: null,
}

let state: OperatorLocation = { ...OFF }
let watchId: number | null = null
const listeners = new Set<(s: OperatorLocation) => void>()

function publish(next: Partial<OperatorLocation>) {
  state = { ...state, ...next }
  for (const listener of listeners) listener(state)
}

export function getLocation(): OperatorLocation {
  return state
}

export function subscribeLocation(listener: (s: OperatorLocation) => void): () => void {
  listeners.add(listener)
  listener(state)
  return () => listeners.delete(listener)
}

/** True when the operator has consented and a live fix is available. */
export function locationAllowed(): boolean {
  return loadPrefs().location_tracking && state.status === 'active'
}

/**
 * Starts tracking. The first call is what makes the browser show its
 * allow/block banner — there is no way to trigger it without asking for a
 * position, which is why this is called straight from the settings switch.
 */
export function startLocation() {
  if (watchId != null) return

  if (typeof navigator === 'undefined' || !('geolocation' in navigator)) {
    publish({ status: 'unavailable', message: 'This browser has no geolocation support.' })
    return
  }

  publish({ status: 'requesting', message: 'Waiting for permission…' })

  watchId = navigator.geolocation.watchPosition(
    (pos) => {
      publish({
        status: 'active',
        latitude: pos.coords.latitude,
        longitude: pos.coords.longitude,
        accuracyM: pos.coords.accuracy ?? null,
        updatedAt: Date.now(),
        message: null,
      })
    },
    (err) => {
      // PERMISSION_DENIED is worth separating: it is the one state the app
      // cannot recover from on its own, so the UI has to send the operator
      // to their browser settings instead of suggesting they try again.
      if (err.code === err.PERMISSION_DENIED) {
        publish({
          status: 'denied',
          latitude: null,
          longitude: null,
          message: 'Blocked in the browser. Re-allow it in the site permissions to use location.',
        })
      } else {
        publish({
          status: 'unavailable',
          message: err.message || 'Could not get a position fix.',
        })
      }
    },
    { enableHighAccuracy: true, maximumAge: 10_000, timeout: 20_000 },
  )
}

export function stopLocation() {
  if (watchId != null && typeof navigator !== 'undefined' && 'geolocation' in navigator) {
    navigator.geolocation.clearWatch(watchId)
  }
  watchId = null
  publish({ ...OFF })
}

/** Resumes tracking on page load when the operator already opted in. */
export function initLocationFromPrefs() {
  if (loadPrefs().location_tracking) startLocation()
}
