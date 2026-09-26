// Pure helpers for parse-routine-image (no I/O) so they can be unit-tested with `deno test`.

export type ErrorCode =
  | 'UNAUTHORIZED' | 'METHOD_NOT_ALLOWED' | 'INVALID_REQUEST' | 'IMAGE_TOO_LARGE'
  | 'INVALID_IMAGE' | 'UNSUPPORTED_IMAGE_TYPE' | 'RATE_LIMITED' | 'AI_UNAVAILABLE'
  | 'AI_PARSE_FAILED' | 'NO_EXERCISES_FOUND' | 'INTERNAL'

// User-facing messages (Spanish, shown as-is by the RN app). Never include internal details.
export const ERROR_MESSAGES: Record<ErrorCode, string> = {
  UNAUTHORIZED: 'No autorizado. Iniciá sesión nuevamente.',
  METHOD_NOT_ALLOWED: 'Método no permitido.',
  INVALID_REQUEST: 'Solicitud inválida.',
  IMAGE_TOO_LARGE: 'La imagen es demasiado grande (máximo 5 MB).',
  INVALID_IMAGE: 'La imagen no es válida.',
  UNSUPPORTED_IMAGE_TYPE: 'Formato de imagen no soportado. Usá JPEG, PNG, WEBP o HEIC.',
  RATE_LIMITED: 'Alcanzaste el límite diario de importaciones con IA. Probá de nuevo mañana.',
  AI_UNAVAILABLE: 'No pudimos analizar la imagen en este momento. Probá de nuevo más tarde.',
  AI_PARSE_FAILED: 'No pudimos interpretar la rutina de la imagen.',
  NO_EXERCISES_FOUND: 'No se pudieron extraer ejercicios de la imagen.',
  INTERNAL: 'Error interno. Probá de nuevo más tarde.',
}

export const MAX_BODY_CHARS = 8_000_000
export const MAX_BASE64_CHARS = 7_000_000 // ~5.25 MB decoded
export const MIN_BASE64_CHARS = 100
export const MAX_DAYS = 7
export const MAX_EXERCISES_PER_DAY = 30
export const MAX_ROUTINE_NAME = 50
export const MAX_DAY_NAME = 50
export const DEFAULT_ROUTINE_NAME = 'Rutina importada'
export const SETS = { def: 3, min: 1, max: 20 } as const
export const REPS = { def: 10, min: 1, max: 100 } as const
export const REST = { def: 90, min: 15, max: 600 } as const

const BASE64_RE = /^[A-Za-z0-9+/]+={0,2}$/
const DECLARED_MIMES = new Set(['image/jpeg', 'image/png', 'image/webp', 'image/heic', 'image/heif'])
const HEIF_BRANDS = new Set(['heic', 'heix', 'hevc', 'hevx', 'heim', 'heis', 'mif1', 'msf1'])

export interface CleanExercise { exercise_id: number; target_sets: number; target_reps: number; rest_seconds: number }
export interface CleanDay { day_name: string; exercises: CleanExercise[] }
export interface CleanRoutine { name: string; days: CleanDay[] }
export type Base64Result =
  | { ok: true; value: string }
  | { ok: false; code: 'INVALID_REQUEST' | 'IMAGE_TOO_LARGE' | 'INVALID_IMAGE' }

export function toInt(value: unknown, def: number, min: number, max: number): number {
  const n = typeof value === 'number'
    ? value
    : typeof value === 'string' && value.trim() !== '' ? Number(value.trim()) : Number.NaN
  if (!Number.isFinite(n)) return def
  return Math.min(max, Math.max(min, Math.floor(n)))
}

// Lone (unpaired) UTF-16 surrogates: Postgres' jsonb (and JSON.stringify -> valid UTF-8) rejects them.
const LONE_SURROGATE_RE = /[\uD800-\uDBFF](?![\uDC00-\uDFFF])|(?<![\uD800-\uDBFF])[\uDC00-\uDFFF]/g

export function cleanText(value: unknown, max: number): string {
  if (typeof value !== 'string') return ''
  const collapsed = value.replace(/\p{Cc}+/gu, ' ').replace(/\s+/g, ' ').trim().replace(LONE_SURROGATE_RE, '')
  // Truncate by Unicode code points, never by UTF-16 code unit, so a surrogate pair (e.g. an
  // emoji) straddling the `max` boundary is dropped whole instead of split into an invalid half.
  return Array.from(collapsed).slice(0, max).join('').trim()
}

