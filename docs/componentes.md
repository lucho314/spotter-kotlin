# Componentes principales

Clases e interfaces reales del código tras FASES 1-4, agrupadas por capa. Cada identificador
listado aquí existe en `app/src/main/java/com/lucho314/spotter/` (verificado). La sección final
"Planificado" lista los nombres que el plan define para fases futuras y que **no existen todavía**.

## `core/common` — tipos y utilidades compartidas

- **`AppError`** (`sealed interface`): `Network`, `Unauthorized`, `NotFound`, `Conflict(detail)`,
  `Validation(reason: ValidationReason)`, `Server(code, message)`, `Unknown(cause)`. Nunca se
  construye a partir de `Throwable.message` de una excepción de red (ver `SafeCall.kt`).
- **`AppResult<T>`** (`sealed interface`): `Success<T>(value)` / `Failure(error: AppError)`.
  Extensiones: `map`, `onSuccess`, `onFailure`, `getOrNull`, `notNullOrNotFound()`,
  `requirePositiveOrNotFound()` (colapsa un conteo de filas afectadas en `NotFound` cuando es 0 —
  importante bajo RLS, donde un update/delete sin permiso devuelve 0 filas con status 2xx).
- **`ValidationReason`** (`enum`): 19 razones (`NAME_EMPTY`, `SETS_RANGE`, `DAYS_MAX_REACHED`, etc.),
  mapeadas a mensajes en `feature/common/ValidationMessages.kt`.
- **`Logger`** (interfaz) / **`AndroidLogger`**: `d`/`w`/`e`, no-op fuera de `BuildConfig.DEBUG`.
- **`TimeProvider`** (interfaz) / **`SystemTimeProvider`**: `now(): Instant`, `zone(): ZoneId`,
  inyectado para que ViewModels/use cases sean deterministas en tests (`FakeTimeProvider`).
- **`IdGenerator`** (interfaz) / **`RandomIdGenerator`**: `uuid(): String`, para IDs generados en
  cliente (entrenamiento activo, outbox) que luego se envían con upsert idempotente.
- **`@IoDispatcher`/`@DefaultDispatcher`**: qualifiers de Hilt para `CoroutineDispatcher`.

## `core/config`

- **`AppConfig`**: `data class(supabaseUrl, supabaseAnonKey, googleWebClientId: String?)` con
  `val isValid` (rechaza URL sin `https`, sin host, o con un path no vacío — supabase-kt lanza en
  ese caso en vez de solo comportarse mal).

## `core/security`

- **`AeadProvider`** (interfaz) / **`TinkAeadProvider`**: expone `aead(): Aead` (Tink AES256-GCM),
  keyset envuelto por una master key de Android Keystore, memoizado a mano con un lock.
- **`KeysetRecoveryPolicy<T>`**: política de reintento genérica (retry → wipe+rebuild →
  fail-closed) independiente de Tink/Android, testeada con fakes (`isUsingKeystore`, `build`,
  `wipe`, `sleep` inyectados).
- **`EncryptedSessionManager`**: implementa `io.github.jan.supabase.auth.SessionManager`.
  `saveSession`/`loadSession`/`loadSessionOrNull`/`deleteSession`; datos corruptos o
  indescifrables se tratan como "sin sesión" (nunca propagan la excepción).
- **`EncryptedCodeVerifierCache`**: implementa `CodeVerifierCache` con el mismo esquema para el
  code verifier de PKCE.

## `core/network`

- **`safeCall(block): AppResult<T>`** + **`ErrorMapper.map(Throwable): AppError`**
  (`core/network/SafeCall.kt`): frontera única entre excepciones de supabase-kt/Ktor/serialization
  y `AppError`. Relanza siempre `CancellationException`.
- **`NetworkMonitor`** (interfaz) / **`ConnectivityNetworkMonitor`**: `val isOnline: Flow<Boolean>`
  vía `callbackFlow` sobre `ConnectivityManager.registerDefaultNetworkCallback`.
- **`SupabaseModule`**: provee `Json` (config de deserialización tolerante — ver
  `MIGRATION_PLAN.md` §6), `SupabaseClient` (Auth en modo PKCE con los managers cifrados de
  arriba, Postgrest, Functions).

## `core/database`

- **`SpotterDatabase`** (Room, versión 1): expone `cachedPayloadDao()`, `activeWorkoutDao()`,
  `pendingWorkoutDao()`.
