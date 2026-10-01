# Revisión de consultas e índices — 2026-09-29

## Correcciones aplicadas el 2026-09-29

Esta revisión describe el estado **antes** de los cambios. Después se aplicaron en Supabase las
migraciones `optimize_owner_rls`, `b2_restrict_shared_routine_reads`, `b2_shared_routine_rpcs`,
`b1_create_routine_from_import_rpc` y `b6_ai_import_rate_limit`. La primera reescribe las políticas
de propietario con `(select auth.uid())` y elimina los dos duplicados de `profiles`; la segunda
impide lecturas anónimas de rutinas compartidas, exige que quien comparte sea dueño de la rutina,
respeta el vencimiento y crea el índice parcial `(routine_id, shared_by, created_at DESC)`.

La Edge Function `parse-routine-image` quedó activa en versión 11 con `verify_jwt=true`: ignora
`user_id` del body, usa RLS del usuario, limita intentos y crea cada rutina en una transacción.
Se ejecutó `ANALYZE` en las tablas afectadas. La cola local de Garmin usa ahora el índice compuesto
`(user_id, status, created_at_epoch_ms)` con migración Room v2→v3. El cliente Kotlin ya no envía
`user_id` a la función y muestra un mensaje específico cuando se agota la cuota.

Verificación: rol `anon` ve cero filas en `shared_routines`, `routines`, `routine_exercises` y
`routine_days`; un usuario autenticado conserva acceso a sus rutinas y la vista previa por código.
La RPC de importación creó rutina, día y ejercicio en una transacción de prueba que luego se revirtió.
La función responde 401 sin JWT. Con `enable_seqscan=off`, `EXPLAIN` confirma que la búsqueda
`routine_id` + `shared_by` + fecha usa el índice nuevo; con nueve filas, el plan normal puede
preferir un recorrido secuencial. Performance Advisor pasó de 28 a cero avisos de `auth_rls_initplan`
y de 25 a cuatro de políticas permisivas múltiples. Las 47 pruebas Deno, el chequeo de tipos,
`:app:testDebugUnitTest` (787 pruebas) y `:app:assembleDebug` pasaron.

**Pendiente por compatibilidad:** cualquier usuario autenticado aún puede listar shares activos
mediante las lecturas directas que usa la app React Native anterior. Las cuatro políticas de lectura
superpuestas son deliberadas hasta retirar ese cliente y aplicar B2 paso 2. Los índices para
ordenamientos de tablas con menos de 650 filas y la agregación de progreso no se añadieron sin
evidencia de latencia; se deben medir con más volumen. Tampoco se retiró `idx_ws_in_progress` sin
confirmar el uso de los clientes antiguos.

## Alcance y evidencia

Revisión de solo lectura del proyecto `bqqpfldwzkfdvlvfjkbr`: 13 tablas, 29 índices y 4 funciones en `public` (PostgreSQL 17.6); consultas PostgREST de los data sources Kotlin, consultas de los cuatro DAO Room, SQL propuesto en `supabase/_proposed/` y la Edge Function `parse-routine-image` desplegada (versión 10). Se contrastaron definiciones reales de índices y políticas, `pg_stat_user_tables`, `pg_stat_user_indexes`, `pg_stat_statements`, planes `EXPLAIN` y Performance Advisor. No se modificó la base de datos.

Los datos son todavía pequeños: `workout_sets` tiene unas 638 filas vivas; `routine_exercises`, 254; `workout_sessions`, 40; `routines`, 23; `shared_routines`, 9. Un `Seq Scan` sobre estas tablas puede ser la elección correcta hoy. Los contadores de `pg_stat_statements` acumulan actividad desde el **20 de marzo de 2026** y pueden incluir la app React Native anterior; no atribuyo todas esas consultas a Kotlin. Los planes solicitados por MCP se calcularon como rol administrativo, así que no reproducen exactamente el costo de RLS para un usuario autenticado.

## Hallazgos, por prioridad

### Alta — Políticas RLS hacen trabajo repetido en las consultas más usadas

Performance Advisor señala **28 políticas** que llaman `auth.uid()` por fila y **25 avisos** de políticas permisivas múltiples (estos últimos se repiten por rol; no son 25 pares diferentes). Las políticas de `routines`, `routine_exercises`, `workout_sets`, `profiles` y otras tablas usan ese patrón. Hay dos políticas equivalentes de lectura y dos de actualización en `profiles`. Las consultas de rutinas con relaciones anidadas registraron 207 llamadas a **14,89 ms de media** y 185 a **8,90 ms**; la consulta de último récord personal, 327 llamadas a **7,73 ms**. Esas cifras muestran dónde medir primero, pero no prueban que RLS sea el único costo.

