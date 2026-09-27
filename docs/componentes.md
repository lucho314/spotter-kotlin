# Componentes principales

Clases e interfaces reales del código tras FASES 1-7, agrupadas por capa. Cada identificador
listado aquí existe en `app/src/main/java/com/lucho314/spotter/` (verificado tras FASES 1-4 en
máquina, FASES 5-7 código escrito no compilado; 473 tests de dominio, ViewModels y
datos verificados en un arnés JVM).

## `core/common` — tipos y utilidades compartidas

- **`AppError`** (`sealed interface`): `Network`, `Unauthorized`, `NotFound`, `Conflict(detail)`,
  `Validation(reason: ValidationReason)`, `Server(code, message)`, `Unknown(cause)`. Nunca se
  construye a partir de `Throwable.message` de una excepción de red (ver `SafeCall.kt`).
- **`AppResult<T>`** (`sealed interface`): `Success<T>(value)` / `Failure(error: AppError)`.
  Extensiones: `map`, `onSuccess`, `onFailure`, `getOrNull`, `notNullOrNotFound()`,
  `requirePositiveOrNotFound()` (colapsa un conteo de filas afectadas en `NotFound` cuando es 0 —
  importante bajo RLS, donde un update/delete sin permiso devuelve 0 filas con status 2xx).
- **`ValidationReason`** (`enum`): 22 razones (`NAME_EMPTY`, `SETS_RANGE`, `DAYS_MAX_REACHED`,
  `WORKOUT_REPS_RANGE`, `AGE_RANGE`, `SHARED_ROUTINE_INVALID` (FASE 6), etc.), mapeadas a mensajes
  en `feature/common/ValidationMessages.kt`.
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

## `core/designsystem/component` — nuevos componentes (FASE 5)

- **`StatCard`**: `@Composable fun StatCard(label, value, modifier, supportingText?, valueColor)`.
  Tarjeta con etiqueta y valor destacado; utilizado en Dashboard (sesiones, última sesión),
  Progreso (PRs) y Perfil (estadísticas).
- **`LineChartGeometry`** (puro): `xPositions`, `yPosition`, `labelIndices` — cálculos de
  posicionamiento para gráficos en Canvas sin dependencias de Compose.
- **`LineChart`**: `@Composable fun LineChart(points: List<ChartPoint>, modifier, lineColor?, maxLabels?)`.
  Gráfico de línea en `Canvas` con ejes, línea, puntos y etiquetas "d/M" para los últimos N puntos.
  Utilizado en `ProgressScreen`.

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
`SharedRoutinePreview`/`SharedRoutineContent`/`SharedRoutineDay`/`SharedRoutineExercise`/`SanitizedSharedRoutine` (FASE 6),
`AuthUser`/`AuthState`, `ShareCode` (value class con `parse`/`generate`), `WeightUnit` (`KG`/`LB`),
`DashboardStats`, `AiImportedRoutine`/`AiImportErrorCodes` (FASE 6), `ExportFormat`/`WorkoutExportData`/
`ExportExercise`/`ExportSetRow`/`ExportTopSet`/`ExportedFile` (FASE 6).

## `domain/repository` — 14 interfaces (todas con implementación en `data/repository`)