- **`CachedPayloadDao`**: `observe`/`get`/`upsert`/`delete`/`deleteByPrefix` sobre
  `CachedPayloadEntity(key, userId, json, ...)`.
- **`ActiveWorkoutDao`**: `observeByUser`/`getByUser`, `startIfAbsent` (atómico: solo inserta si el
  usuario no tiene sesión activa), `replaceActive` (descarta la existente e inserta la nueva),
  `insertNextSet` (calcula `set_number` e inserta en una sola transacción, evita duplicar número
  ante doble tap), `updateSetInputs`, `setCompleted`, `setCurrentExercise`, `setRestTimer`,
  `deleteSession`. Los inserts usan `OnConflictStrategy.ABORT` (nunca `REPLACE`) para que una
  colisión de `user_id` falle fuerte en vez de borrar en cascada la sesión en curso.
- **`PendingWorkoutDao`**: `observeCount`, `observeFailed`, `getPending`, `insertFull`, `delete`,
  `markFailed`, `resetToPending`, `recordAttempt` — estados `PENDING`/`FAILED`.

## `core/navigation`

- **`Routes.kt`**: rutas `@Serializable` (`DashboardRoute`, `RoutinesRoute`, `RoutineDetailRoute`,
  `RoutineEditRoute`, `AddExerciseRoute`, `ArchivedRoutinesRoute`, `TemplatesRoute`,
  `TemplateDetailRoute`, `ImportCodeRoute`, `ImportImageRoute`, `WorkoutRoute`,
  `SessionDetailRoute`, `ExerciseDetailRoute`, `OnboardingRoute`, `HistoryRoute`, `ProgressRoute`,
  `ProfileRoute`).
- **`SpotterNavHost`**: único `NavHost`; wired hoy: `Onboarding`, `Dashboard` (placeholder),
  `Routines`, `ArchivedRoutines`, `RoutineEdit`, `RoutineDetail`, `AddExercise`, `Templates`,
  `TemplateDetail`, `ExerciseDetail`, `Profile`, `Workout`; `History`/`Progress`/`ImportCode`/
  `ImportImage` renderizan `ComingSoonScreen`.
- **`NavController.navigateToWorkout()`** (en `SpotterNavHost.kt`): única forma de abrir
  `WorkoutRoute`, siempre con `launchSingleTop = true` (nunca dos `WorkoutScreen` apiladas). La
  usan el banner "Entrenamiento en curso", `RoutineDetailScreen.onOpenWorkout` y la notificación
  "Descanso terminado" (vía `SpotterRoot`).
- **`DeepLink`** (`AuthCallback`, `ImportRoutine(code)`) + **`DeepLinkParser.parse(raw): DeepLink?`**.
- **`RouteArgs`**: claves de argumento compartidas entre las rutas y los ViewModels que leen
  `SavedStateHandle` directamente.
- **`TopLevelDestination`** (enum): 5 destinos de la barra inferior.

## `domain/model` — modelos puros

Sin lógica de infraestructura. Principales: `RoutineSummary`/`RoutineDetail`/`RoutineDay`/
`RoutineExercise`/`RoutineInput`/`NewRoutineExercise`/`RoutineExercisePatch`, `Exercise`/
`MuscleGroup`/`Equipment`/`Difficulty`/`ExerciseCategory`, `RoutineTemplateSummary`/`TemplateDetail`/
`TemplateDay`/`TemplateExercise`/`TemplateGoal`, `WorkoutSet`/`WorkoutSessionSummary`/
`WorkoutSessionDetail`/`LastExerciseSession`, `ActiveWorkout`/`ActiveExercise`/`ActiveSet`/
`RestTimer`/`ActiveWorkoutStartOutcome`, `PendingWorkout`/`PendingSet`/`PendingStatus`,
`Profile`/`ProfileStats`/`ProfileGoal`, `PersonalRecord`/`ExerciseProgressPoint`,
`SharedRoutinePreview`/`SharedRoutineContent`/`SharedRoutineDay`/`SharedRoutineExercise`,
`AuthUser`/`AuthState`, `ShareCode` (value class con `parse`/`generate`), `WeightUnit` (`KG`/`LB`),
`DashboardStats` (sin consumidores todavía — modelo listo para el dashboard de FASE 5).

## `domain/repository` — 12 interfaces (todas con implementación en `data/repository`)

