/**
 * Motion helpers shared by the animated components.
 *
 * The CSS in index.css carries almost all of the console's animation;
 * what lives here is the handful of things a stylesheet cannot do —
 * counting a number up, and reading the viewer's motion preference from
 * JavaScript so a component can skip an animation rather than merely
 * shorten it.
 */

import { useEffect, useRef, useState } from 'react'

/**
 * Whether this viewer has asked their system for reduced motion.
 *
 * The stylesheet already honours the preference for everything it draws.
 * This is for the animations React drives frame by frame — a count-up is
 * a JavaScript loop, and no media query can stop it. Live, not read once
 * at boot: the setting can be changed while the console is open, and on
 * a screen somebody watches for a whole shift, that matters.
 */
export function usePrefersReducedMotion(): boolean {
  const [reduced, setReduced] = useState(() => {
    if (typeof window === 'undefined' || !window.matchMedia) return false
    return window.matchMedia('(prefers-reduced-motion: reduce)').matches
  })

  useEffect(() => {
    if (typeof window === 'undefined' || !window.matchMedia) return
    const query = window.matchMedia('(prefers-reduced-motion: reduce)')
    const onChange = () => setReduced(query.matches)
    query.addEventListener('change', onChange)
    return () => query.removeEventListener('change', onChange)
  }, [])

  return reduced
}

/**
 * Animates a number towards `target`, returning the value for this frame.
 *
 * Two properties matter on this console:
 *
 *  - It counts *from wherever it currently is*, not from zero. When a
 *    live alert pushes the critical count from 29 to 30, the tile ticks
 *    one step; it does not drop to zero and race back up, which would
 *    read as the figure having been lost and recomputed.
 *  - It always lands exactly on the target. The easing is applied to
 *    progress through the duration rather than to the distance
 *    remaining, so the last frame is the true value — an asymptotic
 *    approach can leave a counter reading 29 forever.
 *
 * Under reduced motion the target is adopted immediately.
 */
export function useCountUp(target: number, durationMs = 750): number {
  const reduced = usePrefersReducedMotion()
  const [animated, setAnimated] = useState(target)
  const fromRef = useRef(target)
  const frameRef = useRef(0)

  useEffect(() => {
    // Under reduced motion nothing is animated, but the ref still has to
    // follow the target: if the viewer turns the preference off later,
    // the next count must start from the figure actually on screen and
    // not from whatever it was when the preference was turned on.
    if (reduced) {
      fromRef.current = target
      return
    }

    const from = fromRef.current
    if (from === target) return

    const distance = target - from
    const start = performance.now()

    const tick = (now: number) => {
      const progress = Math.min(1, (now - start) / durationMs)
      // Quintic ease-out: quick off the mark, a long settle. At these
      // distances anything gentler looks like the number is stuck.
      const eased = 1 - Math.pow(1 - progress, 5)
      const next = Math.round(from + distance * eased)
      fromRef.current = next
      setAnimated(next)
      if (progress < 1) frameRef.current = requestAnimationFrame(tick)
    }

    frameRef.current = requestAnimationFrame(tick)
    return () => cancelAnimationFrame(frameRef.current)
  }, [target, durationMs, reduced])

  // Derived during render rather than written into state by the effect
  // above: setting state from an effect costs a second render pass on
  // every change, and here there is nothing to store — the answer under
  // reduced motion is simply the target.
  return reduced ? target : animated
}