/** Declared mime from the client. Missing -> image/jpeg (RN default). Unsupported -> null. */
export function normalizeDeclaredMime(value: unknown): string | null {
  if (value === undefined || value === null || value === '') return 'image/jpeg'
  if (typeof value !== 'string') return null
  const m = value.trim().toLowerCase()
  const normalized = m === 'image/jpg' ? 'image/jpeg' : m
  return DECLARED_MIMES.has(normalized) ? normalized : null
}

export function normalizeBase64(value: unknown): Base64Result {
  if (typeof value !== 'string' || value.length === 0) return { ok: false, code: 'INVALID_REQUEST' }
  let s = value
  if (s.startsWith('data:')) {
    const comma = s.indexOf(',')
    if (comma < 0) return { ok: false, code: 'INVALID_IMAGE' }
    s = s.slice(comma + 1)
  }
  s = s.replace(/\s+/g, '')
  if (s.length > MAX_BASE64_CHARS) return { ok: false, code: 'IMAGE_TOO_LARGE' }
  if (s.length < MIN_BASE64_CHARS || s.length % 4 !== 0 || !BASE64_RE.test(s)) {
    return { ok: false, code: 'INVALID_IMAGE' }
  }
  return { ok: true, value: s }
}

/** Detects the real image type from magic bytes. Returns null when not an allowed format. */
export function sniffImageMime(base64: string): string | null {
  let head: Uint8Array
  try {
    head = Uint8Array.from(atob(base64.slice(0, 32)), (c) => c.charCodeAt(0))
  } catch {
    return null
  }
  const ascii = (from: number, to: number) => String.fromCharCode(...head.subarray(from, to))
  if (head.length >= 3 && head[0] === 0xff && head[1] === 0xd8 && head[2] === 0xff) return 'image/jpeg'
  const png = [0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a]
  if (head.length >= 8 && png.every((b, i) => head[i] === b)) return 'image/png'
  if (head.length >= 12 && ascii(0, 4) === 'RIFF' && ascii(8, 12) === 'WEBP') return 'image/webp'
  if (head.length >= 12 && ascii(4, 8) === 'ftyp' && HEIF_BRANDS.has(ascii(8, 12))) return 'image/heic'
  return null
}

/** Parses the model output (optionally wrapped in ``` fences). Null when not valid JSON. */
export function parseModelJson(content: unknown): unknown | null {
  if (typeof content !== 'string') return null
  const s = content.replace(/^```(?:json)?\s*/i, '').replace(/\s*```$/, '').trim()
  try {
    return JSON.parse(s)
  } catch {
    return null
  }
}

/**
 * Validates and normalizes the model output. Unknown exercise ids are dropped (counted in `dropped`).
 * Returns null when the overall shape is wrong (not an object / days not an array).
 * Array order defines sort_order (the DB function assigns positions).
 */
export function sanitizeRoutine(parsed: unknown, validIds: Set<number>): { routine: CleanRoutine; dropped: number } | null {
  if (!parsed || typeof parsed !== 'object' || Array.isArray(parsed)) return null
  const p = parsed as Record<string, unknown>
  if (!Array.isArray(p.days)) return null
  let dropped = 0
  const days: CleanDay[] = p.days.slice(0, MAX_DAYS).map((d, i) => {
    const day = d && typeof d === 'object' && !Array.isArray(d) ? d as Record<string, unknown> : {}
    const rawExercises = Array.isArray(day.exercises) ? day.exercises : []
    const exercises: CleanExercise[] = []
    for (const e of rawExercises) {
      if (exercises.length >= MAX_EXERCISES_PER_DAY) break
      const ex = e && typeof e === 'object' && !Array.isArray(e) ? e as Record<string, unknown> : null
      const id = ex ? Number(ex.exercise_id) : Number.NaN
      if (!ex || !Number.isInteger(id) || !validIds.has(id)) {
        dropped++
        continue
      }
      exercises.push({
        exercise_id: id,
        target_sets: toInt(ex.target_sets, SETS.def, SETS.min, SETS.max),
        target_reps: toInt(ex.target_reps, REPS.def, REPS.min, REPS.max),
        rest_seconds: toInt(ex.rest_seconds, REST.def, REST.min, REST.max),
      })
    }
    return { day_name: cleanText(day.day_name, MAX_DAY_NAME) || `Día ${i + 1}`, exercises }
  })
  return { routine: { name: cleanText(p.routine_name, MAX_ROUTINE_NAME) || DEFAULT_ROUTINE_NAME, days }, dropped }
}

export function countExercises(routine: CleanRoutine): number {
  return routine.days.reduce((acc, d) => acc + d.exercises.length, 0)
}
