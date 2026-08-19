/**
 * The API client throws errors shaped like "409 Conflict: {json body}".
 * This pulls out the backend's human-readable {@code message} field so the
 * UI can show "Camera id 'X' already exists" instead of the raw HTTP line.
 */
export function extractApiError(err: unknown, fallback = 'Request failed'): string {
  if (!(err instanceof Error)) return fallback
  const match = err.message.match(/^\d+\s+\S+:\s*(.*)$/s)
  if (!match) return err.message
  try {
    const parsed = JSON.parse(match[1]) as { message?: string }
    return parsed.message ?? match[1]
  } catch {
    return match[1] || fallback
  }
}