| Interfaz | Métodos clave |
|---|---|
| `AuthRepository` | `authState: Flow<AuthState>`, `currentUser()`, `signInWithGoogleIdToken`, `startGoogleOAuth`, `syncProfileDisplayName`, `signOut` |
| `PreferencesRepository` | `weightUnit: Flow<WeightUnit>`, `setWeightUnit`, `isOnboardingDone(userId)`, `setOnboardingDone`, `clearUserScoped` |
| `RoutineRepository` | `observeRoutines`/`refreshRoutines`, `observeArchivedRoutines`/`refreshArchived`, `observeRoutine`/`refreshRoutine`, `createRoutine`, `updateRoutine`, `setArchived`, `deleteRoutine`, `addExercise(s)`, `updateRoutineExercise`, `removeExercise`, `reorderExercises`, `addDays`, `renameDay`, `deleteDay` |
| `ExerciseRepository` | `observeCatalog`, `observeMuscleGroups`, `refreshCatalog(force)`, `getExercise(id)` |
| `TemplateRepository` | `getTemplates(goal?, daysPerWeek?)`, `getTemplate(id)` |
| `SharingRepository` | `findActiveShare`, `createShare`, `getSharedRoutine(code)` |
| `AiImportRepository` | `importFromImage(userId, base64Jpeg): AppResult<AiImportedRoutine>` |
| `ActiveWorkoutRepository` | `observeActive`/`getActive`, `start`, `replace`, `updateSetInputs`, `setCompleted`, `addSet`, `setCurrentExercise`, `setRestTimer`, `discard`, `moveToOutbox` |
| `PendingWorkoutRepository` | `observeCount`, `observeFailed`, `getPending`, `upload`, `delete`, `markFailed`, `resetToPending`, `recordAttempt` |
| `WorkoutHistoryRepository` | `getSessions(page)`, `getSession`, `updateSet`, `addSet`, `deleteSet`, `deleteSession`, `getLastSession`, `getCompletedSince`, `getLastCompletedAt`, `countCompleted` |
| `ProgressRepository` | `getPersonalRecords`, `getLatestPersonalRecord`, `countPersonalRecords`, `getExerciseSets(limit=500)` |
| `ProfileRepository` | `getProfile`, `updatePhysical`, `countActiveRoutines` |
| `LocalDataRepository` | `clearAll()` — borra todas las tablas Room (active/pending/cached) de forma atómica (FASE 5) |
| `ImageRepository` | `createCameraCaptureUri(): AppResult<String>`, `clearCameraCaptures()`, `encodeForAiImport(uri): AppResult<String>` (FASE 6) |
| `WorkoutExportRepository` | `export(data, format): AppResult<ExportedFile>` (FASE 6) |

Todos los métodos suspend que cruzan red devuelven `AppResult<T>`; los `Flow` (`observe*`) nunca
lanzan, reflejan el estado del caché local.

## `domain/usecase`

- **`AdoptTemplateUseCase`** (FASE 3): `invoke(userId, template): AppResult<List<String>>`
  crea una `Routine` por día de la plantilla (vía `RoutineRepository`), con **compensación**: si
  falla cualquier paso (incluida la cancelación de la corrutina, manejada con `NonCancellable`),
  borra las rutinas ya creadas antes de propagar el error/cancelación. KDoc nota riesgo inherente de
  cancelación durante `createRoutine` en vuelo (mitigado por `BackHandler`).
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
- **`GetDashboardStatsUseCase`** (FASE 5): `invoke(userId): DashboardStats` — sesiones de esta
  semana, última sesión, último PR; cada sección es un `AppResult` independiente (fallo parcial no
  bloquea al resto).
- **`GetExerciseProgressUseCase`** (FASE 5): `invoke(userId, exerciseId): AppResult<List<ExerciseProgressPoint>>` —
  puntos de progreso (1RM por sesión) agregados del histórico, máximo 12 sesiones recientes.
- **`GetProfileOverviewUseCase`** (FASE 5): `invoke(userId): AppResult<ProfileOverview>` — perfil +
  estadísticas (entrenamientos, PRs, rutinas activas); perfil falla → falla todo; si falla solo
  algún conteo → `stats = null` y `statsError` seteado.
- **`UpdateProfileUseCase`** (FASE 5): `invoke(userId, current, edit): AppResult<Profile>` — valida
  y persiste datos físicos (peso kg/lb con conversión, altura, fecha nacimiento con edad en
  10..100). Campo vacío borra el dato. Devuelve el `Profile` actualizado.
- **`SignOutUseCase`** (FASE 5): `invoke(): AppResult<Unit>` — cancela alarma y worker, borra toda
  Room (`LocalDataRepository.clearAll()`), llama `signOut()`, borra preferencias por usuario
  (conserva unidad kg/lb). Si falla el borrado, no cierra sesión y reprograma sync. Además,
  `risk(userId): SignOutRisk` calcula entrenamientos sin sincronizar e indica si hay uno activo.
