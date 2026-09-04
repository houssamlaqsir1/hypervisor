/**
 * The interface's icon set.
 *
 * Every icon here is drawn on the same 24×24 grid with the same stroke
 * weight and rounded joins, and inherits `currentColor` — so an icon takes
 * the colour of whatever it sits in, and a row of them reads as one family.
 *
 * This replaced a mixture of emoji (📊 👤 🎥) and box-drawing characters
 * (⊞ ◉ ◈), which is worth recording because it was not only a matter of
 * taste: emoji are rendered from the operating system's own font, so the
 * same screen showed flat two-tone glyphs on Windows, glossy ones on
 * macOS, and something else again on a Linux control-room terminal — at
 * sizes and baselines nobody here chose. Strokes drawn in the document
 * look identical everywhere and follow the theme.
 */

import type { SVGProps } from 'react'

interface IconProps extends Omit<SVGProps<SVGSVGElement>, 'width' | 'height'> {
  /** Edge length in pixels; the icon is always square. */
  size?: number
}

/**
 * Shared frame. Hidden from assistive technology by default, because an
 * icon here almost always sits beside the text it illustrates — passing an
 * `aria-label` clears that, for the rare icon that carries the whole
 * meaning, as an icon-only button does.
 */
function Icon({ size = 20, children, ...rest }: IconProps) {
  return (
    <svg
      viewBox="0 0 24 24"
      width={size}
      height={size}
      fill="none"
      stroke="currentColor"
      strokeWidth={1.75}
      strokeLinecap="round"
      strokeLinejoin="round"
      aria-hidden={rest['aria-label'] ? undefined : true}
      focusable="false"
      {...rest}
    >
      {children}
    </svg>
  )
}

/* ── brand ────────────────────────────────────────────────── */

/** A shield over rail track: railway, guarded. The product mark. */
export function IconBrand(props: IconProps) {
  return (
    <Icon {...props}>
      <path d="M12 2.6 20 5.4v6.1c0 4.6-3.2 8.4-8 9.6-4.8-1.2-8-5-8-9.6V5.4l8-2.8Z" />
      <path d="M9.7 8.4 9 15.6M14.3 8.4l.7 7.2M8.7 11.1h6.6M8.4 13.8h7.2" />
    </Icon>
  )
}

/* ── navigation ───────────────────────────────────────────── */

export function IconDashboard(props: IconProps) {
  return (
    <Icon {...props}>
      <rect x="3" y="3" width="7.5" height="7.5" rx="1.5" />
      <rect x="13.5" y="3" width="7.5" height="4.5" rx="1.5" />
      <rect x="13.5" y="10.5" width="7.5" height="10.5" rx="1.5" />
      <rect x="3" y="13.5" width="7.5" height="7.5" rx="1.5" />
    </Icon>
  )
}

/** Camera body with a lens — the live video watch. */
export function IconLive(props: IconProps) {
  return (
    <Icon {...props}>
      <rect x="2.5" y="6" width="13" height="12" rx="2.5" />
      <path d="M15.5 10.5 21 7.6v8.8l-5.5-2.9z" />
      <circle cx="9" cy="12" r="2.5" />
    </Icon>
  )
}

export function IconGlobe(props: IconProps) {
  return (
    <Icon {...props}>
      <circle cx="12" cy="12" r="9" />
      <path d="M3 12h18M12 3c2.4 2.5 3.6 5.5 3.6 9s-1.2 6.5-3.6 9c-2.4-2.5-3.6-5.5-3.6-9S9.6 5.5 12 3Z" />
    </Icon>
  )
}

export function IconHistory(props: IconProps) {
  return (
    <Icon {...props}>
      <path d="M3.5 9.5A9 9 0 1 1 3 12" />
      <path d="M3 4.5v5h5" />
      <path d="M12 7.5V12l3 1.8" />
    </Icon>
  )
}

export function IconAnalytics(props: IconProps) {
  return (
    <Icon {...props}>
      <path d="M3.5 20.5h17" />
      <rect x="4.5" y="12" width="4" height="6" rx="1" />
      <rect x="10" y="7.5" width="4" height="10.5" rx="1" />
      <rect x="15.5" y="10" width="4" height="8" rx="1" />
    </Icon>
  )
}

/** Sliders rather than a cog: these are preferences, not machinery. */
export function IconSettings(props: IconProps) {
  return (
    <Icon {...props}>
      <path d="M4 7h9M17 7h3M4 12h3M11 12h9M4 17h9M17 17h3" />
      <circle cx="15" cy="7" r="2.2" />
      <circle cx="9" cy="12" r="2.2" />
      <circle cx="15" cy="17" r="2.2" />
    </Icon>
  )
}

export function IconUsers(props: IconProps) {
  return (
    <Icon {...props}>
      <circle cx="9.5" cy="8" r="3.5" />
      <path d="M3 20c0-3.3 2.9-5.5 6.5-5.5s6.5 2.2 6.5 5.5" />
      <path d="M16.5 5.2a3.5 3.5 0 0 1 0 6.6M18 14.9c2 .7 3.4 2.3 3.4 4.4" />
    </Icon>
  )
}

