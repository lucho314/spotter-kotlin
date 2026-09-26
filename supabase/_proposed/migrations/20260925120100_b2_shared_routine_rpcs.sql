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

  -- Defense in depth: also require the share's routine to still be owned by whoever created it
  -- (shared_insert_own already enforces this on write; forged/legacy rows are excluded here too).
  select s.* into v_share
  from public.shared_routines s
  join public.routines r on r.id = s.routine_id and r.user_id = s.shared_by
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

  -- Defense in depth: also require the share's routine to still be owned by whoever created it
  -- (shared_insert_own already enforces this on write; forged/legacy rows are excluded here too).
  select r.* into v_routine
  from public.shared_routines s
  join public.routines r on r.id = s.routine_id and r.user_id = s.shared_by
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
