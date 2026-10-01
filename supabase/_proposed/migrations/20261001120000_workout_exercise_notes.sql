-- Short per-exercise note written during a workout ("la próxima aumentar", "me molestó el hombro")
-- and shown the next time the user opens that exercise's last session. One note per
-- (session, exercise); the 50-character limit matches ExerciseNote.MAX_LENGTH in the app.
create table if not exists public.workout_exercise_notes (
  session_id uuid not null references public.workout_sessions (id) on delete cascade,
  exercise_id integer not null references public.exercises (id) on delete cascade,
  note text not null check (char_length(note) between 1 and 50),
  created_at timestamptz not null default now(),
  primary key (session_id, exercise_id)
);

-- The primary key already covers lookups by session_id (and by session_id + exercise_id).
create index if not exists workout_exercise_notes_exercise_id_idx
  on public.workout_exercise_notes (exercise_id);

alter table public.workout_exercise_notes enable row level security;

revoke all on public.workout_exercise_notes from anon;
grant select, insert, update, delete on public.workout_exercise_notes to authenticated;

-- Ownership goes through the parent session, same as workout_sets.
drop policy if exists workout_exercise_notes_owner on public.workout_exercise_notes;
create policy workout_exercise_notes_owner on public.workout_exercise_notes
  for all to authenticated
  using (
    exists (
      select 1 from public.workout_sessions s
      where s.id = workout_exercise_notes.session_id
        and s.user_id = (select auth.uid())
    )
  )
  with check (
    exists (
      select 1 from public.workout_sessions s
      where s.id = workout_exercise_notes.session_id
        and s.user_id = (select auth.uid())
    )
  );