export function IconCamera(props: IconProps) {
  return (
    <Icon {...props}>
      <path d="M3 8.5A2.5 2.5 0 0 1 5.5 6h1.7a1 1 0 0 0 .83-.45l1.14-1.7A1 1 0 0 1 10 3.4h4a1 1 0 0 1 .83.45l1.14 1.7A1 1 0 0 0 16.8 6h1.7A2.5 2.5 0 0 1 21 8.5v8A2.5 2.5 0 0 1 18.5 19h-13A2.5 2.5 0 0 1 3 16.5z" />
      <circle cx="12" cy="12.2" r="3.4" />
    </Icon>
  )
}

/** A folded map — the surveillance zones. */
export function IconZones(props: IconProps) {
  return (
    <Icon {...props}>
      <path d="M9 4.5 3.5 6.6v13L9 17.4l6 2.1 5.5-2.1v-13L15 6.6z" />
      <path d="M9 4.5v12.9M15 6.6v12.9" />
    </Icon>
  )
}

/* ── severity & status ────────────────────────────────────── */

export function IconBell(props: IconProps) {
  return (
    <Icon {...props}>
      <path d="M6 9a6 6 0 0 1 12 0c0 3.2.7 5 1.6 6.1.5.6.1 1.6-.7 1.6H5.1c-.8 0-1.2-1-.7-1.6C5.3 14 6 12.2 6 9Z" />
      <path d="M10 20a2.2 2.2 0 0 0 4 0" />
    </Icon>
  )
}

export function IconAlertOctagon(props: IconProps) {
  return (
    <Icon {...props}>
      <path d="M8.4 3h7.2l5.4 5.4v7.2L15.6 21H8.4L3 15.6V8.4z" />
      <path d="M12 8v4.8" />
      <circle cx="12" cy="16.2" r=".9" fill="currentColor" stroke="none" />
    </Icon>
  )
}

export function IconAlertTriangle(props: IconProps) {
  return (
    <Icon {...props}>
      <path d="M10.7 4 3.2 17.3A1.5 1.5 0 0 0 4.5 19.5h15a1.5 1.5 0 0 0 1.3-2.2L13.3 4a1.5 1.5 0 0 0-2.6 0Z" />
      <path d="M12 9.4v4.1" />
      <circle cx="12" cy="16.6" r=".9" fill="currentColor" stroke="none" />
    </Icon>
  )
}

export function IconAlertCircle(props: IconProps) {
  return (
    <Icon {...props}>
      <circle cx="12" cy="12" r="9" />
      <path d="M12 7.4v5" />
      <circle cx="12" cy="16.2" r=".9" fill="currentColor" stroke="none" />
    </Icon>
  )
}

export function IconInfo(props: IconProps) {
  return (
    <Icon {...props}>
      <circle cx="12" cy="12" r="9" />
      <path d="M12 11.2v5.4" />
      <circle cx="12" cy="7.9" r=".9" fill="currentColor" stroke="none" />
    </Icon>
  )
}

/** A rotating beacon — the alerts that must not be missed. */
export function IconSiren(props: IconProps) {
  return (
    <Icon {...props}>
      <path d="M7 15.5a5 5 0 0 1 10 0z" />
      <rect x="4.5" y="15.5" width="15" height="4" rx="1.4" />
      <path d="M12 5.2V3.2M18 8.2l1.6-1.5M6 8.2 4.4 6.7" />
    </Icon>
  )
}

export function IconMapPin(props: IconProps) {
  return (
    <Icon {...props}>
      <path d="M12 21.2c4-4.3 6-7.6 6-10.2a6 6 0 1 0-12 0c0 2.6 2 5.9 6 10.2Z" />
      <circle cx="12" cy="10.7" r="2.4" />
    </Icon>
  )
}

export function IconMoon(props: IconProps) {
  return (
    <Icon {...props}>
      <path d="M20.4 14.3A8.6 8.6 0 0 1 9.7 3.6a8.6 8.6 0 1 0 10.7 10.7Z" />
    </Icon>
  )
}

export function IconLanguage(props: IconProps) {
  return (
    <Icon {...props}>
      <circle cx="12" cy="12" r="9" />
      <path d="M3.4 9.5h17.2M3.4 14.5h17.2" />
      <path d="M12 3c2.2 2.6 3.3 5.6 3.3 9s-1.1 6.4-3.3 9c-2.2-2.6-3.3-5.6-3.3-9S9.8 5.6 12 3Z" />
    </Icon>
  )
}

/* ── actions ──────────────────────────────────────────────── */

export function IconTrash(props: IconProps) {
  return (
    <Icon {...props}>
      <path d="M4 6.5h16M9.5 6.5V4.8c0-.7.6-1.3 1.3-1.3h2.4c.7 0 1.3.6 1.3 1.3v1.7" />
      <path d="M6.2 6.5 7 19.1c.05.8.7 1.4 1.5 1.4h7c.8 0 1.45-.6 1.5-1.4l.8-12.6" />
      <path d="M10.3 10.2v6.4M13.7 10.2v6.4" />
    </Icon>
  )
}

