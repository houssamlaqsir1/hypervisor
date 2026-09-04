import {
  useEffect,
  useState,
  type ComponentType,
  type CSSProperties,
  type ReactNode,
} from 'react'
import {
  applyTheme,
  DEFAULT_PREFS,
  loadPrefs,
  savePrefs,
  type Prefs,
} from '../lib/prefs'
import { setLanguage, type Language, type Translate } from '../lib/i18n'
import { useT } from '../lib/useT'
import {
  getLocation,
  startLocation,
  stopLocation,
  subscribeLocation,
  type OperatorLocation,
} from '../lib/operatorLocation'
import {
  IconBell,
  IconCheck,
  IconLanguage,
  IconMapPin,
  IconMoon,
  IconRefresh,
  IconSiren,
} from '../components/icons'

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

interface ShellProps {
  /** Position in the panel, driving the rows' entrance stagger. */
  index?: number
  Icon: ComponentType<{ size?: number }>
  /**
   * Accent for the row's icon tile, given as a CSS variable rather than a
   * literal so it follows the theme — the ambers and greens that read on
   * a white panel are invisible on the dark one.
   */
  tone: string
  toneSoft: string
  label: string
  desc: string
  note?: string | null
  noteTone?: 'info' | 'warn'
  /** The control on the right: a switch, a dropdown, whatever the setting needs. */
  control: ReactNode
}

/** Icon, label, description, optional status line, and one control. */
function SettingRowShell({ index = 0, Icon, tone, toneSoft, label, desc, note, noteTone, control }: ShellProps) {
  return (
    <div className="settings-row" style={{ '--i': index } as CSSProperties}>
      <div
        className="settings-row-icon-wrap"
        style={{ '--row-accent': tone, '--row-accent-soft': toneSoft } as CSSProperties}
      >
        <Icon size={17} />
      </div>
      <div className="settings-row-text">
        <span className="settings-row-label">{label}</span>
        <span className="settings-row-desc">{desc}</span>
        {note && (
          <span className={`settings-row-note settings-row-note--${noteTone === 'warn' ? 'warn' : 'ok'}`}>
            {note}
          </span>
        )}
      </div>
      {control}
    </div>
  )
}

type RowProps = Omit<ShellProps, 'control'> & {
  checked: boolean
  onChange: (v: boolean) => void
}

/** A setting that is genuinely on or off. */
function SettingRow({ checked, onChange, ...shell }: RowProps) {
  return <SettingRowShell {...shell} control={<Toggle checked={checked} onChange={onChange} />} />
}

type SelectRowProps<T extends string> = Omit<ShellProps, 'control'> & {
  value: T
  options: { value: T; label: string }[]
  onChange: (v: T) => void
}

/**
 * A setting that picks one of several values.
 *
 * Language belongs here rather than on a switch: "Français — on/off" would
 * frame English as the normal state and French as a deviation from it, when
 * they are simply two equal choices. A list also has somewhere to put a
 * third language without a redesign.
 */
function SettingSelectRow<T extends string>({ value, options, onChange, ...shell }: SelectRowProps<T>) {
  return (
    <SettingRowShell
      {...shell}
      control={
        <select
          className="settings-select"
          value={value}
          onChange={(e) => onChange(e.target.value as T)}
          aria-label={shell.label}
        >
          {options.map((o) => (
            <option key={o.value} value={o.value}>{o.label}</option>
          ))}
        </select>
      }
    />
  )
}

/**
 * State of the geolocation watch. Unlike notifications this always has
 * something worth showing while on: the live fix is the only evidence that
 * tracking is working, and its accuracy tells the operator how much to
 * trust a position placed on the map.
 */
function locationNote(loc: OperatorLocation, t: Translate): { text: string | null; tone: 'info' | 'warn' } {
  switch (loc.status) {
    case 'requesting':
      return { text: t('settings.location.waiting'), tone: 'info' }
    case 'active':
      return {
        text:
          t('settings.location.tracking', {
            lat: loc.latitude!.toFixed(5),
            lon: loc.longitude!.toFixed(5),
          }) + (loc.accuracyM ? t('settings.location.accuracy', { m: Math.round(loc.accuracyM) }) : ''),
        tone: 'info',
      }
    case 'denied':
    case 'unavailable':
      return { text: loc.message, tone: 'warn' }
    default:
      return { text: null, tone: 'info' }
  }
}

/** Whether the browser will still show its prompt, or has already decided. */
function notificationNote(enabled: boolean, t: Translate): { text: string | null; tone: 'info' | 'warn' } {
  if (!enabled || typeof Notification === 'undefined') return { text: null, tone: 'info' }
  if (Notification.permission === 'granted') {
    return { text: t('settings.notifications.allowed'), tone: 'info' }
  }
  if (Notification.permission === 'denied') {
    return {
      text: t('settings.notifications.blocked'),
      tone: 'warn',
    }
  }
  return { text: t('settings.notifications.willAsk'), tone: 'info' }
}

