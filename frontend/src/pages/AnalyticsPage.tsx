import { useEffect, useMemo, useState, type CSSProperties } from 'react'
import { downloadAlertsCsv, getAnalytics } from '../api/analytics'
import { extractApiError } from '../lib/apiError'
import type { Analytics, AlertSeverity, AlertStatus } from '../types/api'
import { useT } from '../lib/useT'
import { IconAlertCircle, IconDownload } from '../components/icons'
import { AnimatedNumber } from '../components/AnimatedNumber'
import { CardSkeleton, StatRowSkeleton } from '../components/Skeleton'

const WINDOWS = [7, 30, 90]

const SEVERITY_ORDER: AlertSeverity[] = ['CRITICAL', 'HIGH', 'MEDIUM', 'LOW']
const SEVERITY_COLOR: Record<AlertSeverity, string> = {
  CRITICAL: 'var(--critical)',
  HIGH: 'var(--danger)',
  MEDIUM: 'var(--warn)',
  LOW: 'var(--neutral)',
}

const STATUS_ORDER: AlertStatus[] = ['NEW', 'ACKNOWLEDGED', 'RESOLVED']
const STATUS_COLOR: Record<AlertStatus, string> = {
  NEW: 'var(--danger)',
  ACKNOWLEDGED: 'var(--warn)',
  RESOLVED: 'var(--success)',
}

/** Single-series area chart of alerts per day. No legend — the heading names it. */
function TimelineChart({ data, label }: { data: { date: string; count: number }[]; label: string }) {
  const W = 760
  const H = 200
  const PAD = { top: 12, right: 12, bottom: 24, left: 30 }
  const plotW = W - PAD.left - PAD.right
  const plotH = H - PAD.top - PAD.bottom

  const max = Math.max(1, ...data.map((d) => d.count))
  const n = data.length

  const x = (i: number) => PAD.left + (n <= 1 ? plotW / 2 : (i / (n - 1)) * plotW)
  const y = (v: number) => PAD.top + plotH - (v / max) * plotH

  const linePath = data.map((d, i) => `${i === 0 ? 'M' : 'L'} ${x(i).toFixed(1)} ${y(d.count).toFixed(1)}`).join(' ')
  const areaPath =
    `M ${x(0).toFixed(1)} ${(PAD.top + plotH).toFixed(1)} ` +
    data.map((d, i) => `L ${x(i).toFixed(1)} ${y(d.count).toFixed(1)}`).join(' ') +
    ` L ${x(n - 1).toFixed(1)} ${(PAD.top + plotH).toFixed(1)} Z`

  // A few recessive y gridlines.
  const ticks = [0, 0.5, 1].map((f) => Math.round(max * f))
  const labelIdx = n <= 1 ? [0] : [0, Math.floor((n - 1) / 2), n - 1]

  /*
   * Length of the line, so the draw-in animation can dash exactly it.
   *
   * Measured rather than guessed: `stroke-dasharray` has to be at least
   * the true path length or the line reappears part-drawn, and a fixed
   * guess would be wrong for both a 7-day window and a 90-day one. Summed
   * from the segments here instead of read from `getTotalLength()`,
   * because that needs the element in the document and this runs during
   * render — one pass over data we already hold, versus a layout round
   * trip and an effect.
   */
  const pathLength = data.reduce((sum, d, i) => {
    if (i === 0) return 0
    const dx = x(i) - x(i - 1)
    const dy = y(d.count) - y(data[i - 1].count)
    return sum + Math.hypot(dx, dy)
  }, 0)

  return (
    <svg
      viewBox={`0 0 ${W} ${H}`}
      className="analytics-timeline"
      role="img"
      aria-label={label}
      style={{ '--dash': Math.ceil(pathLength) } as CSSProperties}
    >
      <defs>
        {/* The line runs through the brand gradient rather than a single
            hue, so the chart belongs to the same palette as the rail and
            the primary button. */}
        <linearGradient id="chart-stroke" x1="0" y1="0" x2="1" y2="0">
          <stop offset="0%" stopColor="var(--accent)" />
          <stop offset="100%" stopColor="var(--accent-2)" />
        </linearGradient>
        {/* The fill fades out downward: full tint against the line,
            nothing at the axis, so the area reads as depth under the
            series rather than as a solid block of colour. */}
        <linearGradient id="chart-fill" x1="0" y1="0" x2="0" y2="1">
          <stop offset="0%" stopColor="var(--accent)" stopOpacity="0.28" />
          <stop offset="100%" stopColor="var(--accent)" stopOpacity="0" />
        </linearGradient>
      </defs>

      {ticks.map((t) => (
        <g key={t}>
          <line x1={PAD.left} x2={W - PAD.right} y1={y(t)} y2={y(t)} className="chart-grid" />
          <text x={PAD.left - 6} y={y(t) + 3} className="chart-axis-label" textAnchor="end">{t}</text>
        </g>
      ))}
      <path d={areaPath} className="chart-area" />
      <path d={linePath} className="chart-line" />
      {data.map((d, i) => (
        <circle
          key={d.date}
          cx={x(i)}
          cy={y(d.count)}
          r={n > 45 ? 0 : 2.5}
          className="chart-dot"
          style={{ '--i': i } as CSSProperties}
        >
          <title>{d.date} · {d.count}</title>
        </circle>
      ))}
      {labelIdx.map((i) => (
        <text key={i} x={x(i)} y={H - 6} className="chart-axis-label" textAnchor="middle">
          {data[i]?.date.slice(5)}
        </text>
      ))}
    </svg>
  )
}

