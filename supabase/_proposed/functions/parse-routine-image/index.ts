// parse-routine-image — builds a routine from a photo with an LLM and stores it in the CALLER's account.
//
// Security model (B1 fix, 2026-09-25):
//  - Deployed with verify_jwt = true (supabase/_proposed/config.toml). The platform check also lets the anon
//    key and sb_publishable/sb_secret keys through, so the handler resolves the user itself with
//    auth.getUser(jwt) and rejects anything that is not a real, non-anonymous user.
//  - body.user_id is accepted for backward compatibility (RN app) but NEVER used.
//  - All DB access uses a client scoped to the caller's JWT (anon key + Authorization) so RLS applies.
//    The service role key is not used.
//  - The routine is written atomically by the SECURITY INVOKER RPC create_routine_from_import.
//  - Per-user quota (B6) via consume_ai_import_quota.
//
// Response contract (compatible with the RN client, which only reads data.error on 2xx):
//  success: 200 { routine_id, routine_name }
//  failure: 200 { error, code } — except 401 (UNAUTHORIZED) and 405 (METHOD_NOT_ALLOWED).

import { createClient } from 'npm:@supabase/supabase-js@2.116.0'
import {
  countExercises, ERROR_MESSAGES, type ErrorCode, MAX_BODY_CHARS, normalizeBase64,
  normalizeDeclaredMime, parseModelJson, sanitizeRoutine, sniffImageMime,
} from './validation.ts'

const GATEWAY_URL = 'https://ai-gateway.vercel.sh/v1/chat/completions'
const AI_MODEL = 'google/gemini-2.0-flash'
const AI_TIMEOUT_MS = 50_000

const corsHeaders = {
  'Access-Control-Allow-Origin': '*',
  'Access-Control-Allow-Headers': 'authorization, x-client-info, apikey, content-type',
}

const HTTP_STATUS: Partial<Record<ErrorCode, number>> = { UNAUTHORIZED: 401, METHOD_NOT_ALLOWED: 405 }

type CatalogEntry = { id: number; name: string; name_en: string; muscle_group: string }

function json(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { ...corsHeaders, 'Content-Type': 'application/json' },
  })
}

function fail(code: ErrorCode): Response {
  return json({ error: ERROR_MESSAGES[code], code }, HTTP_STATUS[code] ?? 200)
}

// Never log the image, the prompt, tokens or raw upstream/DB bodies.
function log(stage: string, details: Record<string, unknown> = {}): void {
  console.error(JSON.stringify({ fn: 'parse-routine-image', stage, ...details }))
}

// Reads the body stream with a hard byte cap, so a client that omits or lies about
// Content-Length can't force the function to buffer an arbitrarily large payload.
async function readBodyWithLimit(
  body: ReadableStream<Uint8Array>, maxBytes: number,
): Promise<{ ok: true; value: string } | { ok: false }> {
  const reader = body.getReader()
  const chunks: Uint8Array[] = []
  let total = 0
  try {
    while (true) {
      const { done, value } = await reader.read()
      if (done) break
      total += value.byteLength
      if (total > maxBytes) {
        await reader.cancel()
        return { ok: false }
      }
      chunks.push(value)
    }
  } finally {
    reader.releaseLock()
  }
  const merged = new Uint8Array(total)
  let offset = 0
  for (const chunk of chunks) {
    merged.set(chunk, offset)
    offset += chunk.byteLength
  }
  return { ok: true, value: new TextDecoder().decode(merged) }
}

