/**
 * Which alerts the notification layer has already accounted for.
 *
 * Alerts reach the live list by two very different routes, and only one of
 * them is news:
 *
 * - the **WebSocket topic** pushes an alert the moment the correlation
 *   engine raises it — that is a real event and deserves a notification;
 * - a **page fetch** (`listAlerts`) loads recent history whenever the
 *   Dashboard or 3D Map mounts — those alerts are minutes or days old and
 *   the operator has already seen them.
 *
 * Both end up in the same array, so the notifier cannot tell them apart by
 * looking at it. Historical fetches were therefore being announced as if
 * they had just happened: open the app, work elsewhere for a minute, come
 * back to the Dashboard, and its fetch would fire a burst notification for
 * alerts that were already in the list — with the counters unchanged,
 * because nothing had actually arrived.
 *
 * This registry is the shared ledger that settles it. The fetch path marks
 * what it loads as seen *silently*; the notifier only announces ids that
 * are still unknown, which can now only be ones the WebSocket delivered.
 *
 * Module-level, so it survives component remounts, React StrictMode's
 * double-invoked effects and WebSocket re-subscribes. Bounded, so a long
 * shift can't grow it without limit.
 */

const SEEN_CAP = 2000

const seenIds = new Set<number>()
const seenOrder: number[] = []

function remember(id: number) {
  seenIds.add(id)
  seenOrder.push(id)
  if (seenOrder.length > SEEN_CAP) {
    const evicted = seenOrder.shift()
    if (evicted !== undefined) seenIds.delete(evicted)
  }
}

/**
 * Records an id and reports whether this is the first sighting — true means
 * "announce it". Used by the notification layer.
 */
export function markSeenOnce(id: number): boolean {
  if (seenIds.has(id)) return false
  remember(id)
  return true
}

/**
 * Records ids without treating them as news. Used by the history-fetch path
 * so loading the past never sounds like the present.
 */
export function markSeenSilently(ids: Iterable<number>) {
  for (const id of ids) {
    if (!seenIds.has(id)) remember(id)
  }
}

/** Test/debug helper — clears the ledger. */
export function resetSeenLog() {
  seenIds.clear()
  seenOrder.length = 0
}
