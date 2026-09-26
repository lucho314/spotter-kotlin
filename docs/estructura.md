# Estructura del proyecto

Árbol verificado contra `app/src/main/java/com/lucho314/spotter/` y `app/src/test/java/com/lucho314/spotter/`
tras FASES 1-4. Un solo módulo Gradle `:app`. Los paquetes/archivos marcados **(planificado)** no
existen todavía — ver `docs/arquitectura.md` sección "Planificado".

## Raíz

```
MainActivity.kt      @AndroidEntryPoint; splash screen; procesa deep links (auth/import) y el extra
                      open_workout de la notificación de descanso en onCreate/onNewIntent
SpotterApp.kt         @HiltAndroidApp; Configuration.Provider (HiltWorkerFactory para SyncWorkoutsWorker);
                      SingletonImageLoader.Factory (Coil, decoder de GIF animado)
```

## `core/` — infraestructura compartida

| Paquete | Archivos | Responsabilidad |
|---|---|---|
| `core/common/` | `AppResult.kt`, `AppError.kt`, `ValidationReason.kt`, `Dispatchers.kt` (`@IoDispatcher`/`@DefaultDispatcher`), `TimeProvider.kt`, `IdGenerator.kt`, `Logger.kt`, `CommonModule.kt` | Errores tipados, utilidades inyectables (reloj, generador de ids, logger) para que los tests sean deterministas |
| `core/config/` | `AppConfig.kt`, `ConfigModule.kt` | Valida `SUPABASE_URL`/`SUPABASE_ANON_KEY` en runtime; si es inválida, se evita construir el `SupabaseClient` |
| `core/database/` | `SpotterDatabase.kt`, `DatabaseModule.kt`, `dao/ActiveWorkoutDao.kt`, `dao/CachedPayloadDao.kt`, `dao/PendingWorkoutDao.kt`, `entity/ActiveWorkoutEntities.kt`, `entity/CachedPayloadEntity.kt`, `entity/PendingWorkoutEntities.kt` | Room v1: caché de lectura, entrenamiento activo, outbox de sincronización |
| `core/datastore/` | `DataStoreModule.kt` | Dos `DataStore<Preferences>`: `secure_auth` (sesión y PKCE cifrados) y `user_prefs` |
| `core/designsystem/component/` | `Chip.kt` (`SpotterChip`), `ConfirmDialog.kt`, `EmptyState.kt`, `ErrorState.kt`, `ExerciseMedia.kt` (`isVideoUrl`, `ExerciseMedia`), `LoadingState.kt`, `NumberStepper.kt`, `SectionHeader.kt`, `SpotterButton.kt`, `SpotterCard.kt`, `SpotterTextField.kt` | Componentes Compose reutilizables |
| `core/designsystem/theme/` | `Color.kt`, `Shape.kt`, `Spacing.kt`, `Theme.kt`, `Type.kt` | Tema, tipografía, espaciado |
| `core/navigation/` | `DeepLink.kt` (`DeepLink`, `DeepLinkParser`), `RouteArgs.kt`, `Routes.kt`, `SpotterNavHost.kt`, `TopLevelDestination.kt` | Rutas type-safe, `NavHost` único, parsing de deep links |
| `core/network/` | `NetworkModule.kt`, `NetworkMonitor.kt`, `SafeCall.kt` (`safeCall`, `ErrorMapper`), `SupabaseModule.kt` | Cliente Supabase (Auth/Postgrest/Functions), mapeo de excepciones a `AppError`, monitor de conectividad |
| `core/security/` | `AeadProvider.kt` (`TinkAeadProvider`), `EncryptedCodeVerifierCache.kt`, `EncryptedSessionManager.kt`, `KeysetRecoveryPolicy.kt`, `SecurityModule.kt` | Cifrado de sesión/PKCE con Tink + Android Keystore |
| `core/notifications/` | `NotificationChannels.kt`, `NotificationsModule.kt`, `RestTimerAlarmScheduler.kt` (`AndroidRestTimerAlarmScheduler`), `RestTimerReceiver.kt` (`EXTRA_OPEN_WORKOUT`) | Canal `rest_timer` y alarma del temporizador de descanso (FASE 4) |
| `core/work/` | `SyncScheduler.kt` (`WorkManagerSyncScheduler`), `SyncWorkoutsWorker.kt`, `WorkModule.kt` | Sincronización del outbox con WorkManager (FASE 4) |