**Acción recomendada:** reescribir las políticas aplicables con `(select auth.uid())`, consolidar las duplicadas de `profiles` y probar las reglas como `anon`, propietario y otro usuario. Las políticas de lectura de rutinas compartidas tienen semántica distinta a las de propietario y requieren una revisión cuidadosa; no deben eliminarse solo para silenciar el linter. La [guía RLS de Supabase](https://supabase.com/docs/guides/database/postgres/row-level-security#call-functions-with-select) explica por qué el `select` permite calcular la identidad una vez por sentencia.

### Alta — `shared_routines` carece del índice que necesita su búsqueda por rutina

`findActiveShares` filtra `routine_id`, `shared_by` e `is_active`, ordena por `created_at DESC` y limita a cinco filas ([fuente](../app/src/main/java/com/lucho314/spotter/data/remote/datasource/SupabaseSharingRemoteDataSource.kt)). Al momento de la revisión, en producción solo había un índice parcial por `share_code` y un índice único por el mismo campo. El `EXPLAIN` de esa búsqueda usaba `idx_shared_code` para leer las filas activas, filtraba luego `routine_id` y `shared_by`, y finalmente ordenaba. El índice compuesto se añadió después mediante [B2](../supabase/_proposed/migrations/20260925120000_b2_restrict_shared_routine_reads.sql).

**Acción recomendada:** al desplegar B2, evaluar un índice parcial `ON shared_routines (routine_id, shared_by, created_at DESC) WHERE is_active` para cubrir también la consulta Kotlin. Su prefijo `routine_id` sirve para las búsquedas de políticas compartidas. Con solo nueve filas, la mejora actual será mínima; comprobar el plan y la carga de escritura antes de añadirlo.

### Alta — La Edge Function desplegada multiplica escrituras por día

La versión 10 de `parse-routine-image` lee una vez `exercises`, inserta una `routine`, y dentro de un bucle hace un `insert` de `routine_days` y otro de `routine_exercises` por cada día con ejercicios: aproximadamente **2 + 2N solicitudes de base de datos** para N días. Si falla una de las últimas inserciones, las anteriores ya pueden haber quedado guardadas. El código local propuesto usa `create_routine_from_import` para agrupar la operación, pero esa RPC y las migraciones B1/B2/B6 no aparecen en la lista de migraciones desplegadas.

**Acción recomendada:** revisar y desplegar el flujo transaccional propuesto en un cambio separado, verificando permisos y comportamiento de fallo. El índice actual de `exercises(id)` y los índices de relaciones son suficientes para las lecturas de catálogo; aquí domina la cantidad de viajes y la atomicidad.

### Media — Ordenamientos sin índice compuesto, con impacto futuro

| Consulta | Índice actual | Mejora a evaluar cuando crezcan los datos |
|---|---|---|
| Rutinas archivadas por usuario y `created_at DESC` ([fuente](../app/src/main/java/com/lucho314/spotter/data/remote/datasource/SupabaseRoutineRemoteDataSource.kt)) | `idx_routines_user` solo incluye `NOT is_archived` | Parcial `(user_id, created_at DESC) WHERE is_archived`; el `EXPLAIN` actual hace scan y sort de una tabla diminuta. |
| Última sesión completa por `completed_at DESC` ([fuente](../app/src/main/java/com/lucho314/spotter/data/remote/datasource/SupabaseWorkoutRemoteDataSource.kt)) | `(user_id, started_at DESC)` | Parcial `(user_id, completed_at DESC NULLS LAST) WHERE status = 'completed'`; hoy la consulta observada tarda **1,02 ms de media** en 101 llamadas. |
| Último récord personal por `updated_at DESC` ([fuente](../app/src/main/java/com/lucho314/spotter/data/remote/datasource/SupabaseProgressRemoteDataSource.kt)) | `idx_pr_user` y único `(user_id, exercise_id)` | Si la tabla crece, reemplazar `idx_pr_user` por `(user_id, updated_at DESC)` después de medir; no sumar índices redundantes por anticipado. |
| Lista de rutinas activas por `created_at DESC` | Parcial `(user_id) WHERE NOT is_archived` | Si la lista crece, considerar extender el índice existente a `(user_id, created_at DESC) WHERE NOT is_archived`. |

Las 13 tablas tienen menos de 650 filas vivas; los nuevos índices también encarecen `INSERT`/`UPDATE`. La [guía de optimización de Supabase](https://supabase.com/docs/guides/database/query-optimization) recomienda comparar planes y costo real antes de conservar un índice nuevo.

### Media — La cola local de Garmin conserva filas y filtra por estado

Room tiene solo `Index("user_id")` para `garmin_upload` ([entidad](../app/src/main/java/com/lucho314/spotter/core/database/entity/GarminUploadEntity.kt)). `getPending` filtra usuario y estado y ordena por `created_at_epoch_ms`; `observeFailedCount` y `resetFailedToPending` filtran usuario y estado ([DAO](../app/src/main/java/com/lucho314/spotter/core/database/dao/GarminUploadDao.kt)). Las filas `UPLOADED` se conservan para idempotencia, de modo que la tabla puede crecer aun cuando la cola pendiente sea corta.

**Acción recomendada:** si el volumen local lo justifica, sustituir el índice simple por `Index(value = ["user_id", "status", "created_at_epoch_ms"])` mediante una migración Room v2→v3 y comprobar con `EXPLAIN QUERY PLAN` en una base con datos representativos. Los índices de `active_session`/hijos, `pending_workout_set` y `cached_payload` sí cubren sus búsquedas principales; la cola `pending_workout` suele vaciarse al sincronizarse, por lo que no necesita aún otro índice.

### Media — Hay transferencia de datos que un índice no reducirá

La lista de rutinas carga `routine_exercises(id)` y `routine_days(*)` para cada rutina; en `pg_stat_statements` el patrón de resumen con relaciones aparece entre los mayores consumidores de tiempo. El progreso de un ejercicio pide hasta **500 series** para mostrar solo **12 sesiones** ([uso](../app/src/main/java/com/lucho314/spotter/domain/usecase/GetExerciseProgressUseCase.kt)); `idx_wsets_exercise_progress` sí soporta el filtro y orden principal. Si estas pantallas se vuelven lentas, medir tamaño de respuesta y número de filas por pantalla antes de añadir índices. Una proyección más pequeña o agregación del lado servidor puede reducir más trabajo que otro índice.

### Baja — Estadísticas y avisos de índices

`routines`, `shared_routines`, `routine_days` y `profiles` nunca registraron `ANALYZE` automático; `shared_routines` tiene 9 filas vivas pero el plan estima 325 candidatas activas. Recomiendo ejecutar `ANALYZE` en una ventana de mantenimiento y comparar los mismos planes. El Performance Advisor señala **siete claves foráneas sin índice de cobertura**; la más relacionada con una consulta actual es `shared_routines.routine_id`. Las otras (`personal_records.exercise_id`, `routine_exercises.exercise_id`, `routines.source_template_id`, `shared_routines.shared_by`, `template_day_exercises.exercise_id`, `workout_sessions.routine_id`) deben priorizarse si se borran padres con frecuencia o si aparecen en consultas reales; no se justifican siete índices adicionales con las tablas actuales. [Detalle del linter](https://supabase.com/docs/guides/database/database-linter?lint=0001_unindexed_foreign_keys).

El linter también marca `idx_ws_in_progress` con cero lecturas desde el inicio de las estadísticas. La app Kotlin guarda el entrenamiento activo en Room, pero conviene confirmar que ningún cliente antiguo dependa de ese índice antes de retirarlo. El índice único `shared_routines_share_code_key` tiene cero lecturas, pero **no** debe quitarse por ese motivo: protege la unicidad del código.

## Cobertura de índices que sí es adecuada

- `routines` activas: parcial `idx_routines_user` para `user_id` y `NOT is_archived`.
- Detalle de rutinas: PK de `routines` y `exercises`; `(routine_id, day_number)` de `routine_days`; `(routine_id, sort_order)` de `routine_exercises`.
- Historial: `(user_id, started_at DESC)` de `workout_sessions`; `(session_id, exercise_id, set_number)` de `workout_sets`.
- Progreso por ejercicio: `(exercise_id, completed_at DESC)` de `workout_sets`; único `(user_id, exercise_id)` para el `UPSERT` de `update_personal_record`.
- Plantillas: `(template_id, day_number)` de `template_days` y `(template_day_id, sort_order)` de `template_day_exercises`. El catálogo tiene 104 ejercicios y el listado completo no necesita un índice adicional.
- Room: PK y relaciones de `active_session`, `active_exercise`, `active_set` y `pending_workout_set` están indexadas. La PK `key` de `cached_payload` cubre las búsquedas por clave, aunque también se filtre `user_id`.

## Riesgo de acceso detectado al revisar RLS

La base actual concede `SELECT` a `anon` en `shared_routines`, `routines` y `routine_exercises`. Las políticas de lectura compartida se aplican a `{public}` y `shared_select_by_code` permite toda fila con `is_active = true`, sin exigir un código; las políticas de rutinas y ejercicios también permiten leer las rutinas activamente compartidas. Es un problema de alcance de lectura, independiente de los índices. La migración [B2 propuesta](../supabase/_proposed/migrations/20260925120000_b2_restrict_shared_routine_reads.sql) limita la lectura a `authenticated` y añade vencimiento, pero todavía no está desplegada. Conviene resolverlo antes de optimizar esas mismas políticas; cualquier cambio requiere pruebas de acceso y compatibilidad con la app anterior.

## Orden recomendado

1. Resolver el acceso a rutinas compartidas y optimizar RLS en una migración con pruebas de permisos.
2. Desplegar el flujo de importación transaccional y el índice de `shared_routines` después de validar su plan.
3. Medir nuevamente `pg_stat_statements` y los tamaños de respuesta con tráfico Kotlin reciente; los índices para ordenamientos se añaden solo cuando la cardinalidad y el plan lo justifiquen.
4. Evaluar el índice compuesto de Garmin con datos locales representativos y migración Room.

Fuentes técnicas: [Supabase Query Optimization](https://supabase.com/docs/guides/database/query-optimization), [Supabase RLS](https://supabase.com/docs/guides/database/postgres/row-level-security), [Supabase Inspect](https://supabase.com/docs/guides/observability/inspect), [SQLite Query Planning](https://sqlite.org/queryplanner.html).
