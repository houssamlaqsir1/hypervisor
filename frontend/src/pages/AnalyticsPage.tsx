import { useEffect, useMemo, useState } from 'react'
import { downloadAlertsCsv, getAnalytics } from '../api/analytics'
import { extractApiError } from '../lib/apiError'
import type { Analytics, AlertSeverity, AlertStatus } from '../types/api'
import { useT } from '../lib/useT'

const WINDOWS = [7, 30, 90]

const SEVERITY_ORDER: AlertSeverity[] = ['CRITICAL', 'HIGH', 'MEDIUM', 'LOW']
const SEVERITY_COLOR: Record<AlertSeverity, string> = {
  CRITICAL: 'var(--critical)',
  HIGH: 'var(--danger)',
  MEDIUM: 'var(--warn)',
  LOW: '#94a3b8',
}

const STATUS_ORDER: AlertStatus[] = ['NEW', 'ACKNOWLEDGED', 'RESOLVED']
const STATUS_COLOR: Record<AlertStatus, string> = {
  NEW: 'var(--danger)',
  ACKNOWLEDGED: 'var(--warn)',
  RESOLVED: '#16a34a',
}
const STATUS_LABEL: Record<AlertStatus, string> = {
  NEW: 'New',
  ACKNOWLEDGED: 'Acknowledged',
  RESOLVED: 'Resolved',
}

/** Single-series area chart of alerts per day. No legend — the heading names it. */
function TimelineChart({ data }: { data: { date: string; count: number }[] }) {
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

  return (
    <svg viewBox={`0 0 ${W} ${H}`} className="analytics-timeline" role="img" aria-label="Alerts per day">
      {ticks.map((t) => (
        <g key={t}>
          <line x1={PAD.left} x2={W - PAD.right} y1={y(t)} y2={y(t)} className="chart-grid" />
          <text x={PAD.left - 6} y={y(t) + 3} className="chart-axis-label" textAnchor="end">{t}</text>
        </g>
      ))}
      <path d={areaPath} className="chart-area" />
      <path d={linePath} className="chart-line" />
      {data.map((d, i) => (
        <circle key={d.date} cx={x(i)} cy={y(d.count)} r={n > 45 ? 0 : 2.5} className="chart-dot">
          <title>{d.date}: {d.count} alert{d.count === 1 ? '' : 's'}</title>
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
      {rows.map((r) => (
        <div className="bar-row" key={r.label}>
          <span className="bar-label" title={r.label}>{r.label}</span>
          <span className="bar-track">
            <span className="bar-fill" style={{ width: `${(r.count / max) * 100}%`, background: r.color ?? color }} />
          </span>
          <span className="bar-value">{r.count}</span>
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
      .catch((e) => setError(extractApiError(e, 'Failed to load analytics')))
      .finally(() => setLoading(false))
  }, [days])

  const typeRows = useMemo(
    () =>
      data
        ? Object.entries(data.byType)
            .map(([label, count]) => ({ label: label.replace(/_/g, ' '), count: count ?? 0 }))
            .sort((a, b) => b.count - a.count)
        : [],
    [data],
  )

  const zoneRows = useMemo(() => data?.byZone.slice(0, 10) ?? [], [data])

  async function onExport() {
    setExporting(true)
    try {
      await downloadAlertsCsv(days)
    } catch (e) {
      setError(extractApiError(e, 'Export failed'))
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
          <div className="analytics-window">
            {WINDOWS.map((w) => (
              <button
                key={w}
                type="button"
                className={`btn btn-sm ${days === w ? '' : 'secondary'}`}
                onClick={() => setDays(w)}
              >
                {w}d
              </button>
            ))}
          </div>
          <button type="button" className="btn secondary btn-sm" onClick={onExport} disabled={exporting}>
            {exporting ? 'Exporting…' : '⬇ Export CSV'}
          </button>
        </div>
      </div>

      {error && <p className="login-error">{error}</p>}
      {loading && <p className="muted">Loading…</p>}

      {data && !loading && (
        <>
          <div className="analytics-kpis">
            <div className="card analytics-kpi">
              <h3>Total ({data.windowDays}d)</h3>
              <div className="value">{data.total}</div>
            </div>
            {SEVERITY_ORDER.map((s) => (
              <div className="card analytics-kpi" key={s}>
                <h3 style={{ color: SEVERITY_COLOR[s] }}>{s}</h3>
                <div className="value">{data.bySeverity[s] ?? 0}</div>
              </div>
            ))}
          </div>

          <div className="card" style={{ marginBottom: 20 }}>
            <h3 style={{ marginBottom: 12 }}>Alerts per day</h3>
            <TimelineChart data={data.timeline} />
          </div>

          <div className="analytics-grid">
            <div className="card">
              <h3 style={{ marginBottom: 14 }}>By type</h3>
              <BarList rows={typeRows} />
            </div>
            <div className="card">
              <h3 style={{ marginBottom: 14 }}>By zone (top 10)</h3>
              <BarList rows={zoneRows} />
            </div>
            <div className="card">
              <h3 style={{ marginBottom: 14 }}>By status</h3>
              <BarList
                rows={STATUS_ORDER.map((s) => ({
                  label: STATUS_LABEL[s],
                  count: data.byStatus[s] ?? 0,
                  color: STATUS_COLOR[s],
                }))}
              />
            </div>
            <div className="card">
              <h3 style={{ marginBottom: 14 }}>By severity</h3>
              <BarList
                rows={SEVERITY_ORDER.map((s) => ({
                  label: s,
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
