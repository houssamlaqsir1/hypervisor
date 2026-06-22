import { useEffect, useState } from 'react'

/* ── Types ──────────────────────────────────────────────── */
type Theme = 'dark' | 'light' | 'system'

interface Prefs {
  notif_push: boolean
  notif_sound: boolean
  notif_email: boolean
  notif_critical_only: boolean
  location_tracking: boolean
  map_auto_center: boolean
  map_3d_default: boolean
  ai_auto_start: boolean
  overlay_boxes: boolean
  camera_hd: boolean
  compact_alerts: boolean
  animations: boolean
  theme: Theme
}

const DEFAULTS: Prefs = {
  notif_push: true,
  notif_sound: true,
  notif_email: false,
  notif_critical_only: false,
  location_tracking: true,
  map_auto_center: true,
  map_3d_default: false,
  ai_auto_start: true,
  overlay_boxes: true,
  camera_hd: false,
  compact_alerts: false,
  animations: true,
  theme: 'dark',
}

function load(): Prefs {
  try {
    const raw = localStorage.getItem('hypervisor_settings')
    if (raw) return { ...DEFAULTS, ...JSON.parse(raw) }
  } catch { /* ignore */ }
  return { ...DEFAULTS }
}

function save(p: Prefs) {
  localStorage.setItem('hypervisor_settings', JSON.stringify(p))
}

function applyTheme(theme: Theme) {
  const prefersDark = window.matchMedia('(prefers-color-scheme: dark)').matches
  const dark = theme === 'dark' || (theme === 'system' && prefersDark)
  document.documentElement.classList.toggle('dark-mode', dark)
}

/* ── Sub-components ─────────────────────────────────────── */
function Toggle({ checked, onChange }: { checked: boolean; onChange: (v: boolean) => void }) {
  return (
    <button
      role="switch"
      aria-checked={checked}
      onClick={() => onChange(!checked)}
      className={`settings-toggle ${checked ? 'settings-toggle--on' : ''}`}
    >
      <span className="settings-toggle-thumb" />
    </button>
  )
}

function Chevron() {
  return (
    <svg width="7" height="12" viewBox="0 0 7 12" fill="none" style={{ flexShrink: 0, opacity: 0.35 }}>
      <path d="M1 1l5 5-5 5" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" />
    </svg>
  )
}

function CheckIcon() {
  return (
    <svg width="13" height="10" viewBox="0 0 13 10" fill="none">
      <path d="M1 5l4 4L12 1" stroke="white" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" />
    </svg>
  )
}

