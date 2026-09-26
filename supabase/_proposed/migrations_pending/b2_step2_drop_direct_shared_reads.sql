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
