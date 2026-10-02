-- B2 step 1 (2026-09-25): shared routines are no longer readable by the anon role.
-- Direct shared-read policies are kept ONLY for authenticated users (the React Native app still
-- reads shared routines directly) and now honor expires_at.
-- shared_routines INSERT/UPDATE now require owning the referenced routine.
-- Step 2 (supabase/_proposed/migrations_pending/b2_step2_drop_direct_shared_reads.sql) removes the direct
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
create index if not exists shared_routines_routine_owner_recent_active_idx
  on public.shared_routines (routine_id, shared_by, created_at desc) where is_active;

-- Deactivate pre-existing forged shares: rows created before shared_insert_own required ownership
-- (e.g. via the old `auth.uid() = shared_by` check, which let a user share a routine they don't own).
-- New rows can no longer be forged; the RPCs below add the same ownership check as defense in depth.
update public.shared_routines s
set is_active = false
where s.is_active
  and not exists (
    select 1 from public.routines r
    where r.id = s.routine_id and r.user_id = s.shared_by
  );
