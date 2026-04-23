import { api } from './client'
import type { Alert, AlertStats } from '../types/api'

export function listAlerts(params: {
  severity?: string
  since?: string
  limit?: number
} = {}): Promise<Alert[]> {
  const query = new URLSearchParams()
  if (params.severity) query.set('severity', params.severity)
  if (params.since) query.set('since', params.since)
  if (params.limit) query.set('limit', String(params.limit))
  const qs = query.toString()
  return api.get<Alert[]>(`/alerts${qs ? `?${qs}` : ''}`)
}

export function getAlertStats(): Promise<AlertStats> {
  return api.get<AlertStats>('/alerts/stats')
}
