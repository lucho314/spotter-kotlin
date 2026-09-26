# Live backend context for B1/B2 fixes (gathered 2026-09-25 via Supabase MCP)

## Edge function `parse-routine-image` (live version 10)
- Deployed with `verify_jwt: false`.
- Source: identical in logic to `E:\Spotter\supabase\functions\parse-routine-image\index.ts` (reads `user_id` from body, uses SUPABASE_SERVICE_ROLE_KEY, inserts routines/routine_days/routine_exercises for that user_id, returns 200 with `{error}` on failure, leaks gateway error text, no image size limit, non-transactional inserts, exercise_id not validated against the list).
- Secrets used: SUPABASE_URL, SUPABASE_SERVICE_ROLE_KEY, VERCEL_AI_GATEWAY_KEY (SUPABASE_ANON_KEY is also available by default in edge runtime).
- Callers: RN app `services/ai-routine-import.ts` calls `supabase.functions.invoke('parse-routine-image', { body: { image_base64, image_mime_type, user_id } })` — supabase-js automatically sends `Authorization: Bearer <user access token>`. It reads `data.error` or `data.routine_id/routine_name`. The Kotlin app (phase 6 of docs/MIGRATION_PLAN.md) will call it the same way.

## Live RLS policies (roles = {public} on all of them)
shared_routines(id uuid pk default gen_random_uuid(), routine_id uuid not null, shared_by uuid not null, share_code text not null, is_active bool not null default true, created_at timestamptz not null default now(), expires_at timestamptz null)
- shared_insert_own INSERT with_check auth.uid() = shared_by
- shared_select_by_code SELECT using is_active = true            <-- anon readable, enumerable
- shared_select_own SELECT using auth.uid() = shared_by
- shared_update_own UPDATE auth.uid() = shared_by

routines
- routines_select_own SELECT auth.uid() = user_id
- routines_select_shared SELECT id IN (select routine_id from shared_routines where is_active = true)   <-- anon readable
- routines_insert_own / routines_update_own / routines_delete_own (auth.uid() = user_id)

routine_exercises
- re_select (owner via EXISTS routines), re_insert, re_update, re_delete (owner)
- re_select_shared SELECT routine_id IN (active shared)   <-- anon readable

routine_days
- routine_days_select_own SELECT (owner) OR routine_id IN (active shared)   <-- anon readable (combined policy)
- routine_days_insert_own / update_own / delete_own (owner)

Note: expires_at is ignored by all policies.

## Migrations already applied (latest): 20260524150129_ensure_data_api_access_grants; earlier 20260330005107_shared_routines_read_access added the shared read policies.

## RN app compatibility constraint
The RN app (still in production during migration) imports by code with a direct nested select:
`from('shared_routines').select('*, routines(*, routine_days(*), routine_exercises(*, exercises(*, muscle_groups(*))))').eq('share_code', code).eq('is_active', true).maybeSingle()`
then inserts routines/routine_exercises/routine_days as the logged-in user. Share creation inserts into shared_routines directly.
=> Step 1 (now) must NOT break this for authenticated users. Step 2 (after RN app is retired) removes direct shared read policies entirely; prepare that as a separate, NOT-applied migration file.
