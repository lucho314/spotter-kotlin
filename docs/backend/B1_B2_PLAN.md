# Plan: corrección de B1 (edge function `parse-routine-image`) y B2 (rutinas compartidas legibles sin login), con B6 (cuota de IA) incluido

**NO APLICADO. Revisado y aprobado el 2026-09-25; el usuario decidió después no modificar el backend.** El código de este plan vive, sin aplicar ni desplegar, en `supabase/_proposed/` (ver `supabase/_proposed/README.md`). El backend en vivo sigue siendo el descrito en `B1_B2_context.md`, y `docs/MIGRATION_PLAN.md` (sección 8) documenta B1/B2/B3-B6 como hallazgos abiertos, no como implementados.

Todo lo que sigue lo verifiqué leyendo el código. `B1_B2_context.md` es la fuente de verdad sobre el estado en vivo. El implementador solo crea y edita archivos; el orquestador aplica y despliega con el MCP de Supabase (proyecto `bqqpfldwzkfdvlvfjkbr`, según `E:\Spoter Kotlin\.mcp.json`).

**Hallazgos que cambian el diseño:**
1. **Hueco extra en la RLS.** `shared_insert_own` y `shared_update_own` solo comprueban `auth.uid() = shared_by`, no que la rutina sea tuya. Un usuario logueado puede crear un share, o cambiar el `routine_id` de uno suyo, apuntando a una rutina ajena y después leerla. Se cierra en el paso 1.
2. **La app RN no muestra el texto de los errores no-2xx.** `supabase-js` `functions.invoke` lanza `FunctionsHttpError(response)` y `error.context` es un `Response`. Lo comprobé en el código de functions-js. Por eso `context.json.error` en `E:\Spotter\services\ai-routine-import.ts:18` siempre es `undefined` y la app muestra "Edge Function returned a non-2xx status code". Por compatibilidad, los errores manejados se devuelven con **200 y `{error, code}`** (la v10 en vivo ya responde 200). Solo 401 y 405 usan su código HTTP real.
3. **`verify_jwt=true` no alcanza.** El chequeo de la plataforma deja pasar la anon key legacy (es un JWT válido) y también las keys `sb_publishable_`/`sb_secret_` en `Authorization` (docs de Supabase, "auth-headers"). Por eso el handler también tiene que validar al usuario con `auth.getUser(jwt)`.

---

## 1. Objetivo (criterios de aceptación)

1. Existen estos archivos:
   - `E:\Spoter Kotlin\supabase\config.toml`
   - `E:\Spoter Kotlin\supabase\functions\parse-routine-image\index.ts`
   - `E:\Spoter Kotlin\supabase\functions\parse-routine-image\validation.ts`
   - `E:\Spoter Kotlin\supabase\functions\parse-routine-image\validation_test.ts`
   - `E:\Spoter Kotlin\supabase\migrations\20260925120000_b2_restrict_shared_routine_reads.sql`
   - `E:\Spoter Kotlin\supabase\migrations\20260925120100_b2_shared_routine_rpcs.sql`
   - `E:\Spoter Kotlin\supabase\migrations\20260925120200_b1_create_routine_from_import_rpc.sql`
   - `E:\Spoter Kotlin\supabase\migrations\20260925120300_b6_ai_import_rate_limit.sql`
   - `E:\Spoter Kotlin\supabase\migrations_pending\b2_step2_drop_direct_shared_reads.sql` (NO se aplica)
   - `E:\Spoter Kotlin\supabase\rollback\20260925_b1_b2_rollback.sql` (NO se aplica)
2. `npx -y deno@2.9.6 test` sobre `validation_test.ts` pasa, y `npx -y deno@2.9.6 check` sobre `index.ts` no da errores.
3. Después de que el orquestador aplique y verifique (sección 5):
   - Con el rol `anon`: 0 filas en `shared_routines`, `routines`, `routine_exercises` y `routine_days`, y sin permiso EXECUTE sobre las 5 RPC nuevas.
   - Un usuario autenticado que no es el dueño:
     - sigue pudiendo hacer la lectura anidada de la app RN mientras el share está activo y no vencido;
     - `get_shared_routine` devuelve el preview sin ids de usuario ni de rutina;
     - `import_shared_routine` copia la rutina a su cuenta con el sufijo " (importada)";
     - no puede compartir una rutina ajena (ni por RPC ni con insert directo).
   - Un share vencido no se ve ni se importa.
   - Llamar a la función sin `Authorization` devuelve 401. Con la anon key como bearer devuelve 401 `{"code":"UNAUTHORIZED"}` y no crea nada.
   - `get_advisors(security)` no muestra `function_search_path_mutable` ni `anon_security_definer_function_executable` para las funciones nuevas.
4. `docs/MIGRATION_PLAN.md` queda actualizado según el paso 8.
5. `E:\Spotter` no se toca.

---

## 2. Contexto

- `E:\Spoter Kotlin\docs\backend\B1_B2_context.md` es la fuente de verdad del estado en vivo:
  - la función está en `verify_jwt:false`;
  - las políticas usan el rol `{public}`;
  - `routine_days_select_own` es una política combinada (dueño OR compartida);
  - `expires_at` se ignora.
- `E:\Spotter\supabase\functions\parse-routine-image\index.ts` es la función vulnerable:
  - confía en `user_id` del body y usa la service role;
  - hace inserts no transaccionales;
  - devuelve el texto del gateway.
- `E:\Spotter\services\sharing.ts`: la app RN crea shares con insert directo (con un código de 8 caracteres de `Math.random`) y lee con `select('*, routines(*, routine_days(*), routine_exercises(*, exercises(*, muscle_groups(*))))')`. **Eso tiene que seguir funcionando para usuarios autenticados.**
- `E:\Spotter\services\ai-routine-import.ts`: lee `data.error` y `data.routine_id` / `data.routine_name`.
- `E:\Spotter\database\01_schema.sql` está desactualizado:
  - `routine_exercises` tenía `UNIQUE (routine_id, exercise_id)`, que puede seguir en vivo (el plan Kotlin tiene `EXERCISE_ALREADY_IN_ROUTINE`);
  - `routines.days_per_week` tiene CHECK 1..7;
  - `shared_routines.share_code` es UNIQUE;
  - el trigger `generate_share_code` usa `gen_random_bytes` solo si el código viene NULL;
  - `routine_days(id, routine_id, day_number, name)` existe en vivo (sección 6 del plan).
- `E:\Spoter Kotlin\docs\MIGRATION_PLAN.md`:
  - §6 DTOs: líneas 310-312, 327-329 y 334
  - §7 ítem 21: líneas 378-382
  - §8: líneas 413-427
  - §9.2 ErrorMapper: línea 503
  - §9.3 `ShareCode`: línea 558
  - §9.4: líneas 641-646 y 673
  - §9.5: líneas 699-703 y 708
  - test de `ShareCodeTest`: línea 1036
  - FASE 6: líneas 1178-1189 y 1198
  - §13 ítem 3: línea 1275
  - `Validators`: nombre 1..50, sets 1..20, reps 1..100, descanso 15..600 (los límites del servidor se alinean con estos valores).
- Herramientas: no hay `deno` ni `supabase` CLI instalados; sí hay node/npx. El paquete npm `deno` existe (versión latest 2.9.6), así que se usa `npx -y deno@2.9.6 ...`. Para `@supabase/supabase-js` se fija `2.116.0` (no la latest `2.117.2`): Deno 2.9.6 bloquea por defecto los paquetes npm publicados hace menos de 24 h (política de antigüedad mínima) y `2.117.2` cayó dentro de esa ventana el día de la implementación; `2.116.0` (2026-09-07) ya es lo bastante vieja y deja pasar `deno check` sin flags.

---

## 3. Diseño

### 3.1 Base de datos: paso 1 de B2 (se aplica ya)

- **Políticas.** Las de lectura compartida pasan a `TO authenticated` y exigen `is_active and (expires_at is null or expires_at > now())`:
  - `shared_select_by_code`
  - `routines_select_shared`
  - `re_select_shared`
  - `routine_days_select_shared`: nueva. `routine_days_select_own` queda solo con la condición de dueño.
- **Ownership.** `shared_insert_own` y `shared_update_own` exigen además que la rutina sea del usuario.
- **Patrón.** Se usa `(select auth.uid())`, como recomienda el advisor.

**RPCs.** Todas llevan `set search_path = ''` y nombres totalmente calificados, `revoke all ... from public, anon` y `grant execute ... to authenticated`. Todas rechazan `auth.uid()` nulo y usuarios anónimos (`auth.jwt()->>'is_anonymous'`).

| Función | Seguridad | Qué hace |
|---|---|---|
| `get_shared_routine(p_code text) returns jsonb` | SECURITY DEFINER, STABLE | Busca solo por código exacto, sin enumeración. Devuelve `null` si el código tiene formato inválido, no existe, está inactivo o vencido. |
| `import_shared_routine(p_code text) returns uuid` | SECURITY DEFINER | Copia en una sola transacción a la cuenta de `auth.uid()`. Error `P0002 share_not_found` si no hay share válido. |
| `create_share(p_routine_id uuid) returns text` | SECURITY INVOKER | Ver decisión más abajo. |
| `create_routine_from_import(p_name text, p_days jsonb) returns jsonb` | SECURITY INVOKER | La usa la edge function para escribir de forma atómica bajo RLS (B1). |
| `consume_ai_import_quota() returns boolean` | SECURITY DEFINER | Cuota de B6. |