export function IconDownload(props: IconProps) {
  return (
    <Icon {...props}>
      <path d="M12 3.5v11.2" />
      <path d="M7.8 10.6 12 14.8l4.2-4.2" />
      <path d="M4.5 17.2v1.8c0 .8.7 1.5 1.5 1.5h12c.8 0 1.5-.7 1.5-1.5v-1.8" />
    </Icon>
  )
}

export function IconCheck(props: IconProps) {
  return (
    <Icon {...props}>
      <path d="M5 12.8 9.6 17.4 19 6.9" />
    </Icon>
  )
}

/** A tick inside a circle — acknowledged, but not yet closed. */
export function IconCheckCircle(props: IconProps) {
  return (
    <Icon {...props}>
      <circle cx="12" cy="12" r="9" />
      <path d="M8.2 12.3 11 15.1l5-5.6" />
    </Icon>
  )
}

export function IconX(props: IconProps) {
  return (
    <Icon {...props}>
      <path d="M6.2 6.2 17.8 17.8M17.8 6.2 6.2 17.8" />
    </Icon>
  )
}

export function IconChevronDown(props: IconProps) {
  return (
    <Icon {...props}>
      <path d="M6 9.5 12 15.5l6-6" />
    </Icon>
  )
}

export function IconChevronUp(props: IconProps) {
  return (
    <Icon {...props}>
      <path d="M6 14.5 12 8.5l6 6" />
    </Icon>
  )
}

export function IconSearch(props: IconProps) {
  return (
    <Icon {...props}>
      <circle cx="10.8" cy="10.8" r="6.8" />
      <path d="M15.8 15.8 20.5 20.5" />
    </Icon>
  )
}

/** A crosshair — "use my current location". */
export function IconCrosshair(props: IconProps) {
  return (
    <Icon {...props}>
      <circle cx="12" cy="12" r="7" />
      <circle cx="12" cy="12" r="2.2" />
      <path d="M12 2.2v2.6M12 19.2v2.6M2.2 12h2.6M19.2 12h2.6" />
    </Icon>
  )
}

export function IconLogOut(props: IconProps) {
  return (
    <Icon {...props}>
      <path d="M14.5 4.5h3.2c1 0 1.8.8 1.8 1.8v11.4c0 1-.8 1.8-1.8 1.8h-3.2" />
      <path d="M10.2 8.2 14 12l-3.8 3.8M14 12H4.2" />
    </Icon>
  )
}

export function IconPlus(props: IconProps) {
  return (
    <Icon {...props}>
      <path d="M12 5.2v13.6M5.2 12h13.6" />
    </Icon>
  )
}

export function IconPencil(props: IconProps) {
  return (
    <Icon {...props}>
      <path d="M16.6 3.9a2.1 2.1 0 0 1 3 3L8.5 18l-4 1 1-4z" />
      <path d="M14.4 6.1 17.9 9.6" />
    </Icon>
  )
}

/** A compass arrow — "show this on the map". */
export function IconNavigation(props: IconProps) {
  return (
    <Icon {...props}>
      <path d="M20.4 4.1 4.6 10.3c-.9.35-.8 1.65.15 1.85l6.2 1.3 1.3 6.2c.2.95 1.5 1.05 1.85.15z" />
    </Icon>
  )
}

export function IconRefresh(props: IconProps) {
  return (
    <Icon {...props}>
      <path d="M20 11.4A8 8 0 0 0 6.3 6.5L3.6 9" />
      <path d="M4 12.6a8 8 0 0 0 13.7 4.9L20.4 15" />
      <path d="M3.4 4.6V9h4.4M20.6 19.4V15h-4.4" />
    </Icon>
  )
}

export function IconEye(props: IconProps) {
  return (
    <Icon {...props}>
      <path d="M2.5 12C4 8.4 7.7 5.8 12 5.8s8 2.6 9.5 6.2c-1.5 3.6-5.2 6.2-9.5 6.2S4 15.6 2.5 12Z" />
      <circle cx="12" cy="12" r="3" />
    </Icon>
  )
}

export function IconEyeOff(props: IconProps) {
  return (
    <Icon {...props}>
      <path d="M9.9 5.9A9.9 9.9 0 0 1 12 5.8c4.3 0 8 2.6 9.5 6.2a12.6 12.6 0 0 1-2.7 3.9M6.1 7.1A12.3 12.3 0 0 0 2.5 12c1.5 3.6 5.2 6.2 9.5 6.2 1.3 0 2.6-.24 3.7-.68" />
      <path d="M9.9 9.9a3 3 0 0 0 4.2 4.2" />
      <path d="M3.5 3.5 20.5 20.5" />
    </Icon>
  )
}

/** Signal waves — the live feed's connection state. */
export function IconSignal(props: IconProps) {
  return (
    <Icon {...props}>
      <circle cx="12" cy="17.5" r="1.4" />
      <path d="M8.6 14.1a4.8 4.8 0 0 1 6.8 0M5.6 11.1a9 9 0 0 1 12.8 0M2.8 8.3a13 13 0 0 1 18.4 0" />
    </Icon>
  )
}
