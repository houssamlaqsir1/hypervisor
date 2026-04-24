import { useCallback, useEffect, useRef, useState } from 'react'
import { searchPlaces, type GeocodeResult } from '../api/geocode'

export type MapSearchPick = GeocodeResult

type Props = {
  /** Called when user picks a result or uses “My location”. */
  onPick: (place: MapSearchPick) => void
  className?: string
  /** Debounced suggestions while typing (after 3+ characters). */
  suggest?: boolean
  /** Shown under the row when the map is not ready to navigate yet (e.g. Cesium still mounting). */
  mapReadyHint?: string | null
}

export function MapSearchBar({ onPick, className, suggest = false, mapReadyHint = null }: Props) {
  const [q, setQ] = useState('')
  const [results, setResults] = useState<GeocodeResult[]>([])
  const [searchLoading, setSearchLoading] = useState(false)
  const [suggestLoading, setSuggestLoading] = useState(false)
  const [err, setErr] = useState<string | null>(null)
  const [open, setOpen] = useState(false)
  const fetchSeq = useRef(0)

  const applySearchResults = useCallback((list: GeocodeResult[]) => {
    setResults(list)
    setOpen(list.length > 0)
  }, [])

  const runSearch = useCallback(async () => {
    const query = q.trim()
    if (!query) return
    fetchSeq.current += 1
    const seq = fetchSeq.current
    setSearchLoading(true)
    setErr(null)
    try {
      const list = await searchPlaces(query)
      if (seq !== fetchSeq.current) return
      applySearchResults(list)
    } catch {
      if (seq !== fetchSeq.current) return
      setErr('Search failed. Is the backend running?')
      setResults([])
      setOpen(false)
    } finally {
      if (seq === fetchSeq.current) setSearchLoading(false)
    }
  }, [q, applySearchResults])

  useEffect(() => {
    if (!suggest) return
    const query = q.trim()
    if (query.length < 3) {
      fetchSeq.current += 1
      setSuggestLoading(false)
      setResults([])
      setOpen(false)
      return
    }
    const t = window.setTimeout(() => {
      fetchSeq.current += 1
      const seq = fetchSeq.current
      setSuggestLoading(true)
      setErr(null)
      void (async () => {
        try {
          const list = await searchPlaces(query)
          if (seq !== fetchSeq.current) return
          applySearchResults(list)
        } catch {
          if (seq !== fetchSeq.current) return
          setErr('Search failed. Is the backend running?')
          setResults([])
          setOpen(false)
        } finally {
          if (seq === fetchSeq.current) setSuggestLoading(false)
        }
      })()
    }, 400)
    return () => clearTimeout(t)
  }, [q, suggest, applySearchResults])

  const myLocation = useCallback(() => {
    if (!navigator.geolocation) {
      setErr('Geolocation is not supported in this browser.')
      return
    }
    setErr(null)
    navigator.geolocation.getCurrentPosition(
      (pos) => {
        onPick({
          lat: pos.coords.latitude,
          lon: pos.coords.longitude,
          displayName: 'My location',
          pickKind: 'geolocation',
        })
        setResults([])
        setOpen(false)
      },
      () => setErr('Location denied or unavailable.'),
      { enableHighAccuracy: true, timeout: 15_000, maximumAge: 60_000 },
    )
  }, [onPick])

  return (
    <div className={`map-search-bar ${className ?? ''}`}>
      <div className="map-search-row">
        <input
          type="search"
          value={q}
          onChange={(e) => setQ(e.target.value)}
          onKeyDown={(e) => {
            if (e.key === 'Enter') void runSearch()
          }}
          placeholder="Search city, country, address…"
          aria-label="Search map"
          aria-busy={suggestLoading || searchLoading}
        />
        <button
          type="button"
          className="btn"
          disabled={searchLoading}
          onClick={() => void runSearch()}
        >
          {searchLoading ? '…' : 'Search'}
        </button>
        <button
          type="button"
          className="btn secondary map-search-locate"
          title="Go to my location"
          aria-label="Go to my location"
          onClick={myLocation}
        >
          ⊕
        </button>
      </div>
      {mapReadyHint && <p className="muted map-search-hint">{mapReadyHint}</p>}
      {suggest && suggestLoading && !searchLoading && (
        <p className="muted map-search-hint">Fetching suggestions…</p>
      )}
      {err && <p className="muted map-search-err">{err}</p>}
      {open && results.length > 0 && (
        <ul className="map-search-results" role="listbox">
          {results.map((r, i) => (
            <li key={`${r.lat}-${r.lon}-${i}`}>
              <button
                type="button"
                className="map-search-result-btn"
                onClick={() => {
                  onPick(r)
                  setOpen(false)
                }}
              >
                {r.displayName || `${r.lat.toFixed(4)}, ${r.lon.toFixed(4)}`}
              </button>
            </li>
          ))}
        </ul>
      )}
    </div>
  )
}
