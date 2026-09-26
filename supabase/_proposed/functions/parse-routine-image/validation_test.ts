import { assertEquals } from 'jsr:@std/assert@1'
import {
  cleanText, countExercises, normalizeBase64, normalizeDeclaredMime, parseModelJson,
  sanitizeRoutine, sniffImageMime, toInt,
} from './validation.ts'

// Builds a base64 string long/valid enough to pass length checks (multiple of 4, >= 100 chars).
const b64 = (bytes: number[], pad = 120) => btoa(String.fromCharCode(...bytes, ...new Array(pad).fill(0)))

// ---------------------------------------------------------------------------------------------
// toInt
// ---------------------------------------------------------------------------------------------
Deno.test('toInt: numeric string is parsed and floored', () => {
  assertEquals(toInt('8', 0, 0, 100), 8)
})
Deno.test('toInt: number is floored', () => {
  assertEquals(toInt(8.9, 0, 0, 100), 8)
})
Deno.test('toInt: below min is clamped', () => {
  assertEquals(toInt(-3, 5, 1, 100), 1)
})
Deno.test('toInt: non-numeric string returns default', () => {
  assertEquals(toInt('abc', 7, 1, 100), 7)
})
Deno.test('toInt: above max is clamped', () => {
  assertEquals(toInt(1e9, 3, 1, 20), 20)
})
Deno.test('toInt: null returns default', () => {
  assertEquals(toInt(null, 9, 1, 100), 9)
})

// ---------------------------------------------------------------------------------------------
// normalizeDeclaredMime
// ---------------------------------------------------------------------------------------------
Deno.test('normalizeDeclaredMime: undefined defaults to jpeg', () => {
  assertEquals(normalizeDeclaredMime(undefined), 'image/jpeg')
})
Deno.test('normalizeDeclaredMime: padded uppercase jpg normalizes to jpeg', () => {
  assertEquals(normalizeDeclaredMime(' IMAGE/JPG '), 'image/jpeg')
})
Deno.test('normalizeDeclaredMime: unsupported mime returns null', () => {
  assertEquals(normalizeDeclaredMime('image/gif'), null)
})
Deno.test('normalizeDeclaredMime: non-string returns null', () => {
  assertEquals(normalizeDeclaredMime(123), null)
})
Deno.test('normalizeDeclaredMime: heif is accepted as-is', () => {
  assertEquals(normalizeDeclaredMime('image/heif'), 'image/heif')
})

// ---------------------------------------------------------------------------------------------
// normalizeBase64
// ---------------------------------------------------------------------------------------------
Deno.test('normalizeBase64: non-string returns INVALID_REQUEST', () => {
  assertEquals(normalizeBase64(42), { ok: false, code: 'INVALID_REQUEST' })
})
Deno.test('normalizeBase64: empty string returns INVALID_REQUEST', () => {
  assertEquals(normalizeBase64(''), { ok: false, code: 'INVALID_REQUEST' })
})
Deno.test('normalizeBase64: data URL prefix is stripped', () => {
  const png = b64([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a])
  const result = normalizeBase64(`data:image/png;base64,${png}`)
  assertEquals(result, { ok: true, value: png })
})
Deno.test('normalizeBase64: whitespace inside a valid string is stripped', () => {
  const raw = b64([0x01, 0x02, 0x03])
  const withNewlines = raw.slice(0, 4) + '\n' + raw.slice(4)
  const result = normalizeBase64(withNewlines)
  assertEquals(result, { ok: true, value: raw })
})
Deno.test('normalizeBase64: too long returns IMAGE_TOO_LARGE', () => {
  const result = normalizeBase64('A'.repeat(7_000_004))
  assertEquals(result, { ok: false, code: 'IMAGE_TOO_LARGE' })
})
Deno.test('normalizeBase64: too short returns INVALID_IMAGE', () => {
  assertEquals(normalizeBase64('AAA'), { ok: false, code: 'INVALID_IMAGE' })
})
Deno.test('normalizeBase64: invalid characters return INVALID_IMAGE', () => {
  const result = normalizeBase64('@@@@' + 'A'.repeat(200))
  assertEquals(result, { ok: false, code: 'INVALID_IMAGE' })
})
Deno.test('normalizeBase64: length not a multiple of 4 returns INVALID_IMAGE', () => {
  const result = normalizeBase64('A'.repeat(101))
  assertEquals(result, { ok: false, code: 'INVALID_IMAGE' })
})