/** Horizontal bar list — magnitude by row; identity is the label, so one hue is fine. */
function BarList({
  rows,
  color = 'var(--accent)',
  empty = 'No data',
}: {
  rows: { label: string; count: number; color?: string }[]
  color?: string
  empty?: string
}) {
  const max = Math.max(1, ...rows.map((r) => r.count))
  if (rows.length === 0) return <p className="muted" style={{ margin: 0 }}>{empty}</p>
  return (
    <div className="bar-list">
      {rows.map((r, i) => (
        <div className="bar-row" key={r.label} style={{ '--i': i } as CSSProperties}>
          <span className="bar-label" title={r.label}>{r.label}</span>
          <span className="bar-track">
            <span
              className="bar-fill"
              style={
                {
                  width: `${(r.count / max) * 100}%`,
                  background: r.color ?? color,
                  '--i': i,
                } as CSSProperties
              }
            />
          </span>
          <AnimatedNumber className="bar-value" value={r.count} durationMs={600} />
        </div>
      ))}
    </div>
  )
}

export function AnalyticsPage() {
  const t = useT()
  const [days, setDays] = useState(30)
  const [data, setData] = useState<Analytics | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [exporting, setExporting] = useState(false)

  useEffect(() => {
    setLoading(true)
    getAnalytics(days)
      .then(setData)
      .catch((e) => setError(extractApiError(e, t('analytics.loadFailed'))))
      .finally(() => setLoading(false))
  }, [days, t])

  /*
   * The API returns the alert type as its enum name (OBJECT_ON_TRACK).
   * Underscores swapped for spaces still reads as a database column, and
   * stays English in the French interface — so each one goes through the
   * same translation table the alert feed already uses, and only falls
   * back to the de-underscored name for a type this build does not know.
   */
  const typeRows = useMemo(
    () =>
      data
        ? Object.entries(data.byType)
            .map(([type, count]) => {
              const label = t(`alertType.${type}`)
              return {
                label: label === `alertType.${type}` ? type.replace(/_/g, ' ') : label,
                count: count ?? 0,
              }
            })
            .sort((a, b) => b.count - a.count)
        : [],
    [data, t],
  )

  const zoneRows = useMemo(() => data?.byZone.slice(0, 10) ?? [], [data])

  async function onExport() {
    setExporting(true)
    try {
      await downloadAlertsCsv(days)
    } catch (e) {
      setError(extractApiError(e, t('analytics.exportFailed')))
    } finally {
      setExporting(false)
    }
  }

  return (
    <>
      <div className="page-header">
        <div>
          <h2>{t('analytics.title')}</h2>
          <p>{t('analytics.subtitle')}</p>
        </div>
        <div className="analytics-toolbar">
          {/*
            One enclosure with one segment lit, rather than three loose
            buttons: these are three settings of a single dial, and three
            separate buttons left the operator to infer that from spacing.
          */}
          <div className="segmented" role="group" aria-label={t('analytics.window')}>
            {WINDOWS.map((w) => (
              <button
                key={w}
                type="button"
                aria-pressed={days === w}
                onClick={() => setDays(w)}
              >
                {t('analytics.days', { days: w })}
              </button>
            ))}
          </div>
          <button type="button" className="btn secondary btn-sm" onClick={onExport} disabled={exporting}>
            <IconDownload size={14} />
            {exporting ? t('analytics.exporting') : t('analytics.export')}
          </button>
        </div>
      </div>

      {error && (
        <p className="login-error" role="alert">
          <IconAlertCircle size={15} />
          <span>{error}</span>
        </p>
      )}
      {/* Placeholders shaped like the panels that are coming, so switching
          window (7d → 90d) does not collapse the page and push the charts
          up under the pointer before they redraw. */}
      {loading && (
        <>
          <StatRowSkeleton />
          <CardSkeleton height={176} />
        </>
      )}

      {data && !loading && (
        <>
          <div className="analytics-kpis">
            <div className="card analytics-kpi" style={{ '--i': 0 } as CSSProperties}>
              <h3>{t('analytics.totalWindow', { days: data.windowDays })}</h3>
              <AnimatedNumber className="value" value={data.total} />
            </div>
            {SEVERITY_ORDER.map((s, i) => (
              <div
                className="card analytics-kpi"
                key={s}
                style={{ '--i': i + 1 } as CSSProperties}
              >
                <h3 style={{ color: SEVERITY_COLOR[s] }}>{t(`severity.${s}`)}</h3>
                <AnimatedNumber className="value" value={data.bySeverity[s] ?? 0} />
              </div>
            ))}
          </div>

          {/*
            The chart is keyed on the window so that changing it remounts
            the SVG and replays the draw-in. Without the key React reuses
            the same paths, the `d` attribute swaps, and the new series
            appears fully formed with no indication anything changed.
          */}
          <div className="card" style={{ marginBottom: 14, '--i': 6 } as CSSProperties}>
            <h3>{t('analytics.perDay')}</h3>
            <TimelineChart
              key={data.windowDays}
              data={data.timeline}
              label={t('analytics.perDay')}
            />
          </div>

          <div className="analytics-grid">
            <div className="card">
              <h3>{t('analytics.byType')}</h3>
              <BarList rows={typeRows} empty={t('analytics.noData')} />
            </div>
            <div className="card">
              <h3>{t('analytics.byZone')}</h3>
              <BarList rows={zoneRows} empty={t('analytics.noData')} />
            </div>
            <div className="card">
              <h3>{t('analytics.byStatus')}</h3>
              <BarList
                empty={t('analytics.noData')}
                rows={STATUS_ORDER.map((s) => ({
                  label: t(`status.${s}`),
                  count: data.byStatus[s] ?? 0,
                  color: STATUS_COLOR[s],
                }))}
              />
            </div>
            <div className="card">
              <h3>{t('analytics.bySeverity')}</h3>
              <BarList
                empty={t('analytics.noData')}
                rows={SEVERITY_ORDER.map((s) => ({
                  label: t(`severity.${s}`),
                  count: data.bySeverity[s] ?? 0,
                  color: SEVERITY_COLOR[s],
                }))}
              />
            </div>
          </div>
        </>
      )}
    </>
  )
}
