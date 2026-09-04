import { useCountUp } from '../lib/motion'

interface Props {
  value: number
  /** How long the count takes. Bigger figures get a little longer. */
  durationMs?: number
  className?: string
}

/**
 * A figure that counts to its value rather than snapping to it.
 *
 * The point is not decoration: on a console where the numbers change by
 * themselves, a digit that simply swaps is easy to miss, while one that
 * travels catches the eye and says *this just changed*. The counter is
 * announced politely to screen readers via the surrounding tile, and the
 * element carries the settled value in `aria-label` so assistive
 * technology never reads out the intermediate frames.
 */
export function AnimatedNumber({ value, durationMs = 750, className }: Props) {
  const shown = useCountUp(value, durationMs)
  return (
    <span className={className} aria-label={String(value)}>
      <span aria-hidden="true">{shown.toLocaleString()}</span>
    </span>
  )
}
