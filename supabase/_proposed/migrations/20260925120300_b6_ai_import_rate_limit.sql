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