| Interfaz | Métodos clave |
|---|---|
| `AuthRepository` | `authState: Flow<AuthState>`, `currentUser()`, `signInWithGoogleIdToken`, `startGoogleOAuth`, `syncProfileDisplayName`, `signOut` |
| `PreferencesRepository` | `weightUnit: Flow<WeightUnit>`, `setWeightUnit`, `isOnboardingDone(userId)`, `setOnboardingDone`, `clearUserScoped` |
| `RoutineRepository` | `observeRoutines`/`refreshRoutines`, `observeArchivedRoutines`/`refreshArchived`, `observeRoutine`/`refreshRoutine`, `createRoutine`, `updateRoutine`, `setArchived`, `deleteRoutine`, `addExercise(s)`, `updateRoutineExercise`, `removeExercise`, `reorderExercises`, `addDays`, `renameDay`, `deleteDay` |
| `ExerciseRepository` | `observeCatalog`, `observeMuscleGroups`, `refreshCatalog(force)`, `getExercise(id)` |
| `TemplateRepository` | `getTemplates(goal?, daysPerWeek?)`, `getTemplate(id)` |
| `SharingRepository` | `findActiveShare`, `createShare`, `getSharedRoutine(code)` |
| `AiImportRepository` | `importFromImage(userId, base64Jpeg)` |
| `ActiveWorkoutRepository` | `observeActive`/`getActive`, `start`, `replace`, `updateSetInputs`, `setCompleted`, `addSet`, `setCurrentExercise`, `setRestTimer`, `discard`, `moveToOutbox` |
| `PendingWorkoutRepository` | `observeCount`, `observeFailed`, `getPending`, `upload`, `delete`, `markFailed`, `resetToPending`, `recordAttempt` |
| `WorkoutHistoryRepository` | `getSessions(page)`, `getSession`, `updateSet`, `addSet`, `deleteSet`, `deleteSession`, `getLastSession`, `getCompletedSince`, `getLastCompletedAt`, `countCompleted` |
| `ProgressRepository` | `getPersonalRecords`, `getLatestPersonalRecord`, `countPersonalRecords`, `getExerciseSets(limit=500)` |
| `ProfileRepository` | `getProfile`, `updatePhysical`, `countActiveRoutines` |

Todos los métodos suspend que cruzan red devuelven `AppResult<T>`; los `Flow` (`observe*`) nunca
lanzan, reflejan el estado del caché local.

## `domain/usecase`

- **`AdoptTemplateUseCase`** (FASE 3): `invoke(userId, template): AppResult<List<String>>`
  crea una `Routine` por día de la plantilla (vía `RoutineRepository`), con **compensación**: si
  falla cualquier paso (incluida la cancelación de la corrutina, manejada con `NonCancellable`),
  borra las rutinas ya creadas antes de propagar el error/cancelación.
- **`StartWorkoutUseCase`** (FASE 4): arma la instantánea del entrenamiento a partir de la rutina y
  un `DaySelection` (`Day(n)`/`Unassigned`/`All`); devuelve `StartResult.Started(sessionId)` o
  `StartResult.ActiveWorkoutExists(existing)`. Con `replaceExisting = true` reemplaza la sesión
  activa usando exactamente el id que reportó `AlreadyActive`. Sin ejercicios →
  `ValidationReason.NO_EXERCISES`.
- **`UpdateSetInputUseCase`**: persiste los textos de peso/reps de una serie (el ViewModel los
  debounce-a).
- **`ToggleSetCompletionUseCase`**: valida peso/reps (coma decimal, peso vacío = 0 solo en
  `BODYWEIGHT`, vía `ActiveSetWeight`) antes de completar; al completar arranca el `RestTimer` y
  agenda la alarma; descompletar no valida.
- **`FinishWorkoutUseCase`**: `invoke(userId, sessionId): AppResult<FinishResult>` (`Saved` /
  `NothingToSave`); convierte a kg solo las series completadas, mueve la sesión al outbox
  (`moveToOutbox`, atómico), cancela la alarma y agenda la sincronización.
- **`DiscardWorkoutUseCase`**: borra la sesión activa y cancela la alarma.
- **`SyncPendingWorkoutsUseCase`**: sube las filas `PENDING` del usuario actual (filas de otro
  usuario no se tocan); `SyncOutcome.Done` / `RetryLater` / `NoUser`. Errores transitorios (red,
  `Unauthorized`, `Server` 5xx o sin código) → `recordAttempt` y `RetryLater`; el resto (p. ej. un
  42501 de RLS, conflicto) → `markFailed` (`FAILED`). `lastError` guarda un código corto sin PII.
  Subir dos veces es seguro (upsert idempotente por `id`).

