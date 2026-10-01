# Backend Supabase: cambios aplicados y paso pendiente

El 2026-09-29 se aplicaron al proyecto `bqqpfldwzkfdvlvfjkbr` las cinco migraciones de
`migrations/`, en orden de nombre. Sus nombres en el historial remoto son `optimize_owner_rls`,
`b2_restrict_shared_routine_reads`, `b2_shared_routine_rpcs`,
`b1_create_routine_from_import_rpc` y `b6_ai_import_rate_limit`. La Edge Function
`parse-routine-image` se desplegó en versión 11 con `verify_jwt=true`. Los archivos permanecen
en `_proposed/` para conservar los enlaces existentes; representan la versión aplicada y no deben
desplegarse de nuevo por su cuenta.

`migrations_pending/b2_step2_drop_direct_shared_reads.sql` **no está aplicado**. Retira las
lecturas directas de shares y rutinas compartidas, que todavía usa la app React Native anterior.
Aplicarlo solo cuando ese cliente haya sido retirado y el cliente Kotlin use las RPC de sharing.
Mientras tanto, un usuario autenticado puede enumerar los shares activos; el rol anónimo ya no
puede leerlos.

`rollback/20260925_b1_b2_rollback.sql` es solo para una emergencia. Su parte A vuelve a abrir
la lectura anónima; no debe ejecutarse como paso normal. El estado previo y las verificaciones
posteriores están documentados en [`docs/query_index_review_2026-09-29.md`](../../docs/query_index_review_2026-09-29.md).
