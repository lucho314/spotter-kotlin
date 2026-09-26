# Changelog

Historial de cambios por fase de la migración de Spotter (React Native → Android nativo en
Kotlin). Cada entrada resume lo que esa fase entregó y por qué; el detalle línea a línea de cada
ciclo de revisión está en `MIGRATION_PLAN.md` §10 y en `review_carryover.md`.

---

## [FASE 3] — 2026-09-26 — APROBADO (ciclo de revisión 4)

UI de rutinas, plantillas, catálogo de ejercicios y detalle de ejercicio.

- **Rutinas:** lista (`RoutinesScreen`, orden por día de semana, importar por código, accesos a
  plantillas/archivadas/IA, archivar con confirmación, pull-to-refresh), archivadas
  (`ArchivedRoutinesScreen`, con restaurar), crear/editar (`RoutineEditScreen`), detalle
  (`RoutineDetailScreen`: días con drag & drop vía `sh.calvin.reorderable`, mover ejercicio de día,
  agregar/renombrar/borrar día, "sin día asignado"), agregar ejercicio (`AddExerciseScreen`:
  búsqueda insensible a acentos, filtro por grupo muscular, `NumberStepper` de sets/reps/descanso).
- **Plantillas:** lista con filtros por objetivo y días, detalle con días expandibles,
  `AdoptTemplateUseCase` (crea una rutina por día, con compensación transaccional ante fallo o
  cancelación).
- **Detalle de ejercicio:** `ExerciseMedia` con ExoPlayer para video (`.mp4`/`.webm`/`.m3u8`), GIF
  animado o imagen estática vía Coil, con placeholder.
- **Design system:** `NumberStepper`, `SectionHeader`, `SpotterChip`, `ConfirmDialog`, `ExerciseMedia`.
- **Infraestructura de eventos:** `ObserveAsEvents` (colector lifecycle-aware de eventos one-shot),
  `RouteArgs` (lectura de argumentos de ruta por `SavedStateHandle` en vez de `toRoute()`),
  `dropUnlessResumed` en clicks de usuario del `SpotterNavHost`, `<plurals>` reales en español.

**Desviaciones y decisiones registradas durante la implementación** (detalle en
`MIGRATION_PLAN.md` §10):
- `AdoptTemplateUseCase` recibe el `TemplateDetail` ya cargado en memoria (no vuelve a pedirlo por
  red).
- Rutinas adoptadas de una plantilla no llevan `routine_days`: sus ejercicios van a
  `UNASSIGNED_DAY_NUMBER`, no a `1` (evita el bug de rutinas legacy de RN con día `1` fantasma).
- `savedStateHandle.toRoute<T>()` no decodifica argumentos fuera de un `NavBackStackEntry` real;
  los ViewModels con argumentos de ruta leen `SavedStateHandle["..."]` directamente.
- `ArchivedRoutinesViewModel` se agregó como ViewModel propio (no reusa `RoutinesViewModel`: su
  fuente y sus acciones son distintas).
- Item de FASE 2 resuelto de paso: `ActiveWorkoutRepositoryImpl.start()` ahora relanza si la
  re-consulta tras `SQLiteConstraintException` no encuentra nada, en vez de devolver `Started`.

**Bug bloqueante encontrado y corregido en el ciclo 4 de revisión:** `dropUnlessResumed` envolvía
callbacks de *resultado* de operaciones async (`onRoutinesCreated`, `onSaved`, `onExerciseAdded`,
`onArchived`, `onDone` de Onboarding) en vez de callbacks de *click*; un evento entregado con la
pantalla en background quedaba descartado para siempre (p. ej. adoptar una plantilla y apretar Home
a mitad de camino perdía el resultado, permitiendo crear rutinas duplicadas con un segundo tap).
Corregido con `ObserveAsEvents` (ver arriba), adoptado en las 6 pantallas de esta fase.

Otros cambios de los ciclos 2 y 3 de revisión: confirmaciones para renombrar/borrar día y quitar
ejercicio, orden optimista descartado correctamente ante un reordenamiento fallido, compensación de
`AdoptTemplateUseCase` también ante cancelación (`NonCancellable`), separación consistente de
loading "sin caché todavía" (pantalla completa) vs. error transitorio con datos visibles
(snackbar), relay de snackbars entre pantallas vía `NavBackStackEntry.savedStateHandle`, regla
compartida `RoutineOrdering.unassignedBucketDayNumber` para no duplicar el grupo "sin día
asignado" entre rutinas legacy y rutinas nuevas.

**Diferido explícitamente:** `SavedStateHandle` para sobrevivir a la muerte de proceso en los
formularios de edición y en la búsqueda de agregar ejercicio (nice-to-have, no bloqueante).

---

## [FASE 2] — 2026-09-26 — APROBADO

Capa de datos y dominio.

- **DTOs y mappers:** `data/remote/dto/Dtos.kt` (espejo `@Serializable` del esquema Supabase en
  vivo, snake_case) y un mapper DTO/entidad ↔ dominio por familia de modelo.