- **`ShareRoutineUseCase`** (FASE 6): `invoke(userId, routine): AppResult<ShareCode>` — reutiliza
  un share activo no vencido del usuario; si no hay, genera uno con `ShareCode.generate(SecureRandom)`,
  hasta 3 intentos ante `Conflict`. Nunca comparte una rutina ajena (defensa B2). Valida propiedad.
- **`ImportSharedRoutineUseCase`** (FASE 6): `preview(code): AppResult<SharedRoutinePreview>` y
  `importRoutine(code, userId): AppResult<String>`. Re-valida el código (descarta vencidos),
  saneamiento completo (deduplicación, límite 100 ejercicios), compensación ante fallo/cancelación.
  KDoc nota riesgo inherente de cancelación durante `createRoutine` en vuelo.
- **`ImportRoutineFromImageUseCase`** (FASE 6): `invoke(userId, base64Jpeg): AppResult<AiImportedRoutine>` —
  validación de tamaño sin llamadas, re-codificación, validación de UUID y propiedad del usuario,
  refresco de rutinas en fallos ambiguos (puede haber creado igual).
- **`BuildWorkoutExportUseCase`** (FASE 6): `invoke(detail, unit): WorkoutExportData` — constructor
  puro que delega en `WorkoutExportDataBuilder` con zona horaria del `TimeProvider`.

## `domain/calc` — cálculos puros (100% testeados)

`WorkoutMath` (1RM Epley), `WeightConverter` (kg↔lb, `formatOneDecimal`), `WeightInputParser`
(acepta `,`/`.`), `WeekRange` (semana lunes-domingo en zona local), `AgeCalculator` (`Period.between`),
`RoutineOrdering` (orden por día, `nextSortOrder`, `suggestedFirstDayNumber`,
`unassignedBucketDayNumber`), `SpanishWeekdays`, `ExerciseProgressAggregator`, `NumberFormatter`
(`formatVolume` con unidad), `SetInputValidator` (FASE 5: valida peso + reps para series con
conversión de unidades), `Validators`, `ActiveSetWeight` (FASE 4: regla única "peso vacío
inválido salvo en `BODYWEIGHT`", compartida por `ToggleSetCompletionUseCase` y
`FinishWorkoutUseCase`), `TextSanitizer` (FASE 6: normalización de caracteres, bidi, límites),
`SharedRoutineSanitizer` (FASE 6: sanitización con deduplicación), `ExerciseSetGrouping`
(FASE 6: agrupación única), `WorkoutExportDataBuilder` (FASE 6: construcción de datos exportables).

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
- **`LocalDataRepositoryImpl`** (FASE 5): sobre `SpotterDatabase` con transacción atómica;
  `clearAll()` borra las tres familias de tablas en orden (sets antes que padre, para respetar
  foreign keys).
- El resto (`AuthRepositoryImpl`, `PreferencesRepositoryImpl`, `ProfileRepositoryImpl` (FASE 5),
  `ProgressRepositoryImpl`, `TemplateRepositoryImpl`, `SharingRepositoryImpl`, `AiImportRepositoryImpl`,
  `WorkoutHistoryRepositoryImpl`) van directo contra su `RemoteDataSource` con `safeCall`, sin
  caché local.

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

## `feature/profile` (reescrita en FASE 5)

- **`ProfileViewModel`** (FASE 5): `ProfileUiState` con perfil, edad calculada, estadísticas,
  campos editables (peso, altura, fecha de nacimiento, objetivo), unidad kg/lb, indicador de
  guardado y estado de cierre de sesión; eventos one-shot `Message` (success/error). Métodos:
  `onEdit(field)`, `onSaveWeight/Height/BirthDate/Goal`, `onWeightUnitChange`, `onSignOutClick`,
  `onSignOutConfirmed()` (invoca `SignOutUseCase`), `onSignOutDismiss()`, `retry()`.
