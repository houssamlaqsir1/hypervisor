import { api } from './client'

export interface GeocodeResult {
  lat: number
  lon: number
  displayName: string
  south?: number | null
  north?: number | null
  west?: number | null
  east?: number | null
  /** Present only for client-side “My location” picks (not returned by the API). */
  pickKind?: 'geolocation'
}

export function searchPlaces(query: string): Promise<GeocodeResult[]> {
  const q = encodeURIComponent(query.trim())
  return api.get<GeocodeResult[]>(`/geocode/search?q=${q}`)
}
