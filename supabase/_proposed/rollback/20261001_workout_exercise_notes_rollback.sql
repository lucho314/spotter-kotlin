-- Removes the per-exercise notes table (and every note stored in it). The app tolerates the table
-- being absent: reading a note fails silently and uploading one is dropped without failing sync.
drop table if exists public.workout_exercise_notes;