- **`ProfileScreen`** (FASE 5): avatar (`AsyncImage` o inicial), nombre/email, "ESTADÍSTICAS"
  (entrenamientos/PRs/rutinas), "DATOS FÍSICOS" (peso en unidad preferida, altura, edad con
  autoformato de fecha, objetivo), "CONFIGURACIÓN" (unidad con `SegmentedButton` kg/lb),
  "Cerrar sesión" con `ConfirmDialog` que muestra advertencia si hay entrenamientos sin sincronizar
  o en curso.
- **`BirthDateInput`** (FASE 5, puro): autoformato de entrada "DD/MM/AAAA" a medida que escribe.
- **`ProfileGoalLabels`** (FASE 5): mapeo de `ProfileGoal` a strings localizados.

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
  evento `WorkoutStarted`). FASE 6: icono Share → `onShareClick()`, invoca `ShareRoutineUseCase`,
  copia código, abre ACTION_SEND, muestra snackbar "Código copiado: X". Relay de mensaje
  "Rutina importada" vía `savedStateHandle`.
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
- **`AppError.toMessageRes()`/`toLoginMessageRes()`/`toUserMessageRes()` (FASE 5)**,
  **`ValidationReason.toMessageRes()`**: mapeos a strings localizados.
- **`routineExerciseSummary(sets, reps, restSeconds)`**: "N series × N reps · Ns descanso" con
  plurales reales.
- **`ShareIntents`** (FASE 6): `launchShareText(text, title): Boolean`, `launchShareFile(uri, mime, title): Boolean`,
  `copyPlainTextToClipboard(label, text)` — helpers Android para ACTION_SEND y portapapeles.
- **`SectionState<T>`** (FASE 5, sealed interface): `Loading`, `Loaded<T>`, `Error(@StringRes)` —
  estado genérico de sección que puede estar cargando, cargada o con error.
- **`DateFormats`** (FASE 5, puro): `longDay(instant, zone)` → "lunes 3 de marzo",
  `shortDayMonth(instant, zone)` → "3/3", `birthDate(localDate)` → "DD/MM/AAAA".
- **`ActiveWorkoutBanner`** (FASE 5, compartida): composable que muestra "Entrenamiento en curso ·
  Continuar" si `activeWorkoutRoutineName != null`; usada en Dashboard y Rutinas.

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

## `feature/dashboard` (FASE 5)

- **`DashboardViewModel`** (`DashboardUiState`, `DashboardEvent`): saludo según la hora, sesiones
  de esta semana, última sesión ("Hoy"/"Ayer"/"Hace N días"/"-"), último PR, top 3 rutinas
  ordenadas por día de la semana, estadísticas de sincronización pendiente. Acciones:
  `refresh()`, `retry()`, `refreshOnResume()` con throttle, `onStartWorkoutClick`,
  `onSeeAllRoutinesClick`, `onPendingClick`, `onCreateRoutineClick`, `onRoutineClick`,
  `onResumeWorkoutClick`, `onFinishedWorkoutConsumed()`.
- **`DashboardScreen`** (FASE 5): saludo + nombre, tarjetas de estadísticas, banner de
  entrenamiento en curso o botón "Iniciar Entrenamiento", último PR, "Mis Rutinas" con top 3,
  chip de pendientes (condicional), pull-to-refresh. Recibe relay flag `workoutFinishedOnline`
  vía `savedStateHandle` y muestra snackbar correspondiente.
- **`DashboardFormatters`** (FASE 5, puro): `Greeting` enum, `greetingFor(now, zone)`,
  `LastSessionLabel` (sealed interface), `lastSessionLabel(last, now, zone)`.

## `feature/history` (FASE 5-6)

- **`list/HistoryViewModel`** (`HistoryUiState`, `HistoryEvent`): lista de sesiones paginadas
  (offset/limit, 30 por página), banner de pendientes y de fallidos (con "Reintentar" y "Descartar"),
  borrado con confirmación. Acciones: `refresh()`, `retry()`, `loadMore()`, `onDeleteSession(id)`,
  `onRetryFailed(id)`, `onDiscardFailed(id)`.