/* ── Main page ──────────────────────────────────────────── */
export function SettingsPage() {
  const t = useT()
  const [prefs, setPrefs] = useState<Prefs>(loadPrefs)
  const [saved, setSaved] = useState(false)
  const [location, setLocation] = useState<OperatorLocation>(getLocation)
  // Permission is browser state, not React state — re-read it after asking.
  const [permissionTick, setPermissionTick] = useState(0)

  useEffect(() => subscribeLocation(setLocation), [])
  useEffect(() => { applyTheme(prefs.theme) }, [prefs.theme])

  function update<K extends keyof Prefs>(key: K, value: Prefs[K]) {
    setPrefs((prev) => {
      const next = { ...prev, [key]: value }
      savePrefs(next)
      return next
    })
    setSaved(true)
    setTimeout(() => setSaved(false), 1800)
  }

  function toggleNotifications(on: boolean) {
    update('notifications', on)
    // The browser only shows its banner when the page actually asks, and it
    // asks at most once ever — so request on the way *on*, and only while
    // the decision is still open.
    if (on && typeof Notification !== 'undefined' && Notification.permission === 'default') {
      void Notification.requestPermission().then(() => setPermissionTick((t) => t + 1))
    }
  }

  function toggleLocation(on: boolean) {
    update('location_tracking', on)
    if (on) startLocation()
    else stopLocation()
  }

  function chooseLanguage(next: Language) {
    // setLanguage persists it itself, so mirror it into local state rather
    // than writing the same key twice from two places.
    setLanguage(next)
    setPrefs((prev) => ({ ...prev, language: next }))
    setSaved(true)
    setTimeout(() => setSaved(false), 1800)
  }

  function resetAll() {
    savePrefs(DEFAULT_PREFS)
    setPrefs(DEFAULT_PREFS)
    applyTheme(DEFAULT_PREFS.theme)
    setLanguage(DEFAULT_PREFS.language)
    // Follow the restored default rather than assuming it means "off".
    if (DEFAULT_PREFS.location_tracking) startLocation()
    else stopLocation()
  }

  const locNote = locationNote(location, t)
  const notifNote = notificationNote(prefs.notifications, t)
  void permissionTick // re-render hook for the permission read above

  return (
    <div className="settings-page">
      <div className="page-header">
        <div>
          <h2>{t('settings.title')}</h2>
          <p>{t('settings.subtitle')}</p>
        </div>
        <div className="page-actions">
          {saved && (
            <span className="settings-saved-badge" role="status">
              <IconCheck size={13} />
              {t('common.saved')}
            </span>
          )}
          <button type="button" className="btn secondary btn-sm" onClick={resetAll}>
            <IconRefresh size={14} />
            {t('settings.reset')}
          </button>
        </div>
      </div>

      {/*
        One panel rather than three. With four switches, separate cards per
        category cost more in borders, padding and gaps than the grouping
        buys — enough to push the last row off a laptop screen. Section
        labels sit inline instead, so the grouping survives without the
        chrome.
      */}
      <div className="settings-grid">
        <div className="settings-block">

          <p className="settings-group-label">{t('settings.group.alerts')}</p>

          <SettingRow
            index={0}
            Icon={IconBell}
            tone="var(--accent)"
            toneSoft="var(--accent-soft)"
            label={t('settings.notifications')}
            desc={t('settings.notifications.desc')}
            checked={prefs.notifications}
            onChange={toggleNotifications}
            note={notifNote.text}
            noteTone={notifNote.tone}
          />

          <SettingRow
            index={1}
            Icon={IconSiren}
            tone="var(--danger)"
            toneSoft="var(--danger-soft)"
            label={t('settings.highCritical')}
            desc={t('settings.highCritical.desc')}
            checked={prefs.notif_high_critical_only}
            onChange={(v) => update('notif_high_critical_only', v)}
          />

          <p className="settings-group-label">{t('settings.group.location')}</p>

          <SettingRow
            index={2}
            Icon={IconMapPin}
            tone="var(--success)"
            toneSoft="var(--success-soft)"
            label={t('settings.location')}
            desc={t('settings.location.desc')}
            checked={prefs.location_tracking}
            onChange={toggleLocation}
            note={locNote.text}
            noteTone={locNote.tone}
          />

          <p className="settings-group-label">{t('settings.group.appearance')}</p>

          <SettingRow
            index={3}
            Icon={IconMoon}
            tone="var(--neutral)"
            toneSoft="var(--neutral-soft)"
            label={t('settings.darkMode')}
            desc={t('settings.darkMode.desc')}
            checked={prefs.theme === 'dark'}
            onChange={(v) => update('theme', v ? 'dark' : 'light')}
          />

          <p className="settings-group-label">{t('settings.group.language')}</p>

          <SettingSelectRow
            index={4}
            Icon={IconLanguage}
            tone="var(--accent)"
            toneSoft="var(--accent-soft)"
            label={t('settings.language')}
            desc={t('settings.language.desc')}
            value={prefs.language}
            options={[
              { value: 'en', label: 'English' },
              { value: 'fr', label: 'Français' },
            ]}
            onChange={chooseLanguage}
          />

        </div>
      </div>
    </div>
  )
}
