import { useSyncExternalStore } from 'react'
import { languageSnapshot, subscribeLanguage, t } from './i18n'

/**
 * Gives a component the translate function and re-renders it when the
 * language changes.
 *
 * The language lives outside React (it is read at boot, before any
 * component mounts, and written from the settings page), so components
 * subscribe to it through `useSyncExternalStore` rather than threading a
 * context through the tree. Switching language then re-renders every
 * subscribed component at once, with no page reload.
 */
export function useT(): typeof t {
  useSyncExternalStore(subscribeLanguage, languageSnapshot, languageSnapshot)
  return t
}
