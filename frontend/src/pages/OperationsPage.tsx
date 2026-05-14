import { useEffect, useState } from 'react'
import { listZones } from '../api/zones'
import type { Alert, Zone } from '../types/api'
import {
  receiveCameraEvent,
  receiveSigEvent,
} from '../api/operations'
import { AlertRow } from '../components/AlertRow'

export function OperationsPage() {
  const [zones, setZones] = useState<Zone[]>([])
  const [zoneId, setZoneId] = useState<number | ''>('')
  const [busy, setBusy] = useState(false)
  const [log, setLog] = useState<Array<{ id: string; text: string; alerts: Alert[] }>>([])

  useEffect(() => {
    listZones().then((zs) => {
      setZones(zs)
      if (zs.length) setZoneId(zs[0].id)
    })
  }, [])

  async function act(label: string, fn: () => Promise<Alert[]>) {
    setBusy(true)
    try {
      const alerts = await fn()
      setLog((prev) => [
        { id: crypto.randomUUID(), text: label, alerts },
        ...prev,
      ])
    } catch (e) {
      setLog((prev) => [
        { id: crypto.randomUUID(), text: `${label} FAILED: ${String(e)}`, alerts: [] },
        ...prev,
      ])
    } finally {
      setBusy(false)
    }
  }

  const payload = zoneId ? { zoneId: Number(zoneId) } : {}

  return (
    <>
      <div className="page-header">
        <div>
          <h2>Operations console</h2>
          <p>
            Pull events from AI cameras and SIG sensors into the correlation
            pipeline. Use it for live drills, on-call tests, or to forward
            signals from sources that aren&apos;t auto-integrated yet.
          </p>
        </div>
      </div>

      <div className="card" style={{ marginBottom: 20 }}>
        <div className="form-row">
          <label>Target zone</label>
          <select
            value={zoneId}
            onChange={(e) => setZoneId(e.target.value ? Number(e.target.value) : '')}
          >
            <option value="">(random)</option>
            {zones.map((z) => (
              <option key={z.id} value={z.id}>
                {z.name} — {z.type}
              </option>
            ))}
          </select>
        </div>

        <div className="btn-row">
          <button
            className="btn"
            disabled={busy}
            onClick={() =>
              act('AI camera event received', async () =>
                (await receiveCameraEvent(payload)).alerts,
              )
            }
          >
            Receive AI camera event
          </button>
          <button
            className="btn secondary"
            disabled={busy}
            onClick={() =>
              act('SIG event received', async () =>
                (await receiveSigEvent(payload)).alerts,
              )
            }
          >
            Receive SIG event
          </button>
        </div>
      </div>

      <h3>Activity log</h3>
      {log.length === 0 && <p className="muted">No events received yet.</p>}
      <div className="alert-list">
        {log.map((entry) => (
          <div key={entry.id} className="card">
            <b>{entry.text}</b>
            <div className="muted" style={{ margin: '6px 0' }}>
              {entry.alerts.length} alert(s) generated
            </div>
            {entry.alerts.map((a) => (
              <AlertRow key={a.id} alert={a} />
            ))}
          </div>
        ))}
      </div>
    </>
  )
}