- **`list/HistoryScreen`** (FASE 5): `PullToRefreshBox` + `LazyColumn` de `SessionCard`,
  banner de pendientes/fallidos, "Cargar más" con loading si hay más páginas.
- **`detail/SessionDetailViewModel`** (`ExerciseBlock`, `EditingSet`, `SessionDetailUiState`,
  `SessionDetailEvent`): detalle de sesión con bloques por ejercicio, edición por fila con
  validación, agregar serie, borrado con confirmación. FASE 6: `onExport(format)` invoca
  `BuildWorkoutExportUseCase` + `WorkoutExportRepository`, estado `exporting`. Acciones:
  `onEditSet(id)`, `onEditDismiss()`, `onEditConfirm(weight, reps)`, `onDeleteSet(id)`,
  `onAddSet(exerciseId)`, `onExport(format)`, `retry()`. Eventos: `ShareFile(uri, mimeType)`.
- **`detail/SessionDetailScreen`** (FASE 5-6): `TopAppBar` con icono Share (FASE 6), hero
  (duración/volumen/series), bloques con filas de series (editable, borrable), diálogo de edición.
  FASE 6: `showShareSheet` + `ShareWorkoutSheet`, evento `ShareFile` → `launchShareFile`.
- **`share/ShareWorkoutSheet`** (FASE 6, stateless): `ModalBottomSheet` con opciones "PDF Detallado"
  e "Historia", cada una con `CircularProgressIndicator` si `exporting == format`.

## `feature/progress` (FASE 5)

- **`ProgressViewModel`** (`ProgressChartPoint`, `ProgressChartState`, `ProgressUiState`):
  carga records personales, selección de ejercicio para gráfico. Acciones: `refresh()`, `retry()`,
  `onExerciseChipClick(exerciseId)`.
- **`ProgressScreen`** (FASE 5): `PullToRefreshBox`, tarjetas de records personales (PR, 1RM con
  trofeo), chips de ejercicios (click para seleccionar/deseleccionar), `LineChart` de 1RM por
  sesión con etiquetas "d/M".

## `feature/importroutine` (FASE 6)

- **`code/ImportCodeViewModel`** (`ImportCodeStatus`, `ImportCodeUiState`, `ImportCodeEvent`):
  re-valida código (sin red si inválido), carga preview con `ImportSharedRoutineUseCase`, importa
  con compensación. Estados: `Loading`, `Unavailable(titleRes)`, `LoadError(messageRes)`,
  `Ready(preview)`. Eventos: `Imported(routineId)`, `ActionFailed(messageRes)`. Acciones:
  `retry()`, `onImportClick()`.
- **`code/ImportCodeScreen`**: `TopAppBar`, `BackHandler`, estados `Loading`/`Unavailable`/
  `LoadError`/`Ready` con preview (nombre, ejercicios, días), botones "Importar rutina" y
  "Cancelar", evento `Imported` → callback `onImported(routineId)`.
- **`image/ImportImageViewModel`** (`ImportImageUiState`, `ImportImageEvent`): cámara (FileProvider)
  y galería (PickVisualMedia), preview, compresión vía `ImageRepository`, IA vía
  `ImportRoutineFromImageUseCase`. Estados: `imageUri`, `importing`. Eventos: `LaunchCamera(uri)`,
  `Imported(routineId, routineName)`, `ShowError(kind)`. Acciones: `onCameraClick()`,
  `onCameraResult(success)`, `onGalleryResult(uri)`, `onClearImage()`, `onImportClick()`, override
  `onCleared()` limpia cámara en `NonCancellable`.
- **`image/ImportImageScreen`**: `TopAppBar`, `BackHandler`, launchers para `TakePicture` y
  `PickVisualMedia`, descripción, dos botones sin imagen o uno + preview + "Cambiar" con imagen,
  ` CircularProgressIndicator` mientras importa. Evento `LaunchCamera` → `cameraLauncher.launch`
  (con try/catch `ActivityNotFoundException`). Evento `ShowError` → snackbar (con "Ver rutinas" si
  `kind.suggestsCheckingRoutines`). Evento `Imported` → callback `onRoutineCreated(id, name)`.
