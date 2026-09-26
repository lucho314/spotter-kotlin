# NOT APPLIED — proposed backend fix for B1/B2 (+B6)

**Status: reviewed and approved on 2026-09-25. The user then decided NOT to change the backend.**
Nothing in this directory is live. The Supabase project still runs exactly as it did before this
review: the original RLS policies (roles `{public}` on the shared-read policies, `shared_insert_own`
/ `shared_update_own` without an ownership check), the original `parse-routine-image` v10 (trusts
`user_id` from the body, uses the service role key, no AI-import quota), and no `ai_import_usage`
table or new RPCs. The Kotlin app in `docs/MIGRATION_PLAN.md` targets that live state, not this one
— see `docs/MIGRATION_PLAN.md` §8 and `docs/backend/B1_B2_PLAN.md` for the reasoning.

Do not deploy any of this without the user's explicit go-ahead. If that changes, this whole
directory is meant to be moved back to `supabase/` (or copied) and applied as follows.

## What's here

- `config.toml` — sets `verify_jwt = true` for `parse-routine-image`.
- `migrations/` — 4 SQL files, in filename order:
  1. `20260925120000_b2_restrict_shared_routine_reads.sql` — restricts shared-read policies to
     `authenticated`, honors `expires_at`, requires routine ownership on `shared_insert_own`/
     `shared_update_own`, and deactivates any pre-existing forged shares.
  2. `20260925120100_b2_shared_routine_rpcs.sql` — `get_shared_routine`, `import_shared_routine`
     (`SECURITY DEFINER`), `create_share` (`SECURITY INVOKER`).
  3. `20260925120200_b1_create_routine_from_import_rpc.sql` — `create_routine_from_import`
     (`SECURITY INVOKER`, atomic AI-import write).
  4. `20260925120300_b6_ai_import_rate_limit.sql` — `ai_import_usage` table + `consume_ai_import_quota`
     (`SECURITY DEFINER`, 20 attempts/user/24h).
- `migrations_pending/b2_step2_drop_direct_shared_reads.sql` — a FIFTH, later step. Do not apply
  together with the four above; only after the React Native app (`E:\Spotter`) is retired from
  production (see the header comment in the file itself). Needs a fresh timestamp in its filename
  before it can be moved into `migrations/`.
- `rollback/20260925_b1_b2_rollback.sql` — emergency-only, not a migration. See its header.
- `functions/parse-routine-image/` — `index.ts` + `validation.ts` (the edge function) and
  `validation_test.ts` (unit tests, not deployed).

## How to apply, if the decision is reconsidered

1. Preflight (read-only) and the ownership/`forged_shares` check — see `docs/backend/B1_B2_PLAN.md`
   §5.2 step 0.
2. Apply the 4 files in `migrations/` in filename order (steps above).
3. Verify grants/policies and run the `DO $verify$` block — `docs/backend/B1_B2_PLAN.md` §5.2 steps 2-3.
4. Deploy the edge function: `name: parse-routine-image`, `entrypoint_path: index.ts`, files
   `index.ts` + `validation.ts` (not the test file), **`verify_jwt: true`**.
5. Smoke tests, `get_logs`, `get_advisors(type: "security")` — §5.2 steps 5-6.
6. Update the Kotlin app to match (RPC calls instead of direct table reads/writes for sharing and
   AI import, `ParseRoutineImageRequest` without `user_id`, etc.) — this was reverted out of
   `docs/MIGRATION_PLAN.md` when the backend-change decision was reversed and would need to be
   re-applied together with the deploy.
7. `migrations_pending/b2_step2_drop_direct_shared_reads.sql` stays NOT applied until the RN app is
   retired (separate decision).

Full detail, rationale and the exact `DO $verify$` verification block: `docs/backend/B1_B2_PLAN.md`.
Live-state context this was reviewed against: `docs/backend/B1_B2_context.md`.