## `domain/` — lógica de negocio pura (sin Android, sin Hilt salvo `@Inject` en use cases)

| Paquete | Archivos | Contenido |
|---|---|---|
| `domain/model/` | `ActiveWorkoutModels.kt`, `AuthModels.kt`, `DashboardModels.kt` (`DashboardStats`, sin consumidores aún), `ExerciseModels.kt`, `PendingWorkoutModels.kt`, `ProfileModels.kt`, `ProgressModels.kt`, `RoutineModels.kt`, `ShareCode.kt`, `SharingModels.kt`, `TemplateModels.kt`, `WeightUnit.kt`, `WorkoutModels.kt` | Data classes / enums / sealed interfaces del dominio |
| `domain/repository/` | 12 interfaces (ver `componentes.md`) | Contratos que implementa `data/repository` |
| `domain/usecase/` | `AdoptTemplateUseCase.kt` (FASE 3); `StartWorkoutUseCase.kt`, `UpdateSetInputUseCase.kt`, `ToggleSetCompletionUseCase.kt`, `FinishWorkoutUseCase.kt`, `DiscardWorkoutUseCase.kt`, `SyncPendingWorkoutsUseCase.kt` (FASE 4) | Los use cases de fases 5-6 se difieren a las fases que los necesitan |
| `domain/calc/` | `ActiveSetWeight.kt`, `AgeCalculator.kt`, `ExerciseProgressAggregator.kt`, `NumberFormatter.kt`, `RoutineOrdering.kt`, `SpanishWeekdays.kt`, `Validators.kt`, `WeekRange.kt`, `WeightConverter.kt`, `WeightInputParser.kt`, `WorkoutMath.kt` | Cálculos puros, 100% cubiertos por tests unitarios |

## `data/` — implementación de datos

| Paquete | Archivos | Contenido |
|---|---|---|
| `data/remote/dto/` | `Dtos.kt` | DTOs `@Serializable` con `@SerialName` snake_case, espejo del esquema Supabase |
| `data/remote/datasource/` | Interfaz + `Supabase*RemoteDataSource` para: `AiImportRemoteDataSource`, `AuthDataSource`, `ExerciseRemoteDataSource`, `ProfileRemoteDataSource`, `ProgressRemoteDataSource`, `RoutineRemoteDataSource`, `SharingRemoteDataSource`, `TemplateRemoteDataSource`, `WorkoutRemoteDataSource`; más `DataSourceModule.kt` (`@Binds`) | Acceso a Supabase (Postgrest/Auth/Functions) detrás de interfaces, para poder testear los repos con fakes |
| `data/mapper/` | `ActiveWorkoutEntityMapper.kt`, `AuthStateMapper.kt`, `AuthUserMapper.kt`, `DateMappers.kt`, `ExerciseMapper.kt`, `PendingWorkoutMapper.kt`, `ProfileMapper.kt`, `ProgressMapper.kt`, `RoutineMapper.kt`, `SharingMapper.kt`, `TemplateMapper.kt`, `WorkoutMapper.kt` | DTO ↔ dominio y entidad Room ↔ dominio |
| `data/repository/` | `ActiveWorkoutRepositoryImpl.kt`, `AiImportRepositoryImpl.kt`, `AuthRepositoryImpl.kt`, `ExerciseRepositoryImpl.kt`, `PendingWorkoutRepositoryImpl.kt`, `PreferencesRepositoryImpl.kt`, `ProfileRepositoryImpl.kt`, `ProgressRepositoryImpl.kt`, `RepositoryModule.kt` (`@Binds`), `RoutineRepositoryImpl.kt`, `SharingRepositoryImpl.kt`, `TemplateRepositoryImpl.kt`, `WorkoutHistoryRepositoryImpl.kt` | Las 12 implementaciones de `domain/repository` |
| `data/export/` **(planificado)** | — | Generación de PDF/JPEG de entrenamientos (FASE 6) |
| `data/image/` **(planificado)** | — | Compresión/EXIF/base64 para la importación con IA (FASE 6) |

