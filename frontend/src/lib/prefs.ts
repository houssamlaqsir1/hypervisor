/**
 * Operator preferences, stored in the browser.
 *
 * Every switch here does something. That is a deliberate constraint: a
 * settings screen full of toggles that quietly change nothing is worse
 * than a short one, because an operator who flips "Critical only" and
 * still gets flooded stops trusting the whole panel.
 *
 * Consumers read through {@link loadPrefs} rather than touching
 * localStorage directly, so the shape and its defaults are defined once.
 */

export type Theme = 'dark' | 'light'
export type Language = 'en' | 'fr'

export interface Prefs {
  /**
   * Announce new alerts — browser notification *and* the audible cue
   * together. They were once two switches, but nobody wants a silent
   * popup or a beep with no idea what caused it; in practice they are one
   * decision: "tell me when something happens, or don't".
   */
  notifications: boolean
  /** Announce only HIGH and CRITICAL — suppress LOW/MEDIUM routine traffic. */
  notif_high_critical_only: boolean
  /** Master consent for browser geolocation; gates every feature that uses it. */
  location_tracking: boolean
  theme: Theme
  /** Interface language. Alert text composed by the backend is unaffected. */
  language: Language
}

export const STORAGE_KEY = 'hypervisor_settings'

export const DEFAULT_PREFS: Prefs = {
  notifications: true,
  notif_high_critical_only: false,
  location_tracking: true,
  theme: 'dark',
  language: 'en',
}

/**
 * Reads stored preferences, filling anything missing from the defaults.
 *
 * Older builds stored `notif_push` and `notif_sound` separately and named
 * the severity filter `notif_critical_only`. Those are translated here so
 * an operator's existing choices survive the upgrade instead of silently
 * resetting.
 */
export function loadPrefs(): Prefs {
  try {
    const raw = localStorage.getItem(STORAGE_KEY)
    if (!raw) return { ...DEFAULT_PREFS }
    const stored = JSON.parse(raw) as Record<string, unknown>

    const legacyNotifications =
      typeof stored.notif_push === 'boolean' || typeof stored.notif_sound === 'boolean'
        ? Boolean(stored.notif_push) || Boolean(stored.notif_sound)
        : undefined
    const legacySeverityFilter =
      typeof stored.notif_critical_only === 'boolean' ? stored.notif_critical_only : undefined

    return {
      notifications:
        (stored.notifications as boolean) ?? legacyNotifications ?? DEFAULT_PREFS.notifications,
      notif_high_critical_only:
        (stored.notif_high_critical_only as boolean) ??
        legacySeverityFilter ??
        DEFAULT_PREFS.notif_high_critical_only,
      location_tracking:
        (stored.location_tracking as boolean) ?? DEFAULT_PREFS.location_tracking,
      theme: stored.theme === 'light' ? 'light' : 'dark',
      language: stored.language === 'fr' ? 'fr' : 'en',
    }
  } catch {
    return { ...DEFAULT_PREFS }
  }
}

export function savePrefs(prefs: Prefs) {
  localStorage.setItem(STORAGE_KEY, JSON.stringify(prefs))
}

export function applyTheme(theme: Theme) {
  document.documentElement.classList.toggle('dark-mode', theme === 'dark')
}
