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
