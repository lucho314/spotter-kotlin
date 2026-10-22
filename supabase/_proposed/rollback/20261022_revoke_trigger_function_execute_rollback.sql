-- Restores the default EXECUTE grants removed by 20261022120000_revoke_trigger_function_execute.sql
-- (state observed on 2026-10-22: public, anon and authenticated could execute both functions).
grant execute on function public.handle_new_user() to public, anon, authenticated;
grant execute on function public.update_personal_record() to public, anon, authenticated;