JSON que devuelve `get_shared_routine`:
```json
{ "share_code":"...", "expires_at":null,
  "routine": { "name":"...", "description":null, "days_per_week":3,
    "days":[{"day_number":1,"name":"..."}],
    "exercises":[{"exercise_id":1,"day_number":1,"sort_order":0,"target_sets":3,"target_reps":10,"rest_seconds":90,
                  "exercise_name":"...","exercise_name_en":"...","muscle_group":"..."}] } }
```

**Decisión sobre `create_share`: la generación del código pasa al servidor.** Motivos:
1. Reutilizar el share activo y crear uno nuevo ocurre de forma atómica, con un advisory lock por rutina.
2. El código es fuerte: sale de `gen_random_uuid()`, que usa `pg_strong_random`, está en `pg_catalog` y no depende de pgcrypto. Se usan los bytes 0-5 y 10-11, que no llevan bits de versión ni de variante. Como 256 es múltiplo de 32, no hay sesgo.
3. La colisión (`23505`) se reintenta dentro de la función, hasta 5 veces.
4. Es SECURITY INVOKER, así que la RLS sigue aplicando. No hace falta DEFINER.
5. El cliente Kotlin se simplifica: sin `SecureRandom`, sin reintentos y sin `findActiveShare`.

El formato del código es el mismo que el de la app RN (8 caracteres de `ABCDEFGHJKLMNPQRSTUVWXYZ23456789`), así que `ShareCode.parse` lo acepta. La app RN sigue usando su insert directo, que ahora exige ser dueño de la rutina.

**Por qué `create_routine_from_import` es INVOKER y no inserts desde TypeScript con compensación:** así el alta es transaccional de verdad y aplica la RLS del llamador. Además valida `exercise_id` contra `public.exercises` con un JOIN (defensa en profundidad), limita los valores a rangos válidos y usa `on conflict do nothing` para aguantar el UNIQUE que pueda haber en vivo. Si queda 0 ejercicios, lanza `22023 no_valid_exercises` y hace rollback de todo. Es tan poderosa como los inserts directos que la RLS ya permite, así que exponerla a `authenticated` no agrega superficie.

**Cuota de B6: la incluyo porque es barata.** La tabla `public.ai_import_usage` tiene RLS activada, ninguna política y `revoke all` para anon y authenticated; solo la toca la función DEFINER. El límite es **20 intentos por usuario cada 24 h, en ventana móvil**, con advisory lock por usuario. La función además borra las filas del usuario con más de 2 días. El cupo se consume antes de llamar a la IA, aunque la importación falle después: así se frena el abuso de costo.

**Advertencias que se aceptan a propósito.** El advisor puede marcar como WARN `authenticated_security_definer_function_executable` en `get_shared_routine`, `import_shared_routine` y `consume_ai_import_quota`: es intencional, porque solo `authenticated` puede ejecutarlas y todas verifican `auth.uid()`. También puede aparecer INFO `rls_enabled_no_policy` en `ai_import_usage`, también intencional.

### 3.2 Base de datos: paso 2 de B2 (NO se aplica ahora)

`migrations_pending/b2_step2_drop_direct_shared_reads.sql` borra las cuatro políticas `*_shared`/`by_code`. Lleva un encabezado que dice que solo se aplica cuando la app RN esté retirada, y que antes de moverlo a `migrations/` hay que renombrarlo con un timestamp nuevo.

### 3.3 Edge function (B1)

Flujo:
1. `OPTIONS` devuelve `'ok'` con los mismos headers CORS de siempre.
2. Si no es POST, devuelve 405.
3. Se extrae el Bearer de `Authorization`. Si falta, 401.
4. Se crea un cliente con `SUPABASE_URL` y `SUPABASE_ANON_KEY` (con `SUPABASE_PUBLISHABLE_KEY` como alternativa) y `global.headers.Authorization` del llamador.
5. `auth.getUser(jwt)`: si falla, no hay usuario o `is_anonymous`, devuelve 401.
6. Se lee el body con `req.text()`. Si tiene más de 8.000.000 caracteres, `IMAGE_TOO_LARGE`. Si el JSON es inválido, `INVALID_REQUEST`.
7. `user_id` del body se ignora; si no coincide con el usuario, solo se loguea `user_id_ignored`.
8. El mime declarado tiene que ser jpeg/jpg/png/webp/heic/heif (si falta, se asume jpeg). La base64:
   - se normaliza (se quita el prefijo `data:` y los espacios);
   - más de 7.000.000 caracteres (unos 5,25 MB decodificados) da `IMAGE_TOO_LARGE`;
   - menos de 100 caracteres, longitud no múltiplo de 4 o caracteres inválidos dan `INVALID_IMAGE`.
9. Se detecta el tipo real por magic bytes. Si no coincide con un formato permitido, `INVALID_IMAGE`. En el data URL se usa el tipo detectado.
10. RPC `consume_ai_import_quota`: si devuelve error, `INTERNAL`; si devuelve false, `RATE_LIMITED`.
11. Se leen los ejercicios con el cliente del usuario.
12. Se llama al gateway con timeout de 50 s (`AbortController`). Si no responde OK o se corta, `AI_UNAVAILABLE`, y se loguea solo el status o el motivo.
13. `parseModelJson` y luego `sanitizeRoutine`:
    - ids fuera del catálogo se descartan y se loguea cuántos;
    - máximo 7 días y 30 ejercicios por día;
    - sets 1..20 (por defecto 3), reps 1..100 (10), descanso 15..600 (90);
    - nombre de hasta 50 caracteres (por defecto "Rutina importada"), nombre de día de hasta 50 (por defecto "Día N").
14. Si quedan 0 ejercicios, `NO_EXERCISES_FOUND`.
15. RPC `create_routine_from_import`: si devuelve error `22023`, `NO_EXERCISES_FOUND`; cualquier otro error, `INTERNAL`.
16. Respuesta 200 `{routine_id, routine_name}`.

Los logs son JSON con `stage`, `userId`, `code`/`status` y nunca incluyen la imagen, el prompt, tokens ni cuerpos del gateway o de la base. Un try/catch global devuelve `INTERNAL`. No se usa la service role key. El prompt, el modelo y `response_format` quedan igual que antes.

### 3.4 Cliente Kotlin (solo cambios en el documento)

- Las RPC reemplazan las lecturas directas.
- La función se llama sin `user_id`.
- `ParseRoutineImageResponse` agrega `code`.
- En `ErrorMapper`, `P0002` pasa a `NotFound`.

---

## 4. Pasos

### Paso 1. `E:\Spoter Kotlin\supabase\config.toml`
```toml
# Supabase CLI config for this repo. Only the settings that must be versioned live here.
project_id = "spotter"

[functions.parse-routine-image]
# B1 (2026-09-25): the platform must reject requests without a valid JWT.
# The function additionally resolves the user with auth.getUser() and ignores body.user_id.
verify_jwt = true
```

### Paso 2. `E:\Spoter Kotlin\supabase\migrations\20260925120000_b2_restrict_shared_routine_reads.sql` (guardar en UTF-8)
```sql
-- B2 step 1 (2026-09-25): shared routines are no longer readable by the anon role.
-- Direct shared-read policies are kept ONLY for authenticated users (the React Native app still
-- reads shared routines directly) and now honor expires_at.
-- shared_routines INSERT/UPDATE now require owning the referenced routine.
-- Step 2 (supabase/migrations_pending/b2_step2_drop_direct_shared_reads.sql) removes the direct
-- shared-read policies once the RN app is retired.

-- shared_routines ---------------------------------------------------------
drop policy if exists shared_select_by_code on public.shared_routines;
create policy shared_select_by_code on public.shared_routines
  for select to authenticated
  using (is_active and (expires_at is null or expires_at > now()));

drop policy if exists shared_insert_own on public.shared_routines;
create policy shared_insert_own on public.shared_routines
  for insert to authenticated
  with check (
    (select auth.uid()) = shared_by
    and exists (
      select 1 from public.routines r
      where r.id = shared_routines.routine_id and r.user_id = (select auth.uid())
    )
  );

drop policy if exists shared_update_own on public.shared_routines;
create policy shared_update_own on public.shared_routines
  for update to authenticated
  using ((select auth.uid()) = shared_by)
  with check (
    (select auth.uid()) = shared_by
    and exists (
      select 1 from public.routines r
      where r.id = shared_routines.routine_id and r.user_id = (select auth.uid())
    )
  );

-- routines ------------------------------------------------------------------
drop policy if exists routines_select_shared on public.routines;
create policy routines_select_shared on public.routines
  for select to authenticated
  using (
    id in (
      select sr.routine_id from public.shared_routines sr
      where sr.is_active and (sr.expires_at is null or sr.expires_at > now())
    )
  );

-- routine_exercises -----------------------------------------------------------
drop policy if exists re_select_shared on public.routine_exercises;
create policy re_select_shared on public.routine_exercises
  for select to authenticated
  using (
    routine_id in (
      select sr.routine_id from public.shared_routines sr
      where sr.is_active and (sr.expires_at is null or sr.expires_at > now())
    )
  );

-- routine_days: split the combined (owner OR shared) policy -------------------
drop policy if exists routine_days_select_own on public.routine_days;
create policy routine_days_select_own on public.routine_days
  for select to authenticated
  using (
    exists (
      select 1 from public.routines r
      where r.id = routine_days.routine_id and r.user_id = (select auth.uid())
    )
  );

drop policy if exists routine_days_select_shared on public.routine_days;
create policy routine_days_select_shared on public.routine_days
  for select to authenticated
  using (
    routine_id in (
      select sr.routine_id from public.shared_routines sr
      where sr.is_active and (sr.expires_at is null or sr.expires_at > now())
    )
  );

-- Supports the policies above and create_share().
create index if not exists shared_routines_routine_id_active_idx
  on public.shared_routines (routine_id) where is_active;
```
Sobre recursión: las políticas SELECT de `shared_routines` no hacen referencia a `routines`, así que no hay ciclo aunque los WITH CHECK de insert y update sí consulten `routines`.