- **Data sources remotos:** una interfaz + `Supabase*RemoteDataSource` por dominio (ejercicios,
  rutinas, plantillas, entrenamientos, progreso, perfil, compartir, importación con IA); las
  llamadas no pasan por `safeCall` en el data source (sí en el repositorio), para que los fakes de
  test lancen excepciones simples.
- **Room v1:** `SpotterDatabase` con caché de lectura (`CachedPayloadEntity`), entrenamiento activo
  (`ActiveSessionEntity`/`ActiveExerciseEntity`/`ActiveSetEntity`, con operaciones atómicas como
  `startIfAbsent`/`replaceActive`/`insertNextSet`) y outbox de sincronización
  (`PendingWorkoutEntity`/`PendingWorkoutSetEntity`, estados `PENDING`/`FAILED`).
- **12 repositorios** (`data/repository/*Impl.kt` + `RepositoryModule`): `RoutineRepositoryImpl` y
  `ExerciseRepositoryImpl` implementan cache-then-network sobre el caché JSON; el resto va directo
  a red con `AppResult` explícito, salvo `ActiveWorkoutRepositoryImpl`/`PendingWorkoutRepositoryImpl`
  sobre Room relacional.
- **Dominio:** modelos puros (`domain/model`), 12 interfaces de repositorio (`domain/repository`),
  cálculos puros 100% testeados (`domain/calc`: `WorkoutMath`, `WeightConverter`,
  `WeightInputParser`, `WeekRange`, `AgeCalculator`, `RoutineOrdering`, etc.).
- Los use cases del plan (`StartWorkoutUseCase`, `FinishWorkoutUseCase`, etc.) se difirieron
  deliberadamente a las fases que primero los necesitan; ninguno de los tests de esta fase los
  ejercita.
- `TimeProvider`/`IdGenerator` inyectables, con fakes para tests deterministas.

**Aceptación:** compila, todos los tests pasan, esquema Room exportado a `app/schemas/`.

---

## [FASE 1] — 2026-09-25 — APROBADO

Scaffold, build, DI, cliente Supabase, sesión segura, autenticación y esqueleto de navegación.

- **Build:** `settings.gradle.kts`, `build.gradle.kts` raíz y de `:app`, `gradle/libs.versions.toml`
  (Gradle 9.6.0 vía wrapper, Kotlin 2.4.20, AGP 9.4.1, compileSdk/targetSdk 36, minSdk 26); R8 +
  shrink resources en release; tarea `verifyReleaseConfig` que falla si faltan las claves de
  Supabase.
- **Seguridad:** `EncryptedSessionManager`/`EncryptedCodeVerifierCache` (Tink AES256-GCM +
  Android Keystore, persistidos en DataStore), `KeysetRecoveryPolicy` (retry → wipe+rebuild →
  fail-closed), manifest endurecido (`allowBackup=false`, `usesCleartextTraffic=false`,
  `network_security_config` sin pinning), `Logger` no-op en release.
- **Cliente Supabase:** supabase-kt 3.8.0 (Auth PKCE, Postgrest, Functions) + Ktor OkHttp.
- **Autenticación:** login con Google vía Credential Manager (primario) con fallback a OAuth PKCE
  por navegador (deep link `spotter://auth/callback`); `NonceGenerator` (SHA-256).
- **Navegación:** rutas `@Serializable`, `DeepLinkParser` (fuera de `navDeepLink`, para no saltear
  el guardia de sesión), `RootViewModel`/`SpotterRoot` como guardia de autenticación + onboarding.
- **Onboarding y perfil mínimo:** se muestra una vez por usuario; `ProfileScreen` con identidad y
  cerrar sesión.
- **Design system base:** `SpotterButton`, `SpotterCard`, `SpotterTextField`, `LoadingState`,
  `ErrorState`, `EmptyState`, `ConfirmDialog`, tema Compose.

---

## Decisión de backend — 2026-09-25

El usuario decidió **no modificar el proyecto Supabase en vivo**. Se revisó y aprobó una
corrección para los hallazgos B1 (crítico), B2 (alto) y B6 (medio) — migraciones SQL, edge function
reforzada y rollback —, dejada lista sin aplicar en `supabase/_proposed/` (ver
`supabase/_proposed/README.md`). El cliente Kotlin funciona contra el backend en vivo tal cual está,
sin depender de esa corrección. Detalle completo de los hallazgos B1-B6 en `MIGRATION_PLAN.md` §8 y
`docs/review_carryover.md`.

---

## Planificado (fases 4 a 7, no iniciadas)

Ver `MIGRATION_PLAN.md` §10 y la sección "Planificado" de `arquitectura.md`/`componentes.md` para
el detalle. Resumen: entrenamiento activo + temporizador de descanso + sincronización en segundo
plano (FASE 4); historial, progreso, dashboard, perfil completo (FASE 5); compartir/importar
rutinas (código e IA) y exportación de entrenamientos (FASE 6); endurecimiento de release y
verificación en dispositivo real (FASE 7).