/* ── Main page ──────────────────────────────────────────── */
export function SettingsPage() {
  const [prefs, setPrefs] = useState<Prefs>(load)
  const [saved, setSaved] = useState(false)

  useEffect(() => {
    applyTheme(prefs.theme)
  }, [prefs.theme])

  function update<K extends keyof Prefs>(key: K, value: Prefs[K]) {
    setPrefs((prev) => {
      const next = { ...prev, [key]: value }
      save(next)
      return next
    })
    setSaved(true)
    setTimeout(() => setSaved(false), 1800)
  }

  function clearCache() {
    localStorage.removeItem('hypervisor_alert_cache')
    setSaved(true)
    setTimeout(() => setSaved(false), 1800)
  }

  return (
    <div className="settings-page">
      <div className="page-header">
        <div>
          <h2>Settings</h2>
          <p>Manage your preferences, notifications, and appearance.</p>
        </div>
        <div style={{ display: 'flex', alignItems: 'center', gap: 12 }}>
          {saved && <span className="settings-saved-badge">Saved ✓</span>}
          <button className="btn secondary btn-sm" onClick={() => { save(DEFAULTS); setPrefs(DEFAULTS); applyTheme(DEFAULTS.theme) }}>
            Reset to defaults
          </button>
        </div>
      </div>

      <div className="settings-grid">

        {/* ── NOTIFICATIONS ── */}
        <div className="settings-card">
          <div className="settings-block">
            <p className="settings-group-label">Notifications</p>
            {[
              { key: 'notif_push'         as const, label: 'Push notifications',  desc: 'Real-time browser alerts',           icon: '🔔', bg: 'rgba(245,158,11,0.18)' },
              { key: 'notif_sound'        as const, label: 'Alert sounds',        desc: 'Play audio on new alerts',           icon: '🔊', bg: 'rgba(99,102,241,0.18)' },
              { key: 'notif_email'        as const, label: 'Email notifications', desc: 'Send critical alerts to your email', icon: '✉️', bg: 'rgba(37,99,235,0.18)'  },
              { key: 'notif_critical_only'as const, label: 'Critical only',       desc: 'Suppress LOW and MEDIUM alerts',     icon: '🚨', bg: 'rgba(220,38,38,0.18)'  },
            ].map((r) => (
              <div key={r.key} className="settings-row">
                <div className="settings-row-icon-wrap" style={{ background: r.bg }}>{r.icon}</div>
                <div className="settings-row-text">
                  <span className="settings-row-label">{r.label}</span>
                  <span className="settings-row-desc">{r.desc}</span>
                </div>
                <Toggle checked={prefs[r.key]} onChange={(v) => update(r.key, v)} />
              </div>
            ))}
          </div>
        </div>

        {/* ── LOCATION & MAP ── */}
        <div className="settings-card">
          <div className="settings-block">
            <p className="settings-group-label">Location & Map</p>
            {[
              { key: 'location_tracking'as const, label: 'Location tracking', desc: 'Use your browser geolocation',            icon: '📍', bg: 'rgba(34,197,94,0.18)'  },
              { key: 'map_auto_center'  as const, label: 'Auto-center map',   desc: 'Pan to new alert locations automatically', icon: '🗺️', bg: 'rgba(37,99,235,0.18)'  },
              { key: 'map_3d_default'   as const, label: '3D map by default', desc: 'Open 3D view instead of flat map',         icon: '🌐', bg: 'rgba(139,92,246,0.18)' },
            ].map((r) => (
              <div key={r.key} className="settings-row">
                <div className="settings-row-icon-wrap" style={{ background: r.bg }}>{r.icon}</div>
                <div className="settings-row-text">
                  <span className="settings-row-label">{r.label}</span>
                  <span className="settings-row-desc">{r.desc}</span>
                </div>
                <Toggle checked={prefs[r.key]} onChange={(v) => update(r.key, v)} />
              </div>
            ))}
          </div>
        </div>

        {/* ── CAMERAS & AI ── */}
        <div className="settings-card">
          <div className="settings-block">
            <p className="settings-group-label">Cameras & AI</p>
            {[
              { key: 'ai_auto_start'as const, label: 'Auto-start AI detection', desc: 'Begin analysis when a camera goes live', icon: '🤖', bg: 'rgba(34,197,94,0.18)'  },
              { key: 'overlay_boxes'as const, label: 'Detection overlays',      desc: 'Draw bounding boxes on the camera feed', icon: '🎯', bg: 'rgba(245,158,11,0.18)' },
              { key: 'camera_hd'    as const, label: 'HD stream quality',       desc: 'Higher resolution, more bandwidth',      icon: '📹', bg: 'rgba(99,102,241,0.18)' },
            ].map((r) => (
              <div key={r.key} className="settings-row">
                <div className="settings-row-icon-wrap" style={{ background: r.bg }}>{r.icon}</div>
                <div className="settings-row-text">
                  <span className="settings-row-label">{r.label}</span>
                  <span className="settings-row-desc">{r.desc}</span>
                </div>
                <Toggle checked={prefs[r.key]} onChange={(v) => update(r.key, v)} />
              </div>
            ))}
          </div>
        </div>

        {/* ── APPEARANCE ── */}
        <div className="settings-card">
          <div className="settings-block">
            <p className="settings-group-label">Appearance</p>
            <div className="settings-row">
              <div className="settings-row-icon-wrap" style={{ background: 'rgba(99,102,241,0.18)' }}>🌙</div>
              <div className="settings-row-text">
                <span className="settings-row-label">Dark mode</span>
                <span className="settings-row-desc">Switch to a dark colour scheme</span>
              </div>
              <Toggle checked={prefs.theme === 'dark'} onChange={(v) => update('theme', v ? 'dark' : 'light')} />
            </div>
            <div className="settings-row">
              <div className="settings-row-icon-wrap" style={{ background: 'rgba(71,85,105,0.2)' }}>☰</div>
              <div className="settings-row-text">
                <span className="settings-row-label">Compact alert list</span>
                <span className="settings-row-desc">Show more alerts with reduced row height</span>
              </div>
              <Toggle checked={prefs.compact_alerts} onChange={(v) => update('compact_alerts', v)} />
            </div>
            <div className="settings-row">
              <div className="settings-row-icon-wrap" style={{ background: 'rgba(139,92,246,0.18)' }}>✨</div>
              <div className="settings-row-text">
                <span className="settings-row-label">Interface animations</span>
                <span className="settings-row-desc">Enable transitions and motion effects</span>
              </div>
              <Toggle checked={prefs.animations} onChange={(v) => update('animations', v)} />
            </div>
          </div>
        </div>

      </div>
    </div>
  )
}