function buildPrompt(exerciseList: CatalogEntry[]): string {
  return `Analizá esta imagen de una rutina de gimnasio.

Tenés disponible esta lista de ejercicios con sus IDs:
${JSON.stringify(exerciseList)}

Tu tarea:
1. Identificá todos los ejercicios de la imagen.
2. Para cada ejercicio, encontrá el ID más cercano en la lista buscando coincidencias por nombre en español o inglés. Si no hay coincidencia exacta, elegí el más similar por grupo muscular y movimiento.
3. Extraé series, repeticiones y descanso. Si vienen en rango (ej: "8-10 reps", "1-2 min"), usá el valor inferior.
4. Si no hay descanso indicado, usá 90 segundos.
5. Si hay múltiples días, extraé cada uno con su nombre tal como aparece en la imagen.
6. Si no hay estructura de días, agrupá todos los ejercicios en un único día llamado "Día 1".

Respondé ÚNICAMENTE con JSON válido, sin texto adicional, con este formato exacto:
{
  "routine_name": "string",
  "days": [
    {
      "day_name": "string",
      "exercises": [
        {
          "exercise_id": number,
          "target_sets": number,
          "target_reps": number,
          "rest_seconds": number,
          "sort_order": number
        }
      ]
    }
  ]
}`
}

async function callModel(
  apiKey: string, dataUrl: string, prompt: string, userId: string,
): Promise<{ ok: true; content: unknown } | { ok: false; code: ErrorCode }> {
  const controller = new AbortController()
  const timer = setTimeout(() => controller.abort(), AI_TIMEOUT_MS)
  try {
    const res = await fetch(GATEWAY_URL, {
      method: 'POST',
      signal: controller.signal,
      headers: { 'Content-Type': 'application/json', 'Authorization': `Bearer ${apiKey}` },
      body: JSON.stringify({
        model: AI_MODEL,
        messages: [{
          role: 'user',
          content: [
            { type: 'image_url', image_url: { url: dataUrl } },
            { type: 'text', text: prompt },
          ],
        }],
        response_format: { type: 'json' },
      }),
    })
    if (!res.ok) {
      await res.body?.cancel()
      log('ai_status', { userId, status: res.status })
      return { ok: false, code: 'AI_UNAVAILABLE' }
    }
    const data = await res.json().catch(() => null)
    return { ok: true, content: data?.choices?.[0]?.message?.content }
  } catch (err) {
    const reason = err instanceof DOMException && err.name === 'AbortError' ? 'timeout' : 'network'
    log('ai_fetch', { userId, reason })
    return { ok: false, code: 'AI_UNAVAILABLE' }
  } finally {
    clearTimeout(timer)
  }
}