// ---------------------------------------------------------------------------------------------
// sniffImageMime
// ---------------------------------------------------------------------------------------------
Deno.test('sniffImageMime: JPEG magic bytes', () => {
  assertEquals(sniffImageMime(b64([0xff, 0xd8, 0xff, 0xe0])), 'image/jpeg')
})
Deno.test('sniffImageMime: PNG signature', () => {
  assertEquals(sniffImageMime(b64([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a])), 'image/png')
})
Deno.test('sniffImageMime: RIFF/WEBP', () => {
  const bytes = [0x52, 0x49, 0x46, 0x46, 0, 0, 0, 0, 0x57, 0x45, 0x42, 0x50]
  assertEquals(sniffImageMime(b64(bytes)), 'image/webp')
})
Deno.test('sniffImageMime: ftyp/heic', () => {
  const bytes = [0, 0, 0, 0x18, 0x66, 0x74, 0x79, 0x70, 0x68, 0x65, 0x69, 0x63]
  assertEquals(sniffImageMime(b64(bytes)), 'image/heic')
})
Deno.test('sniffImageMime: ftyp/avif is not an allowed brand', () => {
  const bytes = [0, 0, 0, 0x18, 0x66, 0x74, 0x79, 0x70, 0x61, 0x76, 0x69, 0x66]
  assertEquals(sniffImageMime(b64(bytes)), null)
})
Deno.test('sniffImageMime: GIF signature returns null', () => {
  const bytes = [0x47, 0x49, 0x46, 0x38, 0x39, 0x61]
  assertEquals(sniffImageMime(b64(bytes)), null)
})

// ---------------------------------------------------------------------------------------------
// parseModelJson
// ---------------------------------------------------------------------------------------------
Deno.test('parseModelJson: strips markdown fences', () => {
  assertEquals(parseModelJson('```json\n{"a":1}\n```'), { a: 1 })
})
Deno.test('parseModelJson: non-string input returns null', () => {
  assertEquals(parseModelJson(42), null)
})
Deno.test('parseModelJson: invalid JSON returns null', () => {
  assertEquals(parseModelJson('nope'), null)
})

