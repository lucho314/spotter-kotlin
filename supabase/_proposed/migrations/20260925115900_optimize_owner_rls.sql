-- Keep the existing ownership predicates while evaluating auth.uid() once per statement.
-- The two legacy profiles policies duplicate profiles_select_own/profiles_update_own.
drop policy if exists "Users can read their own profile" on public.profiles;
drop policy if exists "Users can update their own profile" on public.profiles;

do $$
declare
  p record;
  new_using text;
  new_check text;
begin
  for p in
    select schemaname, tablename, policyname, qual, with_check
    from pg_policies
    where schemaname = 'public'
      and (qual like '%auth.uid()%' or with_check like '%auth.uid()%')
  loop
    new_using := replace(replace(replace(p.qual, '(SELECT auth.uid())', '__already_wrapped_uid__'),
      'auth.uid()', '(select auth.uid())'), '__already_wrapped_uid__', '(select auth.uid())');
    new_check := replace(replace(replace(p.with_check, '(SELECT auth.uid())', '__already_wrapped_uid__'),
      'auth.uid()', '(select auth.uid())'), '__already_wrapped_uid__', '(select auth.uid())');
    execute format(
      'alter policy %I on %I.%I%s%s',
      p.policyname, p.schemaname, p.tablename,
      case when new_using is null then '' else ' using (' || new_using || ')' end,
      case when new_check is null then '' else ' with check (' || new_check || ')' end
    );
  end loop;
end;
$$;