async function handle(req: Request): Promise<Response> {
  if (req.method === 'OPTIONS') return new Response('ok', { headers: corsHeaders })
  if (req.method !== 'POST') return fail('METHOD_NOT_ALLOWED')

  // 1. Authenticate the caller (never trust the body).
  const match = /^Bearer\s+(\S+)$/i.exec(req.headers.get('Authorization') ?? '')
  if (!match) return fail('UNAUTHORIZED')
  const jwt = match[1]

  const supabaseUrl = Deno.env.get('SUPABASE_URL')
  const anonKey = Deno.env.get('SUPABASE_ANON_KEY') ?? Deno.env.get('SUPABASE_PUBLISHABLE_KEY')
  const gatewayKey = Deno.env.get('VERCEL_AI_GATEWAY_KEY')
  if (!supabaseUrl || !anonKey || !gatewayKey) {
    log('config', { hasUrl: !!supabaseUrl, hasAnonKey: !!anonKey, hasGatewayKey: !!gatewayKey })
    return fail('INTERNAL')
  }

  const supabase = createClient(supabaseUrl, anonKey, {
    global: { headers: { Authorization: `Bearer ${jwt}` } },
    auth: { persistSession: false, autoRefreshToken: false, detectSessionInUrl: false },
  })
  const { data: userData, error: userError } = await supabase.auth.getUser(jwt)
  const user = userData?.user
  if (userError || !user || user.is_anonymous) return fail('UNAUTHORIZED')
  const userId = user.id

  // 2. Read and validate the body. Reject early via Content-Length when present, but never trust
  //    it alone: the stream read below is capped regardless, in case it is missing or lying.
  const declaredLength = Number(req.headers.get('Content-Length') ?? '')
  if (Number.isFinite(declaredLength) && declaredLength > MAX_BODY_CHARS) return fail('IMAGE_TOO_LARGE')
  if (!req.body) return fail('INVALID_REQUEST')
  const bodyResult = await readBodyWithLimit(req.body, MAX_BODY_CHARS)
  if (!bodyResult.ok) return fail('IMAGE_TOO_LARGE')
  const raw = bodyResult.value
  let body: Record<string, unknown>
  try {
    const parsed = JSON.parse(raw)
    if (!parsed || typeof parsed !== 'object' || Array.isArray(parsed)) return fail('INVALID_REQUEST')
    body = parsed as Record<string, unknown>
  } catch {
    return fail('INVALID_REQUEST')
  }
  if (body.user_id !== undefined && body.user_id !== userId) log('user_id_ignored', { userId })

  if (!normalizeDeclaredMime(body.image_mime_type)) return fail('UNSUPPORTED_IMAGE_TYPE')
  const image = normalizeBase64(body.image_base64)
  if (!image.ok) return fail(image.code)
  const mime = sniffImageMime(image.value)
  if (!mime) return fail('INVALID_IMAGE')

  // 3. Quota (B6). Consumed before the paid AI call.
  const { data: allowed, error: quotaError } = await supabase.rpc('consume_ai_import_quota')
  if (quotaError) {
    log('quota', { userId, code: quotaError.code })
    return fail('INTERNAL')
  }
  if (allowed !== true) return fail('RATE_LIMITED')

  // 4. Exercise catalog (readable by any authenticated user).
  const { data: exercises, error: exError } = await supabase
    .from('exercises')
    .select('id, name, name_en, muscle_groups(name)')
    .order('name')
  if (exError || !exercises || exercises.length === 0) {
    log('exercises', { userId, code: exError?.code ?? 'empty' })
    return fail('INTERNAL')
  }
  const catalog: CatalogEntry[] = (exercises as unknown as Array<Record<string, unknown>>).map((e) => {
    const mg = e.muscle_groups as { name?: string } | Array<{ name?: string }> | null
    const mgName = Array.isArray(mg) ? mg[0]?.name : mg?.name
    return { id: Number(e.id), name: String(e.name), name_en: String(e.name_en), muscle_group: mgName ?? '' }
  })
  const validIds = new Set(catalog.map((e) => e.id))

  // 5. Ask the model and sanitize its answer.
  const ai = await callModel(gatewayKey, `data:${mime};base64,${image.value}`, buildPrompt(catalog), userId)
  if (!ai.ok) return fail(ai.code)
  const parsedModel = parseModelJson(ai.content)
  if (parsedModel === null) {
    log('ai_parse', { userId })
    return fail('AI_PARSE_FAILED')
  }
  const clean = sanitizeRoutine(parsedModel, validIds)
  if (!clean) {
    log('ai_shape', { userId })
    return fail('AI_PARSE_FAILED')
  }
  if (clean.dropped > 0) log('unknown_exercises_dropped', { userId, dropped: clean.dropped })
  if (countExercises(clean.routine) === 0) return fail('NO_EXERCISES_FOUND')

  // 6. Persist atomically under the caller's RLS.
  const { data: created, error: rpcError } = await supabase.rpc('create_routine_from_import', {
    p_name: clean.routine.name,
    p_days: clean.routine.days,
  })
  if (rpcError) {
    log('persist', { userId, code: rpcError.code })
    return fail(rpcError.code === '22023' ? 'NO_EXERCISES_FOUND' : 'INTERNAL')
  }
  const result = created as { routine_id?: unknown; routine_name?: unknown } | null
  if (!result || typeof result.routine_id !== 'string') {
    log('persist_shape', { userId })
    return fail('INTERNAL')
  }
  return json({
    routine_id: result.routine_id,
    routine_name: typeof result.routine_name === 'string' ? result.routine_name : clean.routine.name,
  })
}

Deno.serve(async (req) => {
  try {
    return await handle(req)
  } catch (err) {
    log('unhandled', { message: err instanceof Error ? err.message.slice(0, 200) : 'unknown' })
    return fail('INTERNAL')
  }
})