// ---------------------------------------------------------------------------------------------
// sanitizeRoutine
// ---------------------------------------------------------------------------------------------
Deno.test('sanitizeRoutine: null returns null', () => {
  assertEquals(sanitizeRoutine(null, new Set()), null)
})
Deno.test('sanitizeRoutine: array returns null', () => {
  assertEquals(sanitizeRoutine([], new Set()), null)
})
Deno.test('sanitizeRoutine: non-array days returns null', () => {
  assertEquals(sanitizeRoutine({ days: 'x' }, new Set()), null)
})
Deno.test('sanitizeRoutine: unknown exercise ids are dropped and counted', () => {
  const parsed = { routine_name: 'R', days: [{ day_name: 'D1', exercises: [{ exercise_id: 1 }, { exercise_id: 999 }] }] }
  const result = sanitizeRoutine(parsed, new Set([1]))
  assertEquals(result?.dropped, 1)
  assertEquals(result?.routine.days[0].exercises.length, 1)
  assertEquals(result?.routine.days[0].exercises[0].exercise_id, 1)
})
Deno.test('sanitizeRoutine: numeric-string exercise_id is accepted when valid', () => {
  const parsed = { days: [{ day_name: 'D1', exercises: [{ exercise_id: '5' }] }] }
  const result = sanitizeRoutine(parsed, new Set([5]))
  assertEquals(result?.dropped, 0)
  assertEquals(result?.routine.days[0].exercises[0].exercise_id, 5)
})
Deno.test('sanitizeRoutine: non-integer exercise_id is dropped', () => {
  const parsed = { days: [{ day_name: 'D1', exercises: [{ exercise_id: 5.5 }] }] }
  const result = sanitizeRoutine(parsed, new Set([5]))
  assertEquals(result?.dropped, 1)
  assertEquals(result?.routine.days[0].exercises.length, 0)
})
Deno.test('sanitizeRoutine: out-of-range values are clamped', () => {
  const parsed = { days: [{ day_name: 'D1', exercises: [{ exercise_id: 1, target_sets: 99, target_reps: 0, rest_seconds: 5 }] }] }
  const result = sanitizeRoutine(parsed, new Set([1]))
  const ex = result?.routine.days[0].exercises[0]
  assertEquals(ex?.target_sets, 20)
  assertEquals(ex?.target_reps, 1)
  assertEquals(ex?.rest_seconds, 15)
})
Deno.test('sanitizeRoutine: missing values take the defaults', () => {
  const parsed = { days: [{ day_name: 'D1', exercises: [{ exercise_id: 1 }] }] }
  const result = sanitizeRoutine(parsed, new Set([1]))
  const ex = result?.routine.days[0].exercises[0]
  assertEquals(ex?.target_sets, 3)
  assertEquals(ex?.target_reps, 10)
  assertEquals(ex?.rest_seconds, 90)
})
Deno.test('sanitizeRoutine: more than 7 days is clamped to 7', () => {
  const days = Array.from({ length: 9 }, (_, i) => ({ day_name: `D${i}`, exercises: [] }))
  const result = sanitizeRoutine({ days }, new Set())
  assertEquals(result?.routine.days.length, 7)
})
Deno.test('sanitizeRoutine: more than 30 exercises per day is clamped to 30', () => {
  const exercises = Array.from({ length: 40 }, () => ({ exercise_id: 1 }))
  const result = sanitizeRoutine({ days: [{ day_name: 'D1', exercises }] }, new Set([1]))
  assertEquals(result?.routine.days[0].exercises.length, 30)
})
Deno.test('sanitizeRoutine: empty routine name falls back to the default', () => {
  const result = sanitizeRoutine({ days: [] }, new Set())
  assertEquals(result?.routine.name, 'Rutina importada')
})
Deno.test('sanitizeRoutine: routine name longer than 50 chars is truncated', () => {
  const result = sanitizeRoutine({ routine_name: 'A'.repeat(80), days: [] }, new Set())
  assertEquals(result?.routine.name.length, 50)
})
Deno.test('sanitizeRoutine: missing day name at index 1 defaults to "Día 2"', () => {
  const result = sanitizeRoutine({ days: [{ day_name: 'A', exercises: [] }, { exercises: [] }] }, new Set())
  assertEquals(result?.routine.days[1].day_name, 'Día 2')
})
Deno.test('sanitizeRoutine: control characters are stripped from day names', () => {
  const result = sanitizeRoutine({ days: [{ day_name: '\u0001Test', exercises: [] }] }, new Set())
  assertEquals(result?.routine.days[0].day_name, 'Test')
})

// ---------------------------------------------------------------------------------------------
// countExercises
// ---------------------------------------------------------------------------------------------
Deno.test('countExercises: sums exercises across all days', () => {
  const routine = {
    name: 'R',
    days: [
      { day_name: 'D1', exercises: [{ exercise_id: 1, target_sets: 3, target_reps: 10, rest_seconds: 90 }] },
      { day_name: 'D2', exercises: [] },
      {
        day_name: 'D3',
        exercises: [
          { exercise_id: 2, target_sets: 3, target_reps: 10, rest_seconds: 90 },
          { exercise_id: 3, target_sets: 3, target_reps: 10, rest_seconds: 90 },
        ],
      },
    ],
  }
  assertEquals(countExercises(routine), 3)
})

// Sanity check for cleanText, used indirectly above.
Deno.test('cleanText: collapses whitespace and truncates', () => {
  assertEquals(cleanText('  a   b  ', 50), 'a b')
  assertEquals(cleanText('x'.repeat(60), 50).length, 50)
})
Deno.test('cleanText: truncates by code points, never splitting a surrogate pair at the boundary', () => {
  // 'abcd' + U+1F600 (surrogate pair) + 'e': 5 code points, but 6 UTF-16 code units.
  const result = cleanText('abcd\u{1F600}e', 5)
  assertEquals(result, 'abcd\u{1F600}')
  assertEquals([...result].length, 5)
})
Deno.test('cleanText: strips a lone high surrogate', () => {
  assertEquals(cleanText('\uD800abc', 50), 'abc')
})
Deno.test('cleanText: strips a lone low surrogate', () => {
  assertEquals(cleanText('abc\uDC00def', 50), 'abcdef')
})