### Paso 3. `E:\Spoter Kotlin\supabase\migrations\20260925120100_b2_shared_routine_rpcs.sql`
```sql
-- B2 step 1 (2026-09-25): RPCs for sharing/importing routines without direct table reads.
-- All functions: search_path = '' (fully qualified names), EXECUTE only for authenticated.

-- Preview of a shared routine by exact code. Returns NULL when the code is malformed,
-- unknown, inactive or expired. Never exposes user ids or routine ids.
create or replace function public.get_shared_routine(p_code text)
returns jsonb
language plpgsql
stable
security definer
set search_path = ''
as $$
declare
  v_share public.shared_routines%rowtype;
  v_routine public.routines%rowtype;
begin
  if auth.uid() is null or coalesce((auth.jwt() ->> 'is_anonymous')::boolean, false) then
    raise exception 'not_authenticated' using errcode = '42501';
  end if;
  if p_code is null or p_code !~ '^[A-Za-z0-9]{6,32}$' then
    return null;
  end if;

  select s.* into v_share
  from public.shared_routines s
  where s.share_code = p_code
    and s.is_active
    and (s.expires_at is null or s.expires_at > now())
  limit 1;
  if not found then
    return null;
  end if;

  select r.* into v_routine from public.routines r where r.id = v_share.routine_id;
  if not found then
    return null;
  end if;

  return jsonb_build_object(
    'share_code', v_share.share_code,
    'expires_at', v_share.expires_at,
    'routine', jsonb_build_object(
      'name', v_routine.name,
      'description', v_routine.description,
      'days_per_week', v_routine.days_per_week,
      'days', coalesce((
        select jsonb_agg(jsonb_build_object('day_number', d.day_number, 'name', d.name) order by d.day_number)
        from public.routine_days d
        where d.routine_id = v_routine.id
      ), '[]'::jsonb),
      'exercises', coalesce((
        select jsonb_agg(jsonb_build_object(
                 'exercise_id', re.exercise_id,
                 'day_number', re.day_number,
                 'sort_order', re.sort_order,
                 'target_sets', re.target_sets,
                 'target_reps', re.target_reps,
                 'rest_seconds', re.rest_seconds,
                 'exercise_name', e.name,
                 'exercise_name_en', e.name_en,
                 'muscle_group', mg.name)
               order by re.day_number nulls last, re.sort_order)
        from public.routine_exercises re
        join public.exercises e on e.id = re.exercise_id
        left join public.muscle_groups mg on mg.id = e.muscle_group_id
        where re.routine_id = v_routine.id
      ), '[]'::jsonb)
    )
  );
end;
$$;

revoke all on function public.get_shared_routine(text) from public, anon;
grant execute on function public.get_shared_routine(text) to authenticated;

-- Transactional copy of a shared routine into the caller's account.
-- Raises P0002 'share_not_found' when the code is malformed/unknown/inactive/expired.
create or replace function public.import_shared_routine(p_code text)
returns uuid
language plpgsql
volatile
security definer
set search_path = ''
as $$
declare
  v_uid uuid := auth.uid();
  v_routine public.routines%rowtype;
  v_new_id uuid;
begin
  if v_uid is null or coalesce((auth.jwt() ->> 'is_anonymous')::boolean, false) then
    raise exception 'not_authenticated' using errcode = '42501';
  end if;
  if p_code is null or p_code !~ '^[A-Za-z0-9]{6,32}$' then
    raise exception 'share_not_found' using errcode = 'P0002';
  end if;

  select r.* into v_routine
  from public.shared_routines s
  join public.routines r on r.id = s.routine_id
  where s.share_code = p_code
    and s.is_active
    and (s.expires_at is null or s.expires_at > now())
  limit 1;
  if not found then
    raise exception 'share_not_found' using errcode = 'P0002';
  end if;

  -- 38 + len(' (importada)') = 50, the client-side routine name limit.
  insert into public.routines (user_id, name, description, days_per_week)
  values (v_uid, left(btrim(v_routine.name), 38) || ' (importada)', v_routine.description, v_routine.days_per_week)
  returning id into v_new_id;

  insert into public.routine_days (routine_id, day_number, name)
  select v_new_id, d.day_number, d.name
  from public.routine_days d
  where d.routine_id = v_routine.id;

  insert into public.routine_exercises
    (routine_id, exercise_id, day_number, sort_order, target_sets, target_reps, rest_seconds)
  select v_new_id, re.exercise_id, re.day_number, re.sort_order, re.target_sets, re.target_reps, re.rest_seconds
  from public.routine_exercises re
  where re.routine_id = v_routine.id;

  return v_new_id;
end;
$$;

revoke all on function public.import_shared_routine(text) from public, anon;
grant execute on function public.import_shared_routine(text) to authenticated;

-- Returns the caller's active (non-expired) share code for one of THEIR routines, creating it
-- if needed. Server-side code: 8 chars from 'ABCDEFGHJKLMNPQRSTUVWXYZ23456789' (40 bits, no modulo
-- bias), retried on unique_violation. SECURITY INVOKER: RLS still applies.
-- Raises P0002 'routine_not_found' if the routine does not exist or is not owned by the caller.
create or replace function public.create_share(p_routine_id uuid)
returns text
language plpgsql
volatile
security invoker
set search_path = ''
as $$
declare
  v_uid uuid := auth.uid();
  c_alphabet constant text := 'ABCDEFGHJKLMNPQRSTUVWXYZ23456789';
  v_code text;
  v_bytes bytea;
  v_attempt int := 0;
begin
  if v_uid is null or coalesce((auth.jwt() ->> 'is_anonymous')::boolean, false) then
    raise exception 'not_authenticated' using errcode = '42501';
  end if;
  if p_routine_id is null or not exists (
    select 1 from public.routines r where r.id = p_routine_id and r.user_id = v_uid
  ) then
    raise exception 'routine_not_found' using errcode = 'P0002';
  end if;

  -- Serialize concurrent share requests for the same routine.
  perform pg_catalog.pg_advisory_xact_lock(pg_catalog.hashtextextended(p_routine_id::text, 0));

  select s.share_code into v_code
  from public.shared_routines s
  where s.routine_id = p_routine_id
    and s.shared_by = v_uid
    and s.is_active
    and (s.expires_at is null or s.expires_at > now())
  order by s.created_at desc
  limit 1;
  if found then
    return v_code;
  end if;

  loop
    v_attempt := v_attempt + 1;
    -- gen_random_uuid() uses pg_strong_random. Bytes 6 and 8 carry version/variant bits,
    -- so only bytes 0..5 and 10..11 are used.
    v_bytes := pg_catalog.uuid_send(pg_catalog.gen_random_uuid());
    v_code := '';
    for i in 0..7 loop
      v_code := v_code || substr(c_alphabet, (get_byte(v_bytes, case when i < 6 then i else i + 4 end) % 32) + 1, 1);
    end loop;
    begin
      insert into public.shared_routines (routine_id, shared_by, share_code)
      values (p_routine_id, v_uid, v_code);
      return v_code;
    exception when unique_violation then
      if v_attempt >= 5 then
        raise;
      end if;
    end;
  end loop;
end;
$$;

revoke all on function public.create_share(uuid) from public, anon;
grant execute on function public.create_share(uuid) to authenticated;

notify pgrst, 'reload schema';
```