## `domain/calc` — cálculos puros (100% testeados)

`WorkoutMath` (1RM Epley), `WeightConverter` (kg↔lb), `WeightInputParser` (acepta `,`/`.`),
`WeekRange` (semana lunes-domingo en zona local), `AgeCalculator` (`Period.between`),
`RoutineOrdering` (orden por día, `nextSortOrder`, `suggestedFirstDayNumber`,
`unassignedBucketDayNumber`), `SpanishWeekdays`, `ExerciseProgressAggregator`, `NumberFormatter`,
`Validators`, `ActiveSetWeight` (FASE 4: regla única "peso vacío inválido salvo en `BODYWEIGHT`",
compartida por `ToggleSetCompletionUseCase` y `FinishWorkoutUseCase`).

## `data/remote` y `data/mapper`

- **`Dtos.kt`**: DTOs `@Serializable` (`ExerciseDto`, `RoutineSummaryDto`, `RoutineDetailDto`,
  `WorkoutSessionDto`, `WorkoutSetDto`, `PersonalRecordDto`, `ProfileDto`, `SharedRoutineDto`,
  `RoutineTemplateDto`, DTOs de insert, etc.), con `@SerialName` snake_case — ver
  `MIGRATION_PLAN.md` §6 para el detalle campo a campo.
- **`*RemoteDataSource`** (interfaz) + **`Supabase*RemoteDataSource`** (impl): una por dominio
  (`Auth`, `Exercise`, `Routine`, `Template`, `Workout`, `Progress`, `Profile`, `Sharing`,
  `AiImport`). Las llamadas no pasan por `safeCall` acá — eso ocurre en el repositorio — así los
  fakes de test lanzan excepciones simples.
- **`*Mapper`**: funciones de extensión DTO→dominio / entidad→dominio (y viceversa donde aplica),
  una por familia de modelo.

## `data/repository` — implementaciones

Cada `*RepositoryImpl` implementa su interfaz homónima de `domain/repository` (bindings en
`RepositoryModule`, `@Binds` + `@Singleton`). Notas de comportamiento relevantes:
- **`RoutineRepositoryImpl`** / **`ExerciseRepositoryImpl`**: patrón cache-then-network sobre
  `CachedPayloadDao` (ver `arquitectura.md`).
- **`ActiveWorkoutRepositoryImpl`**: sobre `ActiveWorkoutDao`; en `start()`, si una
  `SQLiteConstraintException` deja la re-consulta en `null`, relanza (no devuelve `Started`
  silenciosamente) — fix de un carry-over de revisión de FASE 2, resuelto en FASE 3.
  `replace(existingSessionId, ...)` verifica en la transacción del DAO que la sesión a reemplazar
  sea del mismo usuario (FASE 4).
- **`PendingWorkoutRepositoryImpl`**: sobre `PendingWorkoutDao`; `upload()` hace upsert idempotente
  contra Supabase.
- El resto (`AuthRepositoryImpl`, `PreferencesRepositoryImpl`, `TemplateRepositoryImpl`,
  `SharingRepositoryImpl`, `AiImportRepositoryImpl`, `WorkoutHistoryRepositoryImpl`,
  `ProgressRepositoryImpl`, `ProfileRepositoryImpl`) van directo contra su `RemoteDataSource` con
  `safeCall`, sin caché local.

## `feature/root`

- **`RootViewModel`**: `uiState: StateFlow<RootUiState>` (`Loading`/`ConfigError`/`SignedOut`/
  `SignedIn(needsOnboarding)`), `pendingImportCode: StateFlow<ShareCode?>`, `onDeepLink(link)`,
  `consumeDeepLink()`, `pendingOpenWorkout: StateFlow<Boolean>` (`onOpenWorkoutRequested()` /
  `consumeOpenWorkout()`, alimentado por el extra `open_workout` de la notificación). Una vez por
  sesión de proceso al pasar a `SignedIn`: sincroniza el `display_name` y llama
  `SyncScheduler.schedule()`.
