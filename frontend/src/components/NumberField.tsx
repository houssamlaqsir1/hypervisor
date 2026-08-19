import { useEffect, useRef, useState } from 'react'

interface Props {
  value: number
  onChange: (value: number) => void
  /** How much one stepper click moves the value. */
  step?: number
  min?: number
  max?: number
  disabled?: boolean
  required?: boolean
  title?: string
  'aria-label'?: string
}

/**
 * A numeric field you can actually type in.
 *
 * The obvious implementation — `<input type="number">` whose onChange does
 * `Number(e.target.value)` — is quietly hostile to editing, because a
 * half-typed number is not a number:
 *
 * - typing `-` (to start a longitude) parses to `NaN`, blanking the field
 * - clearing it parses to `0`, so the field refills itself under you
 * - typing `33.` parses to `33`, and the decimal point you just pressed
 *   disappears before you can type what follows it
 *
 * So the text being edited is kept as text, and a number is emitted only
 * once it parses. The field re-syncs if the value is changed from outside
 * (a form reset, opening a row for edit) but never rewrites what someone is
 * in the middle of typing.
 *
 * The native spinners are replaced with our own, for looks and for
 * behaviour: the browser's step for `step="any"` is 1, which on a latitude
 * is a jump of about 111 km. Here the caller sets a step that makes sense
 * for the quantity — a ten-metre nudge for coordinates, a degree for a
 * bearing. Arrow keys do the same thing, as they would natively.
 */
export function NumberField({
  value,
  onChange,
  step = 1,
  min,
  max,
  disabled,
  required,
  title,
  'aria-label': ariaLabel,
}: Props) {
  const [text, setText] = useState(() => format(value))
  // Mirrors `text` for readers that must not see a stale closure. Two quick
  // stepper clicks land in one React batch, and a handler reading the state
  // variable would compute both from the same starting value — so the second
  // click would be silently lost.
  const textRef = useRef(text)
  // What we last handed upward, so an echo of our own value doesn't count
  // as an outside change and clobber in-progress typing.
  const lastEmitted = useRef<number>(value)

  function write(next: string) {
    textRef.current = next
    setText(next)
  }

  useEffect(() => {
    if (value !== lastEmitted.current) {
      lastEmitted.current = value
      write(format(value))
    }
  }, [value])

  function emit(next: number) {
    lastEmitted.current = next
    onChange(next)
  }

  function onType(raw: string) {
    // Reject anything that couldn't become a number, the way type="number"
    // does — letters simply don't appear. What this *does* accept is a
    // number still being typed ("-", "-7.", ""), which is the part the
    // native input got wrong.
    if (!PARTIAL_NUMBER.test(raw)) return
    write(raw)
    if (raw.trim() === '') return // mid-edit; wait for blur to decide
    const parsed = Number(raw)
    if (Number.isFinite(parsed)) emit(parsed)
  }

  /** Anything left unparseable when focus leaves reverts to the last good value. */
  function onBlur() {
    const raw = textRef.current
    const parsed = Number(raw)
    if (raw.trim() === '' || !Number.isFinite(parsed)) {
      write(format(lastEmitted.current))
      return
    }
    const clamped = clamp(parsed, min, max)
    if (clamped !== parsed) emit(clamped)
    write(format(clamped))
  }

  function nudge(direction: 1 | -1) {
    const raw = textRef.current
    const base = raw.trim() !== '' && Number.isFinite(Number(raw)) ? Number(raw) : 0
    // Round to the step's own precision — 33.5731 + 0.0001 would otherwise
    // land on 33.573199999999996.
    const decimals = (String(step).split('.')[1] ?? '').length
    const next = clamp(Number((base + direction * step).toFixed(decimals)), min, max)
    write(format(next))
    emit(next)
  }

  return (
    <div className={`number-field ${disabled ? 'number-field--disabled' : ''}`}>
      <input
        // text + inputMode rather than type="number": it keeps the browser
        // from second-guessing a half-typed value, and lets the steppers
        // below be ours instead of the platform's.
        type="text"
        inputMode="decimal"
        value={text}
        onChange={(e) => onType(e.target.value)}
        onBlur={onBlur}
        onKeyDown={(e) => {
          if (e.key === 'ArrowUp') { e.preventDefault(); nudge(1) }
          if (e.key === 'ArrowDown') { e.preventDefault(); nudge(-1) }
        }}
        disabled={disabled}
        required={required}
        title={title}
        aria-label={ariaLabel}
      />
      <span className="number-field-steppers" aria-hidden="true">
        <button type="button" tabIndex={-1} disabled={disabled} onClick={() => nudge(1)}>
          <svg viewBox="0 0 10 6" width="9" height="6"><path d="M1 5L5 1L9 5" fill="none" stroke="currentColor" strokeWidth="1.6" strokeLinecap="round" strokeLinejoin="round" /></svg>
        </button>
        <button type="button" tabIndex={-1} disabled={disabled} onClick={() => nudge(-1)}>
          <svg viewBox="0 0 10 6" width="9" height="6"><path d="M1 1L5 5L9 1" fill="none" stroke="currentColor" strokeWidth="1.6" strokeLinecap="round" strokeLinejoin="round" /></svg>
        </button>
      </span>
    </div>
  )
}

/**
 * A number, or the beginning of one: an optional sign, digits, at most one
 * decimal point. Matches "", "-", "-7", "-7.", "33.5731" — rejects letters,
 * a second dot, a sign in the middle.
 */
const PARTIAL_NUMBER = /^-?\d*\.?\d*$/

function format(value: number): string {
  return value == null || Number.isNaN(value) ? '' : String(value)
}

function clamp(value: number, min?: number, max?: number): number {
  let out = value
  if (min != null) out = Math.max(min, out)
  if (max != null) out = Math.min(max, out)
  return out
}