### Paso 4. `E:\Spoter Kotlin\supabase\migrations\20260925120200_b1_create_routine_from_import_rpc.sql` (UTF-8, contiene "Día")
```sql
-- B1 (2026-09-25): atomic creation of an AI-imported routine in the CALLER's account.
-- SECURITY INVOKER: RLS applies (routines_insert_own, routine_days_insert_own, re_insert).
-- Used by the parse-routine-image edge function through a client scoped to the caller's JWT.
-- p_days: [{ "day_name": text, "exercises": [{ "exercise_id": int, "target_sets": int,
--            "target_reps": int, "rest_seconds": int }] }]
-- Unknown exercise ids are dropped; values are clamped; max 7 days, 30 exercises per day.
-- Raises 22023 'no_valid_exercises' (whole call rolled back) when nothing valid remains.
create or replace function public.create_routine_from_import(p_name text, p_days jsonb)
returns jsonb
language plpgsql
volatile
security invoker
set search_path = ''
as $$
declare
  v_uid uuid := auth.uid();
  v_name text;
  v_day_count int;
  v_routine_id uuid;
  v_inserted int;
begin
  if v_uid is null or coalesce((auth.jwt() ->> 'is_anonymous')::boolean, false) then
    raise exception 'not_authenticated' using errcode = '42501';
  end if;
  if p_days is null or jsonb_typeof(p_days) <> 'array' or jsonb_array_length(p_days) = 0 then
    raise exception 'invalid_days' using errcode = '22023';
  end if;

  v_day_count := least(jsonb_array_length(p_days), 7);
  v_name := left(btrim(coalesce(p_name, '')), 50);
  if v_name = '' then
    v_name := 'Rutina importada';
  end if;

  insert into public.routines (user_id, name, days_per_week)
  values (v_uid, v_name, v_day_count)
  returning id into v_routine_id;

  insert into public.routine_days (routine_id, day_number, name)
  select v_routine_id,
         d.ord::smallint,
         coalesce(nullif(left(btrim(d.val ->> 'day_name'), 50), ''), 'Día ' || d.ord)
  from jsonb_array_elements(p_days) with ordinality as d(val, ord)
  where d.ord <= 7
  on conflict do nothing;

  insert into public.routine_exercises
    (routine_id, exercise_id, day_number, sort_order, target_sets, target_reps, rest_seconds)
  select v_routine_id,
         e.id,
         d.ord::smallint,
         (x.ord - 1)::smallint,
         least(greatest(coalesce(v.sets, 3), 1), 20)::smallint,
         least(greatest(coalesce(v.reps, 10), 1), 100)::smallint,
         least(greatest(coalesce(v.rest, 90), 15), 600)::smallint
  from jsonb_array_elements(p_days) with ordinality as d(val, ord)
  cross join lateral jsonb_array_elements(
    case when jsonb_typeof(d.val -> 'exercises') = 'array' then d.val -> 'exercises' else '[]'::jsonb end
  ) with ordinality as x(val, ord)
  cross join lateral (
    select
      case when x.val ->> 'exercise_id'  ~ '^\d{1,9}$' then (x.val ->> 'exercise_id')::int end  as exercise_id,
      case when x.val ->> 'target_sets'  ~ '^\d{1,6}$' then (x.val ->> 'target_sets')::int end  as sets,
      case when x.val ->> 'target_reps'  ~ '^\d{1,6}$' then (x.val ->> 'target_reps')::int end  as reps,
      case when x.val ->> 'rest_seconds' ~ '^\d{1,6}$' then (x.val ->> 'rest_seconds')::int end as rest
  ) v
  join public.exercises e on e.id = v.exercise_id
  where d.ord <= 7
    and x.ord <= 30
  on conflict do nothing;

  get diagnostics v_inserted = row_count;
  if v_inserted = 0 then
    raise exception 'no_valid_exercises' using errcode = '22023';
  end if;

  return jsonb_build_object('routine_id', v_routine_id, 'routine_name', v_name);
end;
$$;

revoke all on function public.create_routine_from_import(text, jsonb) from public, anon;
grant execute on function public.create_routine_from_import(text, jsonb) to authenticated;

notify pgrst, 'reload schema';
```

### Paso 5. `E:\Spoter Kotlin\supabase\migrations\20260925120300_b6_ai_import_rate_limit.sql`
```sql
-- B6 (2026-09-25): per-user quota for the AI routine import (cost abuse protection).
-- The table is private: RLS enabled, no policies, no grants. Only the SECURITY DEFINER
-- function below touches it. Limit: 20 attempts per user per rolling 24 h.
create table if not exists public.ai_import_usage (
  id bigint generated always as identity primary key,
  user_id uuid not null references auth.users (id) on delete cascade,
  created_at timestamptz not null default now()
);
create index if not exists ai_import_usage_user_created_idx
  on public.ai_import_usage (user_id, created_at desc);
alter table public.ai_import_usage enable row level security;
revoke all on table public.ai_import_usage from anon, authenticated;

-- Returns true and records one attempt when the caller is under quota; false otherwise.
create or replace function public.consume_ai_import_quota()
returns boolean
language plpgsql
volatile
security definer
set search_path = ''
as $$
declare
  v_uid uuid := auth.uid();
  c_limit constant int := 20;
  v_count int;
begin
  if v_uid is null or coalesce((auth.jwt() ->> 'is_anonymous')::boolean, false) then
    raise exception 'not_authenticated' using errcode = '42501';
  end if;

  perform pg_catalog.pg_advisory_xact_lock(pg_catalog.hashtextextended('ai_import:' || v_uid::text, 0));

  delete from public.ai_import_usage
  where user_id = v_uid and created_at < now() - interval '2 days';

  select count(*) into v_count
  from public.ai_import_usage
  where user_id = v_uid and created_at > now() - interval '24 hours';

  if v_count >= c_limit then
    return false;
  end if;

  insert into public.ai_import_usage (user_id) values (v_uid);
  return true;
end;
$$;

revoke all on function public.consume_ai_import_quota() from public, anon;
grant execute on function public.consume_ai_import_quota() to authenticated;

notify pgrst, 'reload schema';
```

### Paso 6. `E:\Spoter Kotlin\supabase\migrations_pending\b2_step2_drop_direct_shared_reads.sql`
```sql
-- =====================================================================================
-- B2 STEP 2 — DO NOT APPLY YET.
-- Apply ONLY after the React Native app (E:\Spotter) is retired from production, i.e. when
-- no client reads shared_routines / shared routines directly through PostgREST anymore.
-- The Kotlin app uses get_shared_routine / import_shared_routine / create_share, which keep
-- working after this migration (DEFINER RPCs + owner policies).
-- Effect: authenticated users can no longer list active share codes or read other users'
-- shared routines directly (removes enumeration by logged-in users).
-- Before applying: move this file to supabase/migrations/ renamed as
-- <YYYYMMDDHHMMSS>_b2_step2_drop_direct_shared_reads.sql with the apply date.
-- Verify afterwards: as an authenticated non-owner, `select count(*) from public.shared_routines`
-- only counts own rows, and get_shared_routine/import_shared_routine still work.
-- =====================================================================================
drop policy if exists shared_select_by_code on public.shared_routines;
drop policy if exists routines_select_shared on public.routines;
drop policy if exists re_select_shared on public.routine_exercises;
drop policy if exists routine_days_select_shared on public.routine_days;
```

### Paso 7. `E:\Spoter Kotlin\supabase\rollback\20260925_b1_b2_rollback.sql` (NO se aplica; solo para emergencias)
```sql
-- EMERGENCY ROLLBACK for 2026-09-25 B1/B2/B6 migrations. NOT a migration; never auto-apply.
-- Part A re-opens B2 (anon can read shared routines again). Prefer fixing forward.
-- Part B breaks the new parse-routine-image function (it calls these RPCs): apply Part B only
-- if the edge function is also rolled back.
-- Before using, compare with the pg_policies snapshot taken before applying (pre-flight step).

-- ---------- Part A: policies back to the pre-2026-09-25 live state (roles = public) ----------
drop policy if exists shared_select_by_code on public.shared_routines;
create policy shared_select_by_code on public.shared_routines for select using (is_active = true);
drop policy if exists shared_insert_own on public.shared_routines;
create policy shared_insert_own on public.shared_routines for insert with check (auth.uid() = shared_by);
drop policy if exists shared_update_own on public.shared_routines;
create policy shared_update_own on public.shared_routines for update
  using (auth.uid() = shared_by) with check (auth.uid() = shared_by);
drop policy if exists routines_select_shared on public.routines;
create policy routines_select_shared on public.routines for select
  using (id in (select routine_id from public.shared_routines where is_active = true));
drop policy if exists re_select_shared on public.routine_exercises;
create policy re_select_shared on public.routine_exercises for select
  using (routine_id in (select routine_id from public.shared_routines where is_active = true));
drop policy if exists routine_days_select_shared on public.routine_days;
drop policy if exists routine_days_select_own on public.routine_days;
create policy routine_days_select_own on public.routine_days for select
  using (
    exists (select 1 from public.routines r where r.id = routine_days.routine_id and r.user_id = auth.uid())
    or routine_id in (select routine_id from public.shared_routines where is_active = true)
  );

-- ---------- Part B: drop new objects (only together with an edge-function rollback) ----------
-- drop function if exists public.get_shared_routine(text);
-- drop function if exists public.import_shared_routine(text);
-- drop function if exists public.create_share(uuid);
-- drop function if exists public.create_routine_from_import(text, jsonb);
-- drop function if exists public.consume_ai_import_quota();
-- drop table if exists public.ai_import_usage;
-- drop index if exists public.shared_routines_routine_id_active_idx;
-- notify pgrst, 'reload schema';
```
Rollback de la función: **no redesplegar la v10.** Si la nueva función falla, se corrige hacia adelante. Como mitigación mínima de emergencia se puede redesplegar el código viejo, pero con `verify_jwt=true`: eso sigue confiando en `user_id` del body, así que solo bloquea las llamadas sin JWT.

### Paso 8. `E:\Spoter Kotlin\supabase\functions\parse-routine-image\validation.ts` (código puro, sin I/O)
```ts
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

export function cleanText(value: unknown, max: number): string {
  if (typeof value !== 'string') return ''
  return value.replace(/\p{Cc}+/gu, ' ').replace(/\s+/g, ' ').trim().slice(0, max).trim()
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
```