- **`image/AiImportErrorKind`** (puro, sin R): `OFFLINE`, `IMAGE_TOO_LARGE`, `IMAGE_UNREADABLE`,
  `TIMEOUT_MAYBE_CREATED`, `CONNECTION_LOST_MAYBE_CREATED`, `NOT_RECOGNIZED`, `SESSION_EXPIRED`,
  `GENERIC`. Extensión `AppError.toAiImportErrorKind()`, propiedad `suggestsCheckingRoutines`,
  método local `messageRes()` → `@StringRes`.

## `core/designsystem/component`

`SpotterButton` (variantes `Primary`/`Secondary`/`Ghost`, tamaños `Small`/`Medium`/`Large`),
`SpotterCard`, `SpotterTextField`, `SpotterChip`, `NumberStepper`, `SectionHeader`, `ConfirmDialog`,
`LoadingState`, `ErrorState`, `EmptyState`, `ExerciseMedia` (+ función `isVideoUrl`), `StatCard`,
`LineChart`, `LineChartGeometry` (FASE 5).

## Datos (FASE 6)

- **`data/export/ExportPalette`**: constantes ARGB que replican `SpotterColors` (sin Compose).
- **`data/export/PageCursor`** (puro): rastreador de página/posición Y para paginación de PDF.
- **`data/export/ExportFileNames`** (puro): nombres con timestamp UTC, limpieza de archivos vencidos.
- **`data/export/ExportFileWriter`**: escribe en `cacheDir/exports/`, purga, devuelve Uri de
  `FileProvider`.
- **`data/export/WorkoutPdfRenderer`**: `PdfDocument` A4, header con "SPOTTER" y fecha, hero,
  tabla de series paginada con encabezado repetido "(cont.)", pie con página, fuentes desde
  `ResourcesCompat.getFont`, sin dependency Compose.
- **`data/export/WorkoutStoryRenderer`**: `Bitmap` 1080×1920 ARGB, `Canvas` con `LinearGradient`,
  "SPOTTER", fecha, nombre, 3 tiles (duración/volumen/series), 4 ejercicios + "+N más", pie,
  `compress(JPEG, 92)`.
- **`data/export/WorkoutExportRepositoryImpl`**: orquesta con `ExportFileWriter`, selecciona
  renderer, maneja errores.
- **`data/image/ImageSizing`** (puro): `inSampleSize`, `scaledSize`, `base64Length`, `exifTransform`.
- **`data/image/ImageRepositoryImpl`**: `createCameraCaptureUri` (FileProvider temporal, purga 1h),
  `clearCameraCaptures` (best effort), `encodeForAiImport` (decodificación, EXIF, escalado,
  compresión iterativa). Try/catch propio sin `safeCall`.
- **`data/repository/AiImportErrorMapper`** (puro object): mapea excepciones a `AiImportErrorCodes`,
  nunca propaga `message`.
- **`AiImportRepositoryImpl`** (actualizado): usa `AiImportErrorMapper`, nunca loguea `error` del
  servidor, loguea solo clase y status.
- **`SharingRepositoryImpl`** (actualizado): consulta mínima con `SharedRoutineImportDto`,
  valida `shareCode` y `isActive` en cliente.
- **`SharingRemoteDataSource`/`SupabaseSharingRemoteDataSource`** (actualizados): nueva consulta de
  share liviana.
- **`SupabaseAiImportRemoteDataSource`** (actualizado): `socketTimeoutMillis = REQUEST_TIMEOUT_MS`
  + KDoc explícito del bug de OkHttp 10 s.
- **`Dtos.kt`** (actualizado): DTOs nuevos `SharedRoutineImportDto`, `SharedRoutineBodyDto`,
  `SharedRoutineDayDto`, `SharedRoutineExerciseDto`.

