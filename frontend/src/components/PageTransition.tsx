import type { ReactNode } from 'react'
import { useLocation } from 'react-router-dom'

/**
 * Fades and lifts each page in as the route changes.
 *
 * The whole mechanism is the `key`. Keying the wrapper on the pathname
 * makes React discard the previous page's element and mount a fresh one,
 * which restarts the CSS animation; without it the animation would play
 * once on first load and never again, because the element would simply
 * be reused with new children.
 *
 * It is an entrance only — there is no exit animation, because an exit
 * has to hold the outgoing page on screen while it plays, and that puts
 * a delay between an operator clicking a page and seeing it. On a
 * surveillance console the arriving page should be there immediately;
 * the movement is there to say *this is a different page now*, not to
 * make anyone wait for it.
 */
export function PageTransition({ children }: { children: ReactNode }) {
  const { pathname } = useLocation()
  return (
    <div key={pathname} className="page-enter">
      {children}
    </div>
  )
}