### Paso 9. `E:\Spoter Kotlin\supabase\functions\parse-routine-image\index.ts`
```ts
// parse-routine-image — builds a routine from a photo with an LLM and stores it in the CALLER's account.
//
// Security model (B1 fix, 2026-09-25):
//  - Deployed with verify_jwt = true (supabase/config.toml). The platform check also lets the anon
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

function buildPrompt(exerciseList: CatalogEntry[]): string {
  // (copy verbatim the prompt text from E:\Spotter\supabase\functions\parse-routine-image\index.ts
  //  lines 42-72, with ${JSON.stringify(exerciseList)} in the same place)
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

  // 2. Read and validate the body.
  const raw = await req.text()
  if (raw.length > MAX_BODY_CHARS) return fail('IMAGE_TOO_LARGE')
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
```
`buildPrompt` tiene que devolver exactamente el texto del prompt original (`E:\Spotter\supabase\functions\parse-routine-image\index.ts:42-72`), incluida la instrucción de `sort_order` aunque ya no se use.

### Paso 10. `E:\Spoter Kotlin\supabase\functions\parse-routine-image\validation_test.ts`
Usar `Deno.test` e `import { assertEquals } from 'jsr:@std/assert@1'`. Helper: `const b64 = (bytes: number[], pad = 120) => btoa(String.fromCharCode(...bytes, ...new Array(pad).fill(0)))`; con esos tamaños la longitud siempre es múltiplo de 4 y supera 100.

Casos:
- **`toInt`:** `'8'`→8, `8.9`→8, `-3` con min 1 da 1, `'abc'` da el default, `1e9` da el máximo, `null` da el default.
- **`normalizeDeclaredMime`:** `undefined`→`image/jpeg`, `' IMAGE/JPG '`→`image/jpeg`, `'image/gif'`→null, `123`→null, `'image/heif'`→`image/heif`.
- **`normalizeBase64`:**
  - `42` da `INVALID_REQUEST`;
  - `''` da `INVALID_REQUEST`;
  - `'data:image/png;base64,' + b64(png)` da ok y sin prefijo;
  - un string válido con `\n` da ok y sin espacios;
  - `'A'.repeat(7_000_004)` da `IMAGE_TOO_LARGE`;
  - `'AAA'` da `INVALID_IMAGE`;
  - `'@@@@' + 'A'.repeat(200)` da `INVALID_IMAGE`;
  - longitud no múltiplo de 4 da `INVALID_IMAGE`.
- **`sniffImageMime`:**
  - JPEG `[0xff,0xd8,0xff,0xe0]`;
  - firma PNG;
  - `RIFF` + 4 bytes + `WEBP`;
  - `[0,0,0,0x18]` + `ftypheic`;
  - `ftypavif` da null;
  - bytes GIF `GIF89a` dan null.
- **`parseModelJson`:** `` '```json\n{"a":1}\n```' `` da `{a:1}`; `42` da null; `'nope'` da null.
- **`sanitizeRoutine`:**
  - `null` o `[]` o `{days: 'x'}` dan null;
  - los ids desconocidos se descartan y `dropped` los cuenta;
  - `exercise_id: '5'` se acepta si 5 es válido; `5.5` se descarta;
  - sets 99→20, reps 0→1, descanso 5→15, faltantes toman los defaults 3/10/90;
  - 9 días quedan en 7; 40 ejercicios quedan en 30;
  - nombre vacío da `'Rutina importada'`; nombre de 80 caracteres queda en 50;
  - nombre de día faltante en el índice 1 da `'Día 2'`;
  - caracteres de control se eliminan.
- **`countExercises`:** comprobar la suma.

### Paso 11. Editar `E:\Spoter Kotlin\docs\MIGRATION_PLAN.md` (reemplazos exactos)

**(a) Línea 14.** Reemplazar el texto desde `**No hay cambios de schema.**` hasta el final de la línea por:
`Los únicos cambios de backend son los ítems aprobados de la sección 8 (B1, B2 paso 1 y B6, del 2026-09-25), versionados en \`supabase/\` (migraciones y edge function). El resto de los problemas del backend siguen listados en la sección 8 como ítems separados que requieren aprobación del usuario.`

**(b) §6, líneas 310-312.** Reemplazar la definición de `SharedRoutineDto` por:
```kotlin
// Respuesta de la RPC get_shared_routine (jsonb; null si el código no existe, está inactivo o vencido). No trae ids de usuario ni de rutina.
data class SharedRoutinePreviewDto(@SerialName("share_code") val shareCode: String, @SerialName("expires_at") val expiresAt: String? = null,
  val routine: SharedRoutineBodyDto)
data class SharedRoutineBodyDto(val name: String, val description: String? = null, @SerialName("days_per_week") val daysPerWeek: Int? = null,
  val days: List<SharedRoutineDayDto> = emptyList(), val exercises: List<SharedRoutineExerciseDto> = emptyList())
data class SharedRoutineDayDto(@SerialName("day_number") val dayNumber: Int, val name: String)
data class SharedRoutineExerciseDto(@SerialName("exercise_id") val exerciseId: Int, @SerialName("day_number") val dayNumber: Int? = null,
  @SerialName("sort_order") val sortOrder: Int, @SerialName("target_sets") val targetSets: Int, @SerialName("target_reps") val targetReps: Int,
  @SerialName("rest_seconds") val restSeconds: Int, @SerialName("exercise_name") val exerciseName: String,
  @SerialName("exercise_name_en") val exerciseNameEn: String, @SerialName("muscle_group") val muscleGroup: String? = null)
```

**(c) Líneas 327-329.** Borrar `data class SharedRoutineInsertDto(...)` (los shares se crean con la RPC `create_share`) y reemplazar las dos líneas de `ParseRoutineImage*` por:
```kotlin
data class ParseRoutineImageRequest(@SerialName("image_base64") val imageBase64: String, @SerialName("image_mime_type") val mimeType: String) // sin user_id: la función usa el JWT
data class ParseRoutineImageResponse(@SerialName("routine_id") val routineId: String? = null, @SerialName("routine_name") val routineName: String? = null,
  val error: String? = null, val code: String? = null)
```

**(d) Línea 334.** Reemplazar por:
`- **Filtrar SIEMPRE por \`user_id\` las consultas de \`routines\`.** Hasta aplicar el paso 2 de B2 (sección 8), la RLS deja que cualquier usuario **autenticado** lea las rutinas compartidas activas de otros usuarios.`
Agregar debajo:
`- **Compartir e importar solo vía RPC** (\`postgrest.rpc(...)\`), nunca leyendo \`shared_routines\` ni rutinas ajenas directo: \`create_share(p_routine_id)\` → código (\`String\`); \`get_shared_routine(p_code)\` → \`SharedRoutinePreviewDto?\` (decodificar como tipo nulable; el cuerpo \`null\` significa inexistente, inactivo o vencido); \`import_shared_routine(p_code)\` → id de la rutina nueva (\`String\`). Errores: \`P0002\` = código o rutina inexistente; \`42501\` = sin sesión.`

**(e) §7, líneas 378-382 (ítem 21).** Reemplazar las 4 viñetas por:
```
    - `:3-6` código con `Math.random`. **Kotlin:** el código lo genera el servidor (RPC `create_share`, aleatorio fuerte).
    - `:8-17` crea un registro nuevo en cada "compartir". **Kotlin:** `create_share` reutiliza el share activo y reintenta colisiones en el servidor.
    - `:19-28` ignora `expires_at` y lee las tablas directo. **Kotlin:** RPC `get_shared_routine`, que valida activo y no vencido y no expone ids.
    - `:30-77` importación no transaccional. **Kotlin:** RPC `import_shared_routine`, transaccional en una sola llamada.
```