## `feature/` — pantallas (Compose + ViewModel)

```
feature/
├── auth/                    LoginScreen/ViewModel, OnboardingScreen/ViewModel, GoogleCredentialClient, NonceGenerator
├── root/                    SpotterRoot, RootViewModel (guardia de sesión + deep link pendiente), ConfigErrorScreen
├── common/                  ObserveAsEvents, ErrorMessages, ValidationMessages, RoutineExerciseSummary, ComingSoonScreen (placeholder)
├── profile/                 ProfileScreen/ViewModel — identidad + cerrar sesión (cancela alarma y sync; datos físicos: fase 5)
├── exercise/                ExerciseDetailScreen/ViewModel, ExerciseLabels
├── routines/
│   ├── list/                RoutinesScreen/ViewModel, ArchivedRoutinesScreen/ViewModel
│   ├── edit/                RoutineEditScreen/ViewModel (crear y editar)
│   ├── detail/               RoutineDetailScreen/ViewModel (días, drag&drop, mover de día, agregar ejercicio)
│   └── addexercise/          AddExerciseScreen/ViewModel
├── templates/                TemplateLabels, list/TemplatesScreen/ViewModel, detail/TemplateDetailScreen/ViewModel
├── dashboard/    (planificado)
├── workout/                 WorkoutScreen/ViewModel (entrenamiento activo, timers, series, último entrenamiento)
├── history/      (planificado — HistoryRoute hoy renderiza ComingSoonScreen)
├── progress/     (planificado — ProgressRoute hoy renderiza ComingSoonScreen)
└── importroutine/(planificado — ImportCodeRoute/ImportImageRoute hoy renderizan ComingSoonScreen)
```

## Recursos (`app/src/main/res/`)

`values/strings.xml` (incluye `<plurals>` para "N ejercicio(s)"/"N serie(s)"/"N rep(s)"/"N
rutina(s)"), `values/colors.xml`, `values/themes.xml`, `xml/data_extraction_rules.xml`,
`xml/file_paths.xml` (`FileProvider`, usado recién en FASE 6), `xml/network_security_config.xml`,
más `drawable/`, `font/`, `mipmap-*/`, `raw/`.

## Tests (`app/src/test/java/com/lucho314/spotter/`)

Un test unitario por clase de producción con lógica no trivial, más `testutil/` con fakes
compartidos (`FakeAead`, `FakeAuthDataSource`, `FakeAuthRepository`, `FakeExerciseRemoteDataSource`,
`FakeExerciseRepository`, `FakeIdGenerator`, `FakeLogger`, `FakePreferencesRepository`,
`FakeRoutineRemoteDataSource`, `FakeRoutineRepository`, `FakeSharingRemoteDataSource`,
`FakeTemplateRepository`, `FakeTimeProvider`, `FakeWorkoutRemoteDataSource`, `FakeAiImportRemoteDataSource`,
`CountingLazy`, `MainDispatcherRule`). Los tests de Room (`ActiveWorkoutDaoTest`,
`CachedPayloadDaoTest`, `PendingWorkoutDaoTest`) corren con Robolectric sobre una base en memoria.
**355 `@Test`** en el código tras FASE 4 (conteo de anotaciones; la corrida anterior en verde,
tras FASE 3, tenía 308). Tests de FASE 4: `StartWorkoutUseCaseTest`,
`ToggleSetCompletionUseCaseTest`, `FinishWorkoutUseCaseTest`, `SyncPendingWorkoutsUseCaseTest`,
`SyncWorkoutsWorkerTest` (Robolectric), `WorkoutViewModelTest`, `RestTimerTest`, más casos nuevos en
`ActiveWorkoutDaoTest`, `ActiveWorkoutRepositoryImplTest`, `RoutineDetailViewModelTest` y
`ProfileViewModelTest`.

## Documentación (`docs/`)

`MIGRATION_PLAN.md` (plan completo), `review_carryover.md` (ítems diferidos), `backend/` (esquema
en vivo de Supabase y contexto de B1/B2), `arquitectura.md`, `estructura.md` (este archivo),
`componentes.md`, `changelog.md`.
