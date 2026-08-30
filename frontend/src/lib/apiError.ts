/**
 * The API client throws errors shaped like "409 Conflict: {json body}".
 * This pulls out the backend's human-readable {@code message} field so the
 * UI can show "Camera id 'X' already exists" instead of the raw HTTP line.
 */
/**
 * The HTTP status the API client recorded, or null when the request never
 * got a response at all — a server that is down or still starting, a DNS
 * or network failure, a CORS rejection.
 *
 * That distinction matters wherever the two would otherwise be reported as
 * the same thing. "401" means the server answered and rejected the
 * credentials; null means nobody answered, which is not the user's fault
 * and needs different advice.
 */
export function httpStatusOf(err: unknown): number | null {
  if (!(err instanceof Error)) return null
  const match = err.message.match(/^(\d{3})\s/)
  return match ? Number(match[1]) : null
}

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