**(f) §8, líneas 413-427.** Reemplazar la sección completa por:
```
## 8. Hallazgos del backend: ítems SEPARADOS que requieren aprobación del usuario

**B1, B2 (paso 1) y B6 fueron aprobados e implementados el 2026-09-25. El código vive en `supabase/` de este repo (`functions/`, `migrations/`, `migrations_pending/`, `rollback/`). Siguen pendientes B2 paso 2, B3, B4 y B5.**

- **B1 (CRÍTICO) — RESUELTO 2026-09-25.** La edge function `parse-routine-image` confiaba en `user_id` del body y escribía con la *service role key*: cualquiera con la anon key podía crear rutinas en otra cuenta y gastar créditos de IA.
  - Arreglo (`supabase/functions/parse-routine-image/`): `verify_jwt = true`; el usuario sale de `auth.getUser(jwt)`; `user_id` del body se ignora; las escrituras usan un cliente con el JWT del llamador (RLS), sin service role; la rutina se crea atómicamente con la RPC `create_routine_from_import(p_name, p_days)` (`SECURITY INVOKER`).
  - Validaciones: `exercise_id` contra el catálogo (los desconocidos se descartan); ≤7 días, ≤30 ejercicios por día; series 1..20, reps 1..100, descanso 15..600; imagen ≤7.000.000 caracteres base64 (~5 MB) y solo JPEG/PNG/WEBP/HEIC (verificado por magic bytes); errores genéricos, sin texto del gateway ni de la base.
  - Contrato: éxito `200 {routine_id, routine_name}`; error `200 {error, code}` con `code` ∈ `INVALID_REQUEST`, `IMAGE_TOO_LARGE`, `INVALID_IMAGE`, `UNSUPPORTED_IMAGE_TYPE`, `RATE_LIMITED`, `AI_UNAVAILABLE`, `AI_PARSE_FAILED`, `NO_EXERCISES_FOUND`, `INTERNAL`; `401` sin sesión válida; `405` si no es POST. Los errores van con 200 porque la app RN solo lee `data.error` en respuestas 2xx.
  - **El cliente Kotlin NO envía `user_id`.**
- **B2 (ALTO) — PASO 1 RESUELTO 2026-09-25; PASO 2 PENDIENTE.** Sin login se podían leer todos los `shared_routines` activos (códigos enumerables) y las rutinas, ejercicios y días vinculados.
  - Paso 1 (`supabase/migrations/20260925120000_b2_restrict_shared_routine_reads.sql` y `20260925120100_b2_shared_routine_rpcs.sql`): las políticas de lectura compartida (`shared_select_by_code`, `routines_select_shared`, `re_select_shared`, `routine_days_select_shared`) pasan a `TO authenticated` y respetan `expires_at`; `shared_insert_own` y `shared_update_own` exigen ser dueño de la rutina (antes se podía compartir una rutina ajena). RPCs solo para `authenticated`: `get_shared_routine(p_code)` y `import_shared_routine(p_code)` (`SECURITY DEFINER`, la segunda transaccional) y `create_share(p_routine_id)` (`SECURITY INVOKER`: reutiliza el share activo y genera el código en el servidor).
  - Riesgo residual hasta el paso 2: un usuario **logueado** puede listar los shares activos y leer esas rutinas por REST (la app RN lo necesita).
  - Paso 2 (`supabase/migrations_pending/b2_step2_drop_direct_shared_reads.sql`): borra las políticas de lectura compartida directa. **Aplicar solo cuando se retire la app RN.** El cliente Kotlin no depende de ellas.
- **B3 (MEDIO) — `01_schema.sql:174-203` — los PR no se recalculan.** [texto actual sin cambios]
- **B4 (BAJO) — funciones sin `search_path`.** [texto actual sin cambios] Las funciones nuevas del 2026-09-25 ya usan `set search_path = ''`.
- **B5 (MANTENIMIENTO) — `database/*.sql` no refleja la base en vivo.** [texto actual sin cambios]
- **B6 (MEDIO) — RESUELTO 2026-09-25.** Tope de tamaño de imagen en la función y cuota de 20 importaciones con IA por usuario cada 24 h (tabla privada `ai_import_usage` + RPC `consume_ai_import_quota`, `supabase/migrations/20260925120300_b6_ai_import_rate_limit.sql`). El cupo se consume antes de llamar a la IA, aunque la importación falle después.
```
En B3, B4 y B5, conservar literalmente el texto actual de las líneas 424-426 donde dice "[texto actual sin cambios]".

**(g) §9.2, después de la línea 503.** Agregar la fila:
`| \`PostgrestRestException\` con code \`"P0002"\` (RPCs de compartir: código o rutina inexistente) | \`NotFound\` |`

**(h) §9.3, línea 558.** Reemplazar la línea de `fun generate(...)` por:
`    // sin generate(): los códigos nuevos los genera el servidor (RPC create_share) con el formato CLIENT`

**(i) §9.4, líneas 641-646.** Reemplazar `SharingRepository` y `AiImportRepository` por:
```kotlin
interface SharingRepository {
  suspend fun createShare(routineId: String): AppResult<ShareCode>                 // RPC create_share: reutiliza el activo o crea uno (código del servidor)
  suspend fun getSharedRoutine(code: ShareCode): AppResult<SharedRoutineContent?>   // RPC get_shared_routine; null = inexistente, inactivo o vencido
  suspend fun importSharedRoutine(code: ShareCode): AppResult<String>               // RPC import_shared_routine (transaccional); id de la rutina nueva
}
interface AiImportRepository { suspend fun importFromImage(base64Jpeg: String): AppResult<Pair<String, String>> } // (routineId, routineName); sin userId
```
**Línea 673.** Reemplazar por:
`\`SharingRepository.getSharedRoutine\` devuelve el modelo de dominio \`SharedRoutineContent(name, description, daysPerWeek, exerciseCount, dayCount, expiresAt: Instant?)\` en \`domain/model\`, mapeado desde \`SharedRoutinePreviewDto\`. El dominio no ve DTOs.`

**(j) §9.5, líneas 699-703.** Reemplazar por:
```
- **`ShareRoutineUseCase(sharingRepo)`:** llama a `createShare(routineId)`. El servidor reutiliza el share activo, genera el código y reintenta las colisiones; el cliente no genera códigos ni reintenta.
- **`ImportSharedRoutineUseCase(sharingRepo)`:**
  - `preview(code): AppResult<SharedRoutinePreview>`: si `getSharedRoutine` devuelve `null` → `NotFound` (el servidor ya valida inactivo y vencido).
  - `import(code): AppResult<String>`: `importSharedRoutine(code)`. Es una sola RPC transaccional (nombre `"$name (importada)"`, días y ejercicios con `dayNumber`), así que no hace falta compensación. `P0002` → `NotFound`.
```
**Línea 708.** Reemplazar por:
`- **\`ImportRoutineFromImageUseCase(aiRepo)\`:** valida \`base64.length <= 4_000_000\` (si no, \`IMAGE_TOO_LARGE\`) y llama. El repositorio mapea el \`code\` de la respuesta: \`IMAGE_TOO_LARGE\` → \`Validation(IMAGE_TOO_LARGE)\`; \`INVALID_IMAGE\` y \`UNSUPPORTED_IMAGE_TYPE\` → \`Validation(IMAGE_UNREADABLE)\`; \`UNAUTHORIZED\` o HTTP 401 → \`Unauthorized\`; el resto (\`RATE_LIMITED\`, \`NO_EXERCISES_FOUND\`, \`AI_PARSE_FAILED\`, \`AI_UNAVAILABLE\`, \`INTERNAL\`) → \`Server(code)\`.`

**(k) Línea 1036.** Reemplazar por:
`- \`domain/model/ShareCodeTest\`: parse (formatos CLIENT y HEX, normalización, inválidos).`

**(l) FASE 6.**
- **Paso 1 (línea 1178).** Reemplazar `**Compartir rutina:** \`ShareRoutineUseCase\` conectado en \`RoutineDetailScreen\`.` por `**Compartir rutina:** \`ShareRoutineUseCase\` conectado en \`RoutineDetailScreen\` (RPC \`create_share\`; el código lo genera el servidor).` El resto de la línea no cambia.
- **Paso 2.** Agregar al final de la viñeta "Preview": ` Sale de la RPC \`get_shared_routine\`; nunca se lee \`shared_routines\` directo.` Reemplazar la viñeta "Importar rutina" por:
  `- "Importar rutina" → \`ImportSharedRoutineUseCase.import\` (RPC \`import_shared_routine\`, transaccional) → reemplaza la pantalla por \`RoutineDetailRoute(newId)\` con "Rutina importada". \`NotFound\` → "Código inválido o expirado". "Cancelar" vuelve.`
- **Paso 3.** Reemplazar la viñeta "Error: ..." (línea 1189) por:
  `- Llamada: \`functions.invoke("parse-routine-image", ParseRoutineImageRequest(imageBase64, "image/jpeg"))\`, **sin \`user_id\`** (la función toma el usuario del JWT). Respuesta 200: si trae \`routineId\` es éxito; si trae \`error\`/\`code\`, se mapea el \`code\` (ver \`ImportRoutineFromImageUseCase\`). HTTP 401 (\`RestException\`) → \`Unauthorized\`. Mensajes en español por \`code\`: \`RATE_LIMITED\` → "Alcanzaste el límite diario de importaciones con IA"; \`NO_EXERCISES_FOUND\`/\`AI_PARSE_FAILED\` → "No se pudo leer una rutina en la imagen"; el resto → genérico. El detalle solo se loguea en debug.`
- **Tests de la fase 6 (línea 1198).** Reemplazar el inicio hasta `\`ImportCodeViewModelTest\`` por:
  `**Tests de la fase 6:** \`ShareRoutineUseCaseTest\` (delega en \`createShare\`, propaga \`NotFound\`), \`ImportSharedRoutineUseCaseTest\` (preview \`null\` → NotFound; import devuelve el id; \`P0002\` → NotFound), \`SharingRepositoryTest\` (mapeo de \`SharedRoutinePreviewDto\` → \`SharedRoutineContent\`), \`AiImportRepositoryTest\` (el JSON del request no contiene \`user_id\`; mapeo de cada \`code\`), \`ImportCodeViewModelTest\`...`
  El resto de la línea se conserva.

**(m) §13, línea 1275.** Reemplazar por:
`3. **Ítems del backend B1-B6** (sección 8): B1, B2 paso 1 y B6 fueron aprobados e implementados el 2026-09-25. B2 paso 2 se aplica al retirar la app RN (\`supabase/migrations_pending/\`). **Por defecto:** B3-B5 no se tocan sin aprobación.`

---

## 5. Pruebas

### 5.1 Implementador (local)
- `npx -y deno@2.9.6 test "E:/Spoter Kotlin/supabase/functions/parse-routine-image/validation_test.ts"`: todos pasan.
- `npx -y deno@2.9.6 check "E:/Spoter Kotlin/supabase/functions/parse-routine-image/index.ts"`: sin errores. Necesita red para bajar `npm:@supabase/supabase-js@2.116.0` y `jsr:@std/assert`.
- Si `npx deno` no funciona en Windows, reportarlo y no bloquear. En ese caso el orquestador valida con el deploy.
- No hay Postgres local: el SQL lo valida el orquestador.

### 5.2 Orquestador: orden de ejecución con el MCP de Supabase

**0. Preflight (solo lectura).** Si algo no coincide con lo que se espera, parar y ajustar el SQL.
```sql
select table_name, column_name, data_type, is_nullable, column_default
from information_schema.columns
where table_schema = 'public' and table_name in ('routines','routine_days','routine_exercises','shared_routines')
order by table_name, ordinal_position;

select conrelid::regclass as tbl, conname, pg_get_constraintdef(oid) as def
from pg_constraint
where conrelid in ('public.routines'::regclass, 'public.routine_days'::regclass,
                   'public.routine_exercises'::regclass, 'public.shared_routines'::regclass);

select tgrelid::regclass as tbl, tgname, pg_get_triggerdef(oid)
from pg_trigger
where not tgisinternal
  and tgrelid in ('public.routines'::regclass, 'public.routine_days'::regclass,
                  'public.routine_exercises'::regclass, 'public.shared_routines'::regclass);

-- SNAPSHOT for rollback: keep this output.
select tablename, policyname, roles, cmd, qual, with_check
from pg_policies
where schemaname = 'public' and tablename in ('shared_routines','routines','routine_exercises','routine_days')
order by 1, 2;

select proname from pg_proc
where pronamespace = 'public'::regnamespace
  and proname in ('get_shared_routine','import_shared_routine','create_share','create_routine_from_import','consume_ai_import_quota');

-- Forged/legacy shares: rows created before shared_insert_own required routine ownership.
-- The first migration deactivates these; the count here is just to know what to expect.
select count(*) as forged_shares
from public.shared_routines s
where s.is_active
  and not exists (
    select 1 from public.routines r where r.id = s.routine_id and r.user_id = s.shared_by
  );
```
Lo que se espera:
- `routine_days` solo tiene `id, routine_id, day_number, name` como NOT NULL sin default.
- `routine_exercises.day_number` existe.
- No hay triggers de INSERT en `routines`, `routine_days` ni `routine_exercises` que usen nombres sin calificar. Si los hay, pueden fallar bajo `search_path=''`: habría que revisar.
- Las 4 políticas tienen los nombres del context file.
- Ninguna de las 5 funciones existe todavía.
- `forged_shares` normalmente es 0; si no lo es, esas filas quedan `is_active = false` al aplicar la primera migración.

**1. Aplicar migraciones** con `apply_migration`, en orden:
- `b2_restrict_shared_routine_reads`
- `b2_shared_routine_rpcs`
- `b1_create_routine_from_import_rpc`
- `b6_ai_import_rate_limit`

El MCP asigna su propio número de versión, que no va a coincidir con el timestamp del archivo. Es aceptable; si más adelante se usa la CLI, conciliar con `supabase migration repair`.

**2. Privilegios y políticas (solo lectura):**
```sql
select p.proname, pg_get_function_identity_arguments(p.oid) as args, p.prosecdef as security_definer, p.proconfig,
       has_function_privilege('anon', p.oid, 'execute') as anon_exec,
       has_function_privilege('authenticated', p.oid, 'execute') as auth_exec
from pg_proc p
where p.pronamespace = 'public'::regnamespace
  and p.proname in ('get_shared_routine','import_shared_routine','create_share','create_routine_from_import','consume_ai_import_quota')
order by 1;
-- Expected: anon_exec = false and auth_exec = true for all 5; proconfig = {search_path=""};
-- security_definer = true for get_shared_routine, import_shared_routine, consume_ai_import_quota; false for the other two.

select tablename, policyname, roles, cmd from pg_policies
where schemaname = 'public' and tablename in ('shared_routines','routines','routine_exercises','routine_days')
order by 1, 2;
-- Expected: shared_select_by_code, routines_select_shared, re_select_shared, routine_days_select_shared,
-- routine_days_select_own, shared_insert_own, shared_update_own -> roles {authenticated}.
```

**3. Verificación funcional.** Todo se revierte, porque el bloque siempre termina con `raise exception`. Necesita al menos un share activo y no vencido, y un segundo perfil.
```sql
do $verify$
declare
  v_code text; v_owner uuid; v_other uuid; v_src uuid;
  v_src_ex int; v_src_days int; v_ex_id int; v_cnt int;
  v_json jsonb; v_new uuid; v_user uuid; v_name text;
  v_share1 text; v_share2 text; v_ai jsonb;
begin
  select s.share_code, s.shared_by, s.routine_id into v_code, v_owner, v_src
  from public.shared_routines s
  where s.is_active and (s.expires_at is null or s.expires_at > now())
  order by s.created_at desc limit 1;
  if v_code is null then raise exception 'CHECK FAILED: no active share to test with'; end if;
  select p.id into v_other from public.profiles p where p.id <> v_owner order by p.created_at limit 1;
  if v_other is null then raise exception 'CHECK FAILED: need a second profile'; end if;
  select count(*) into v_src_ex from public.routine_exercises where routine_id = v_src;
  select count(*) into v_src_days from public.routine_days where routine_id = v_src;
  select min(id) into v_ex_id from public.exercises;

  -- A) anon sees nothing
  perform set_config('request.jwt.claims', '{"role":"anon"}', true);
  set local role anon;
  begin select count(*) into v_cnt from public.shared_routines; exception when insufficient_privilege then v_cnt := 0; end;
  if v_cnt <> 0 then raise exception 'CHECK FAILED: anon sees % shared_routines', v_cnt; end if;
  begin select count(*) into v_cnt from public.routines; exception when insufficient_privilege then v_cnt := 0; end;
  if v_cnt <> 0 then raise exception 'CHECK FAILED: anon sees % routines', v_cnt; end if;
  begin select count(*) into v_cnt from public.routine_exercises; exception when insufficient_privilege then v_cnt := 0; end;
  if v_cnt <> 0 then raise exception 'CHECK FAILED: anon sees % routine_exercises', v_cnt; end if;
  begin select count(*) into v_cnt from public.routine_days; exception when insufficient_privilege then v_cnt := 0; end;
  if v_cnt <> 0 then raise exception 'CHECK FAILED: anon sees % routine_days', v_cnt; end if;
  reset role;

  -- B) authenticated non-owner
  perform set_config('request.jwt.claims', json_build_object('sub', v_other, 'role', 'authenticated')::text, true);
  set local role authenticated;

  -- RN compatibility (step 1): direct reads still work while shared
  select count(*) into v_cnt from public.shared_routines where share_code = v_code and is_active;
  if v_cnt <> 1 then raise exception 'CHECK FAILED: RN direct read of shared_routines broken'; end if;
  select count(*) into v_cnt from public.routines where id = v_src;
  if v_cnt <> 1 then raise exception 'CHECK FAILED: RN direct read of shared routine broken'; end if;
  select count(*) into v_cnt from public.routine_exercises where routine_id = v_src;
  if v_cnt <> v_src_ex then raise exception 'CHECK FAILED: RN read of shared exercises % <> %', v_cnt, v_src_ex; end if;
  select count(*) into v_cnt from public.routine_days where routine_id = v_src;
  if v_cnt <> v_src_days then raise exception 'CHECK FAILED: RN read of shared days % <> %', v_cnt, v_src_days; end if;

  -- get_shared_routine
  v_json := public.get_shared_routine(v_code);
  if v_json is null or (v_json -> 'routine' ->> 'name') is null then raise exception 'CHECK FAILED: preview null'; end if;
  if jsonb_array_length(v_json -> 'routine' -> 'exercises') <> v_src_ex
     or jsonb_array_length(v_json -> 'routine' -> 'days') <> v_src_days then
    raise exception 'CHECK FAILED: preview counts mismatch';
  end if;
  if position(v_owner::text in v_json::text) > 0 or position(v_src::text in v_json::text) > 0 then
    raise exception 'CHECK FAILED: preview leaks ids';
  end if;
  if public.get_shared_routine('NOPE0000NOPE') is not null then raise exception 'CHECK FAILED: unknown code returned data'; end if;
  if public.get_shared_routine('bad code;--') is not null then raise exception 'CHECK FAILED: malformed code returned data'; end if;

  -- import_shared_routine
  v_new := public.import_shared_routine(v_code);
  select r.user_id, r.name into v_user, v_name from public.routines r where r.id = v_new;
  if v_user is distinct from v_other or v_name not like '% (importada)' then raise exception 'CHECK FAILED: imported routine owner/name'; end if;
  select count(*) into v_cnt from public.routine_exercises where routine_id = v_new;
  if v_cnt <> v_src_ex then raise exception 'CHECK FAILED: imported exercises % <> %', v_cnt, v_src_ex; end if;
  select count(*) into v_cnt from public.routine_days where routine_id = v_new;
  if v_cnt <> v_src_days then raise exception 'CHECK FAILED: imported days % <> %', v_cnt, v_src_days; end if;
  begin
    perform public.import_shared_routine('NOPE0000NOPE');
    raise exception 'CHECK FAILED: import of unknown code succeeded';
  exception when no_data_found then null; end;

  -- cannot share a foreign routine
  begin
    perform public.create_share(v_src);
    raise exception 'CHECK FAILED: create_share on foreign routine succeeded';
  exception when no_data_found then null; end;
  begin
    insert into public.shared_routines (routine_id, shared_by, share_code) values (v_src, v_other, 'FORGED99TEST');
    raise exception 'CHECK FAILED: forged share insert succeeded';
  exception when insufficient_privilege then null; end;

  -- create_share reuses the active share
  v_share1 := public.create_share(v_new);
  v_share2 := public.create_share(v_new);
  if v_share1 is null or v_share1 <> v_share2 or v_share1 !~ '^[A-HJ-NP-Z2-9]{8}$' then
    raise exception 'CHECK FAILED: create_share % / %', v_share1, v_share2;
  end if;

  -- AI import RPC: clamps + unknown id dropped
  v_ai := public.create_routine_from_import('  Test IA  ', jsonb_build_array(jsonb_build_object(
            'day_name', 'Día A', 'exercises', jsonb_build_array(
              jsonb_build_object('exercise_id', v_ex_id, 'target_sets', 99, 'target_reps', 0, 'rest_seconds', 5),
              jsonb_build_object('exercise_id', 999999999, 'target_sets', 3)))));
  if v_ai ->> 'routine_name' <> 'Test IA' then raise exception 'CHECK FAILED: AI routine name %', v_ai ->> 'routine_name'; end if;
  select count(*) into v_cnt from public.routines where id = (v_ai ->> 'routine_id')::uuid and user_id = v_other;
  if v_cnt <> 1 then raise exception 'CHECK FAILED: AI routine owner'; end if;
  select count(*) into v_cnt from public.routine_exercises
  where routine_id = (v_ai ->> 'routine_id')::uuid and target_sets = 20 and target_reps = 1 and rest_seconds = 15;
  if v_cnt <> 1 then raise exception 'CHECK FAILED: AI exercises clamp/filter (%)', v_cnt; end if;
  begin
    perform public.create_routine_from_import('x', '[{"day_name":"A","exercises":[{"exercise_id":999999999}]}]'::jsonb);
    raise exception 'CHECK FAILED: AI import with only unknown exercises succeeded';
  exception when invalid_parameter_value then null; end;

  if public.consume_ai_import_quota() is not true then raise exception 'CHECK FAILED: quota'; end if;
  reset role;

  -- C) expiry is honored
  update public.shared_routines set expires_at = now() - interval '1 minute' where routine_id = v_src;
  perform set_config('request.jwt.claims', json_build_object('sub', v_other, 'role', 'authenticated')::text, true);
  set local role authenticated;
  if public.get_shared_routine(v_code) is not null then raise exception 'CHECK FAILED: expired share previewed'; end if;
  select count(*) into v_cnt from public.shared_routines where share_code = v_code;
  if v_cnt <> 0 then raise exception 'CHECK FAILED: expired share readable'; end if;
  select count(*) into v_cnt from public.routines where id = v_src;
  if v_cnt <> 0 then raise exception 'CHECK FAILED: routine of expired share readable'; end if;
  begin
    perform public.import_shared_routine(v_code);
    raise exception 'CHECK FAILED: expired share imported';
  exception when no_data_found then null; end;
  reset role;

  raise exception 'ALL CHECKS PASSED (everything rolled back)';
end
$verify$;
```
El único resultado aceptable es el error `ALL CHECKS PASSED (everything rolled back)`. Cualquier `CHECK FAILED` significa que hay que corregir antes de desplegar.

**4. Desplegar la función** con `deploy_edge_function`:
- `name: parse-routine-image`, `entrypoint_path: index.ts`, `verify_jwt: true`;
- archivos: `index.ts` y `validation.ts` (sin el test).

Después, `get_edge_function` tiene que mostrar `verify_jwt: true` y la versión 11.

Hacerlo **enseguida de las migraciones**. Mientras tanto la v10 sigue vulnerable, aunque sigue funcionando porque no usa las RPC.

**5. Smoke tests de la función.** La anon key se obtiene con `get_publishable_keys` (o `get_anon_key`).
```bash
FN=https://bqqpfldwzkfdvlvfjkbr.supabase.co/functions/v1/parse-routine-image
# a) sin Authorization -> 401 (gateway)
curl -s -o /dev/null -w "%{http_code}\n" -X POST "$FN" -H "Content-Type: application/json" -d '{}'
# b) anon key como bearer (JWT válido sin usuario) -> 401 {"error":"No autorizado...","code":"UNAUTHORIZED"}
curl -s -w "\n%{http_code}\n" -X POST "$FN" -H "Authorization: Bearer $ANON" -H "apikey: $ANON" \
  -H "Content-Type: application/json" -d '{"image_base64":"AAAA","user_id":"00000000-0000-0000-0000-000000000000"}'
# c) publishable key (sb_publishable_...) como bearer, si existe -> 401 UNAUTHORIZED
# d) preflight CORS -> 200 "ok"
curl -s -w "\n%{http_code}\n" -X OPTIONS "$FN"
```
Opcional, solo si se consigue un access token real de un usuario de prueba: con `{"image_base64":"<120 chars 'A'>"}` se espera `200 {"code":"INVALID_IMAGE"}` y que no se cree ninguna fila.

Después:
- `get_logs(service: "edge-function")`: los logs no tienen tokens ni base64.
- SQL: `select count(*) from public.routines where user_id = '00000000-0000-0000-0000-000000000000';` tiene que dar 0.

**6. Advisors.** `get_advisors(type: "security")`:
- sin `function_search_path_mutable` ni `anon_security_definer_function_executable` para las 5 funciones;
- WARN aceptados: `authenticated_security_definer_function_executable` en las 3 DEFINER;
- INFO aceptado: `rls_enabled_no_policy` en `ai_import_usage`;
- las advertencias previas de B4 siguen ahí.

**7. Prueba manual en la app RN (usuario).** Compartir una rutina; importar por código desde otra cuenta; importar con IA una foto real. Todo tiene que funcionar igual que antes.

### 5.3 Paso 2 de B2 (a futuro)
Aplicar el archivo pendiente y repetir el bloque DO quitando la parte "RN compatibility". Como no-dueño, `select count(*) from public.shared_routines` tiene que contar solo las filas propias, y las RPC tienen que seguir pasando.

---

## 6. Riesgos y casos borde

- **Schema en vivo distinto al repo** (columnas o constraints de `routine_days`, UNIQUE de `routine_exercises`, triggers que asumen `search_path`). Lo detectan el preflight y el bloque DO. Los `on conflict do nothing` absorben el UNIQUE `(routine_id, exercise_id)`: un ejercicio repetido entre días en la importación con IA se descarta en silencio, igual que antes, que fallaba.
- **Enumeración residual.** Hasta el paso 2, cualquier usuario autenticado puede listar los códigos activos por REST. Es un riesgo aceptado por compatibilidad con la app RN. `get_shared_routine` no tiene rate limit; el espacio de códigos es de 40 bits (48 para los hex viejos).
- **Mensajes de 401 en la app RN.** Muestra el texto genérico "non-2xx". Solo pasa con una sesión inválida.
- **Cuota.** Se consume también en los intentos fallidos, con 20 por día. Un usuario legítimo que reintenta mucho puede toparse con `RATE_LIMITED`. El límite se ajusta cambiando `c_limit`.
- **Variables de entorno.** Si en el futuro se desactivan las keys legacy, `SUPABASE_ANON_KEY` puede desaparecer; por eso está el fallback a `SUPABASE_PUBLISHABLE_KEY`. Si no hay ninguna, la función devuelve `INTERNAL` y lo loguea.
- **Orden de despliegue.** Si la función se despliega antes que las migraciones, la importación con IA devuelve `INTERNAL` porque faltan las RPC. No es un problema de seguridad, pero sí una caída.
- **Nombre importado.** Se trunca a 38 caracteres más " (importada)", para no superar el límite de 50 del cliente Kotlin. La app RN no truncaba: es un cambio menor.
- **Magic bytes.** Si el móvil declara un mime equivocado, se usa el tipo detectado. HEIC con brands raros (por ejemplo `avif`) se rechaza con `INVALID_IMAGE`.
- **Timeout de la IA.** El timeout de 50 s en la función queda por debajo de los 60 s del cliente Kotlin. Se devuelve `AI_UNAVAILABLE`.
- **Usuarios anónimos.** Los rechazan las RPC y la función. Las políticas del paso 1 no los distinguen: si algún día se activan los sign-ins anónimos, podrían listar shares hasta el paso 2.
- **Versiones de migraciones.** Los números del historial remoto no van a coincidir con los nombres de los archivos del repo (el MCP asigna su propio timestamp).
- **Rollback.** La parte A reabre B2 y la parte B rompe la función nueva. Preferir siempre corregir hacia adelante.

## 7. Preguntas abiertas
Ninguna bloqueante. Decidí por defecto lo siguiente; el usuario puede cambiarlo:
- cuota de 20 por usuario cada 24 h;
- truncar el nombre importado a 38 caracteres más el sufijo;
- errores manejados con HTTP 200 y `{error, code}` por compatibilidad con la app RN;
- incluir B6 en esta entrega.

**Fuentes:**
- https://supabase.com/changelog.md
- https://supabase.com/docs/guides/functions/auth.md
- https://supabase.com/docs/guides/functions/auth-headers.md
- https://raw.githubusercontent.com/supabase/supabase-js/master/packages/core/functions-js/src/FunctionsClient.ts
- registro npm: `@supabase/supabase-js` latest 2.117.2 (se usa `2.116.0` en el código para evitar la política de antigüedad mínima de `deno@2.9.6`); `deno` latest 2.9.6.