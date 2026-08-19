import { api } from './client'
import { getToken } from './token'
import type { Analytics } from '../types/api'

export function getAnalytics(days: number): Promise<Analytics> {
  return api.get<Analytics>(`/analytics/summary?days=${days}`)
}

const BASE = import.meta.env.VITE_API_BASE ?? '/api'

/**
 * Downloads the CSV export. Uses fetch directly (not a plain link) so the
 * JWT is sent — the endpoint is auth-protected — then triggers a browser
 * download from the returned blob.
 */
export async function downloadAlertsCsv(days: number): Promise<void> {
  const token = getToken()
  const res = await fetch(`${BASE}/analytics/export.csv?days=${days}`, {
    headers: token ? { Authorization: `Bearer ${token}` } : undefined,
  })
  if (!res.ok) throw new Error(`Export failed: ${res.status}`)
  const blob = await res.blob()
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = `alerts-last-${days}-days.csv`
  document.body.appendChild(a)
  a.click()
  a.remove()
  URL.revokeObjectURL(url)
}
