# Live Supabase schema snapshot (public), queried 2026-09-26 via MCP (read-only)
NN = NOT NULL, d= default. Backend must NOT be modified (user decision 2026-09-25).

- exercises: id integer NN, name text NN, name_en text NN, muscle_group_id smallint NN, equipment equipment_type NN d='barbell', image_url text, gif_url text, secondary_muscles jsonb NN d='[]', instructions jsonb NN d='[]', difficulty text (CHECK beginner|intermediate|advanced), category text (CHECK compound|isolation|cardio|stretch|plyometric), exercisedb_id text
- muscle_groups: id smallint NN, name text NN, name_en text NN
- personal_records: id uuid NN, user_id uuid NN, exercise_id integer NN, best_weight_kg numeric NN, best_reps_at_weight smallint NN, estimated_1rm numeric NN, achieved_at timestamptz NN, updated_at timestamptz NN d=now()
- profiles: id uuid NN, display_name text NN, avatar_url text, created_at timestamptz NN, updated_at timestamptz NN, weight_kg numeric, height_cm smallint, birth_date date, fitness_goal text
- routine_days: id uuid NN, routine_id uuid (nullable), day_number integer NN, name text NN; UNIQUE(routine_id, day_number); FK routine ON DELETE CASCADE
- routine_exercises: id uuid NN, routine_id uuid NN, exercise_id integer NN, sort_order smallint NN d=0, target_sets smallint NN d=3, target_reps smallint NN d=10, rest_seconds smallint NN d=90, day_number integer NN d=1; UNIQUE(routine_id, exercise_id, day_number)
- routine_templates: id uuid NN, name text NN, name_es text NN, description text, description_es text, goal fitness_goal NN, difficulty template_difficulty NN, days_per_week smallint NN, is_active boolean NN d=true, sort_order smallint NN d=0
- routines: id uuid NN, user_id uuid NN, name text NN, description text, days_per_week smallint (CHECK 1..7), is_archived boolean NN d=false, created_at timestamptz NN, updated_at timestamptz NN, source_template_id uuid
- shared_routines: id uuid NN, routine_id uuid NN, shared_by uuid NN, share_code text NN UNIQUE, is_active boolean NN d=true, created_at timestamptz NN, expires_at timestamptz
- template_day_exercises: id uuid NN, template_day_id uuid NN, exercise_id integer NN, sort_order smallint NN d=0, target_sets smallint NN d=3, target_reps smallint NN d=10, rest_seconds smallint NN d=90, notes text
- template_days: id uuid NN, template_id uuid NN, day_number smallint NN, name text NN, name_es text NN, description text
- workout_sessions: id uuid NN, user_id uuid NN, routine_id uuid, started_at timestamptz NN d=now(), completed_at timestamptz, notes text, status session_status NN d='in_progress'
- workout_sets: id uuid NN, session_id uuid NN, exercise_id integer NN, set_number smallint NN, weight_kg numeric NN, reps smallint NN, rpe numeric, is_warmup boolean NN d=false, completed_at timestamptz NN d=now()

Enums: equipment_type = barbell,dumbbell,machine,cable,bodyweight,kettlebell,band,other; fitness_goal = strength,hypertrophy,fat_loss,general; session_status = in_progress,completed,cancelled; template_difficulty = beginner,intermediate,advanced.
Note: profiles.fitness_goal is TEXT (not the enum). Triggers: routines BEFORE UPDATE set_updated_at; shared_routines BEFORE INSERT generate_share_code (broken if share_code NULL — always send a code); personal_records maintained by a trigger on workout_sets INSERT only (B3).
