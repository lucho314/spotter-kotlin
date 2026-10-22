-- handle_new_user() and update_personal_record() are SECURITY DEFINER trigger functions (fired by
-- auth.users.on_auth_user_created and public.workout_sets.on_workout_set_inserted), but the default
-- grants also expose them as /rest/v1/rpc endpoints to anon and authenticated (security advisors
-- 0028/0029). Calling a trigger function outside a trigger already fails, so this is defense in
-- depth. Triggers keep working: Postgres checks EXECUTE on the function only at CREATE TRIGGER time,
-- not when the trigger fires.
revoke execute on function public.handle_new_user() from public, anon, authenticated;
revoke execute on function public.update_personal_record() from public, anon, authenticated;