- **`SpotterRoot`**: composable raíz; decide `LoginScreen`/`ConfigErrorScreen`/`NavHost` según
  `RootUiState`; maneja la navegación al deep link de importación pendiente y a `WorkoutRoute`
  cuando se toca la notificación de descanso.
- **`ConfigErrorScreen`**: pantalla mostrada si `AppConfig.isValid == false`.

## `feature/auth`

- **`LoginViewModel`**/**`LoginScreen`**: credential manager o fallback PKCE, según
  `GOOGLE_WEB_CLIENT_ID`.
- **`OnboardingViewModel`**/**`OnboardingScreen`**: se muestra una vez por usuario
  (`PreferencesRepository.isOnboardingDone`).
- **`GoogleCredentialClient`**, **`NonceGenerator`** (SHA-256 del nonce crudo).

## `feature/profile`

- **`ProfileViewModel`**: `ProfileUiState(displayName, email, signingOut, errorMessageRes)`;
  `onSignOutConfirmed()` cancela la alarma de descanso y el worker de sincronización y luego llama
  `AuthRepository.signOut()` (sin `SignOutUseCase` — no limpia Room/DataStore todavía, FASE 5). **`ProfileScreen`**: identidad +
  botón "Cerrar sesión" con `ConfirmDialog`.

## `feature/routines`

