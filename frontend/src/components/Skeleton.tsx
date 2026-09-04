/**
 * Placeholders shaped like the content that is coming.
 *
 * A spinner tells an operator to wait. A skeleton tells them what they
 * are waiting for and, because it occupies the same space the real
 * content will, the page does not jump when the data lands — which on a
 * dashboard someone is already reaching towards is the difference
 * between clicking Resolve and clicking Delete.
 */

interface SkeletonProps {
  width?: string | number
  height?: string | number
  radius?: string
  className?: string
}

export function Skeleton({ width, height = 12, radius, className }: SkeletonProps) {
  return (
    <span
      className={`skeleton ${className ?? ''}`}
      style={{ display: 'block', width, height, borderRadius: radius }}
      aria-hidden="true"
    />
  )
}

/*
 * The placeholders below deliberately mirror the real components' current
 * dimensions — a 32px icon tile, a 22px figure, three lines of message.
 * That is the whole point of a skeleton: if it is not the size of what
 * replaces it, the page still jumps when the data lands, and it has done
 * nothing except make the wait prettier.
 */

/** The five counters across the top of the dashboard, before they load. */
export function StatRowSkeleton({ count = 5 }: { count?: number }) {
  return (
    <div className="dash-stat-row" aria-hidden="true">
      {Array.from({ length: count }, (_, i) => (
        <div key={i} className="dash-stat-card">
          <Skeleton width={32} height={32} radius="var(--r-sm)" />
          <div className="dash-stat-info" style={{ flex: 1, gap: 6 }}>
            <Skeleton width="52%" height={16} />
            <Skeleton width="74%" height={9} />
          </div>
        </div>
      ))}
    </div>
  )
}

/** Rows in the alert feed, before they load. */
export function AlertListSkeleton({ count = 5 }: { count?: number }) {
  return (
    <div className="alert-list" aria-hidden="true">
      {Array.from({ length: count }, (_, i) => (
        <div key={i} className="alert-row">
          <Skeleton width={58} height={17} radius="var(--r-xs)" />
          <div style={{ display: 'flex', flexDirection: 'column', gap: 7, minWidth: 0 }}>
            <Skeleton width="32%" height={12} />
            <Skeleton width="86%" height={11} />
            <Skeleton width="24%" height={11} />
          </div>
          <div style={{ display: 'flex', flexDirection: 'column', gap: 10, alignItems: 'flex-end' }}>
            <Skeleton width={150} height={17} />
            <Skeleton width={210} height={26} radius="var(--r-sm)" />
          </div>
        </div>
      ))}
    </div>
  )
}

/** A card-shaped placeholder, for the analytics panels. */
export function CardSkeleton({ height = 180 }: { height?: number }) {
  return (
    <div className="card" aria-hidden="true">
      <Skeleton width="30%" height={11} />
      <div style={{ marginTop: 14 }}>
        <Skeleton height={height} radius="var(--r-md)" />
      </div>
    </div>
  )
}

/** Body rows of an admin table, before they load. */
export function TableSkeleton({ rows = 4, columns = 5 }: { rows?: number; columns?: number }) {
  return (
    <div className="card table-card" aria-hidden="true">
      <div style={{ padding: '14px 16px' }}>
        {Array.from({ length: rows }, (_, r) => (
          <div
            key={r}
            style={
              {
                display: 'grid',
                gridTemplateColumns: `repeat(${columns}, minmax(0, 1fr))`,
                gap: 16,
                padding: '11px 0',
                borderBottom: r === rows - 1 ? 'none' : '1px solid var(--border)',
              } as React.CSSProperties
            }
          >
            {Array.from({ length: columns }, (_, c) => (
              <Skeleton key={c} height={11} width={c === 0 ? '62%' : '80%'} />
            ))}
          </div>
        ))}
      </div>
    </div>
  )
}
