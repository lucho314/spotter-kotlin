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