- **`list/RoutinesViewModel`** (`RoutinesUiState`, `RoutinesEvent`) / **`RoutinesScreen`**: lista
  ordenada por día, importar por código, accesos a plantillas/archivadas/IA, archivar con
  confirmación, pull-to-refresh. Desde FASE 4 muestra `ActiveWorkoutBanner` ("Entrenamiento en
  curso · Continuar") si `activeWorkoutRoutineName != null` (provisorio hasta el dashboard real).
- **`list/ArchivedRoutinesViewModel`** (`ArchivedRoutinesUiState`, `ArchivedRoutinesEvent`) /
  **`ArchivedRoutinesScreen`**: restaurar rutinas archivadas.
- **`edit/RoutineEditViewModel`** (`RoutineEditUiState`, `RoutineEditEvent`) /
  **`RoutineEditScreen`**: crear y editar, con `Validators`.
- **`detail/RoutineDetailViewModel`** (`RoutineDayGroup`, `RoutineDetailUiState`,
  `RoutineDetailEvent`) / **`RoutineDetailScreen`**: agrupación por día (incluye "sin día
  asignado"), reordenar con `sh.calvin.reorderable`, mover ejercicio de día, editar/quitar
  ejercicio, agregar/renombrar/borrar día. FASE 4: "Iniciar Entrenamiento" con selector de día,
  diálogo "Continuar / Descartar y empezar / Cancelar" ante `WorkoutAlreadyActive`, pedido de
  `POST_NOTIFICATIONS` en API 33+, y `onOpenWorkout` (sin guard `dropUnlessResumed`: lo dispara el
  evento `WorkoutStarted`).
- **`addexercise/AddExerciseViewModel`** (`AddExerciseItem`, `ConfiguringExercise`,
  `AddExerciseUiState`, `AddExerciseEvent`) / **`AddExerciseScreen`**: catálogo con búsqueda y
  filtro por grupo muscular, `NumberStepper` de sets/reps/descanso.

## `feature/templates`

- **`list/TemplatesViewModel`** (`TemplatesUiState`) / **`TemplatesScreen`**: filtros por objetivo
  y días por semana.
- **`detail/TemplateDetailViewModel`** (`TemplateDetailUiState`, `TemplateDetailEvent`) /
  **`TemplateDetailScreen`**: días expandibles, adopción vía `AdoptTemplateUseCase` con el botón
  bloqueado mientras corre.
- **`TemplateLabels`**: mapeo de enums a texto en español.

## `feature/exercise`

- **`ExerciseDetailViewModel`** (`ExerciseDetailUiState`) / **`ExerciseDetailScreen`**: usa
  `core/designsystem/component/ExerciseMedia` para video (ExoPlayer, `.mp4`/`.webm`/`.m3u8`), GIF
  animado o imagen estática (Coil), con placeholder si no hay media.
- **`ExerciseLabels`**: mapeo de `Equipment`/`Difficulty`/`ExerciseCategory` a español.

## `feature/workout` (FASE 4)

- **`WorkoutViewModel`** (`WorkoutUiState`, `InputDraft`, `LastSessionUiState`, `WorkoutEvent`):
  observa `ActiveWorkoutRepository.observeActive(userId)` (Room es la fuente de verdad, así que el
  estado sobrevive a la muerte del proceso); borradores de texto por serie superpuestos al estado
  de Room, persistidos con `debounce(300)` y flush inmediato al completar/cambiar de ejercicio y en
  `onCleared`; ticker de 1 s para sesión y descanso. Acciones: `onWeightChange`, `onRepsChange`,
  `onToggleSet`, `onAddSet`, `onSelectExercise`, `onSkipRest`, `onFinish`, `onDiscard`,
  `onShowLastSession`/`onDismissLastSession`. Eventos: `RestFinished` (beep `R.raw.beep` +
  háptico), `ActionFailed`, `Finished(online)`, `Discarded`, `NoActiveWorkout`.
- **`WorkoutScreen`**: pantalla completa sin barra inferior; header con timer y "Finalizar",
  puntos de ejercicios, `ExerciseMedia`, tarjeta de descanso con "Saltar", tabla de series
  (KG/LB, reps, check), "Agregar serie", "Último entrenamiento" en hoja inferior, banner
  "Sin conexión — los entrenamientos se guardan localmente", `BackHandler` que confirma el
  descarte.

## `feature/common`

- **`ObserveAsEvents(flow, onEvent)`**: colector de eventos one-shot lifecycle-aware
  (`repeatOnLifecycle(STARTED)`), usado por las 6 pantallas de FASE 3 en vez de un
  `LaunchedEffect(Unit)` plano (ver `arquitectura.md`).
- **`AppError.toMessageRes()`/`toLoginMessageRes()`**, **`ValidationReason.toMessageRes()`**:
  mapeos a strings localizados.
- **`routineExerciseSummary(sets, reps, restSeconds)`**: "N series × N reps · Ns descanso" con
  plurales reales.
- **`ComingSoonScreen`**: placeholder para Dashboard/Historial/Progreso/Entrenamiento/Import.

## `core/notifications` y `core/work` (FASE 4)

- **`NotificationChannels`**: canal `rest_timer` (importancia HIGH).
- **`RestTimerAlarmScheduler`** / **`AndroidRestTimerAlarmScheduler`**: `schedule(endsAt)` con
  `setExactAndAllowWhileIdle` si `canScheduleExactAlarms()`, si no `setAndAllowWhileIdle`;
  `cancel()`. `PendingIntent` con `FLAG_IMMUTABLE`.
- **`RestTimerReceiver`** (no exportado): si la app está en primer plano no hace nada; si no, y
  hay permiso, notifica "Descanso terminado" con un intent a `MainActivity` con
  `EXTRA_OPEN_WORKOUT`.
- **`SyncWorkoutsWorker`** (`@HiltWorker`, `CoroutineWorker`): corre
  `SyncPendingWorkoutsUseCase`; `Done`/`NoUser` → `success()`, `RetryLater` → `retry()`.
- **`SyncScheduler`** / **`WorkManagerSyncScheduler`**: `schedule()` encola trabajo único
  `sync_workouts` (`APPEND_OR_REPLACE`, red requerida, backoff exponencial 30 s); `cancel()`.
- Bindings en `NotificationsModule` y `WorkModule`.

## `core/designsystem/component`

`SpotterButton` (variantes `Primary`/`Secondary`/`Ghost`, tamaños `Small`/`Medium`/`Large`),
`SpotterCard`, `SpotterTextField`, `SpotterChip`, `NumberStepper`, `SectionHeader`, `ConfirmDialog`,
`LoadingState`, `ErrorState`, `EmptyState`, `ExerciseMedia` (+ función `isVideoUrl`).

## Planificado (no implementado — nombres del plan, fases 5-7)

`GetDashboardStatsUseCase`, `GetExerciseProgressUseCase`, `GetProfileOverviewUseCase`,
`UpdateProfileUseCase`, `SignOutUseCase` (FASE 5); `ShareRoutineUseCase`,
`ImportSharedRoutineUseCase`, `ImportRoutineFromImageUseCase`, `BuildWorkoutExportUseCase`
(FASE 6); `DashboardScreen`/`DashboardViewModel`, `HistoryListScreen`/`SessionDetailScreen`,
`ProgressScreen` (con gráfico propio en `Canvas`), `StatCard`, `LineChart` (FASE 5); pantallas de
importar por código/imagen y de exportar/compartir (FASE 6).
