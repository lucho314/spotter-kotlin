# Estructura del proyecto

Árbol verificado contra `app/src/main/java/com/lucho314/spotter/` y `app/src/test/java/com/lucho314/spotter/`
tras FASES 1-7 más la integración con Garmin Connect (2026-09-28). El 2026-09-28 se compiló y
probó por primera vez el árbol completo de Android en una máquina con SDK real: `./gradlew
:app:testDebugUnitTest` (**785 @Test, 0 fallos**), `:app:assembleDebug` y `:app:assembleRelease`
sin errores (detalle y caveats en `README.md`/`changelog.md`). Un solo módulo Gradle `:app`.
Archivos nuevos de FASE 7: `proguard-rules.pro` (reescrito), `keystore.properties.example`,
`data_extraction_rules.xml` (arreglado). Archivos nuevos de la integración con Garmin Connect:
`data/garmin/` (completo), `feature/garmin/` (completo), `domain/calc/Garmin*`,
`domain/model/GarminModels.kt`, `domain/repository/Garmin*Repository.kt`,
`domain/usecase/*Garmin*UseCase.kt`, `core/work/GarminUpload*.kt`,
`core/database/{entity/GarminUploadEntity,dao/GarminUploadDao}.kt`,
`feature/history/detail/GarminSessionUploadViewModel.kt`, `app/schemas/.../2.json` (esquema Room v2).

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
| `core/database/` | `SpotterDatabase.kt`, `DatabaseModule.kt`, `dao/ActiveWorkoutDao.kt`, `dao/CachedPayloadDao.kt`, `dao/PendingWorkoutDao.kt`, `dao/GarminUploadDao.kt`, `entity/ActiveWorkoutEntities.kt`, `entity/CachedPayloadEntity.kt`, `entity/PendingWorkoutEntities.kt`, `entity/GarminUploadEntity.kt` | Room v2 (`AutoMigration(1, 2)`): caché de lectura, entrenamiento activo, outbox de sincronización, cola de subida a Garmin (`garmin_upload`, v2) |
| `core/datastore/` | `DataStoreModule.kt` | Dos `DataStore<Preferences>`: `secure_auth` (sesión y PKCE cifrados) y `user_prefs` |
| `core/designsystem/component/` | `Chip.kt` (`SpotterChip`), `ConfirmDialog.kt`, `EmptyState.kt`, `ErrorState.kt`, `ExerciseMedia.kt` (`isVideoUrl`, `ExerciseMedia`), `LoadingState.kt`, `NumberStepper.kt`, `SectionHeader.kt`, `SpotterButton.kt`, `SpotterCard.kt`, `SpotterTextField.kt`, `LineChartGeometry.kt` (FASE 5), `LineChart.kt` (FASE 5), `StatCard.kt` (FASE 5) | Componentes Compose reutilizables |
| `core/designsystem/theme/` | `Color.kt`, `Shape.kt`, `Spacing.kt`, `Theme.kt`, `Type.kt` | Tema, tipografía, espaciado |
| `core/navigation/` | `DeepLink.kt` (`DeepLink`, `DeepLinkParser`), `RouteArgs.kt`, `Routes.kt`, `SpotterNavHost.kt`, `TopLevelDestination.kt` | Rutas type-safe, `NavHost` único, parsing de deep links |
| `core/network/` | `NetworkModule.kt`, `NetworkMonitor.kt`, `SafeCall.kt` (`safeCall`, `ErrorMapper`), `SupabaseModule.kt` | Cliente Supabase (Auth/Postgrest/Functions), mapeo de excepciones a `AppError`, monitor de conectividad |
| `core/security/` | `AeadProvider.kt` (`TinkAeadProvider`), `EncryptedCodeVerifierCache.kt`, `EncryptedSessionManager.kt`, `KeysetRecoveryPolicy.kt`, `SecurityModule.kt` | Cifrado de sesión/PKCE con Tink + Android Keystore |
| `core/notifications/` | `NotificationChannels.kt`, `NotificationsModule.kt`, `RestTimerAlarmScheduler.kt` (`AndroidRestTimerAlarmScheduler`), `RestTimerReceiver.kt` (`EXTRA_OPEN_WORKOUT`) | Canal `rest_timer` y alarma del temporizador de descanso (FASE 4) |
| `core/work/` | `SyncScheduler.kt` (`WorkManagerSyncScheduler`), `SyncWorkoutsWorker.kt`, `GarminUploadScheduler.kt` (`WorkManagerGarminUploadScheduler`), `GarminUploadWorker.kt`, `WorkModule.kt` | Sincronización del outbox con WorkManager (FASE 4); subida a Garmin Connect (Garmin) |

## `domain/` — lógica de negocio pura (sin Android, sin Hilt salvo `@Inject` en use cases)

| Paquete | Archivos | Contenido |
|---|---|---|
| `domain/model/` | `ActiveWorkoutModels.kt`, `AuthModels.kt`, `AiImportModels.kt` (FASE 6), `DashboardModels.kt` (`DashboardStats`), `ExerciseModels.kt`, `PendingWorkoutModels.kt`, `ProfileModels.kt`, `ProgressModels.kt`, `RoutineModels.kt`, `ShareCode.kt`, `SharingModels.kt` (con `SanitizedSharedRoutine` FASE 6), `TemplateModels.kt`, `WeightUnit.kt`, `WorkoutExportModels.kt` (FASE 6), `WorkoutModels.kt`, `GarminModels.kt` (Garmin: `GarminError`, `GarminResult`, `GarminConnectionState`, `GarminLoginResult`, `GarminActivitySnapshot`/`GarminSetSnapshot`, `GarminActivityPlan`/`GarminPlannedSet`, `GarminUploadStatus`/`GarminEnqueueResult`/`GarminUploadOutcome`) | Data classes / enums / sealed interfaces del dominio |
| `domain/repository/` | 12 interfaces + `LocalDataRepository.kt` (FASE 5) + `ImageRepository.kt`, `WorkoutExportRepository.kt` (FASE 6) + `GarminAccountRepository.kt`, `GarminActivityRepository.kt`, `GarminUploadRepository.kt` (Garmin) | Contratos que implementa `data/repository`/`data/garmin` |
| `domain/usecase/` | `AdoptTemplateUseCase.kt` (FASE 3); `StartWorkoutUseCase.kt`, `UpdateSetInputUseCase.kt`, `ToggleSetCompletionUseCase.kt`, `FinishWorkoutUseCase.kt`, `DiscardWorkoutUseCase.kt`, `SyncPendingWorkoutsUseCase.kt` (FASE 4); `GetDashboardStatsUseCase.kt`, `GetExerciseProgressUseCase.kt`, `GetProfileOverviewUseCase.kt`, `UpdateProfileUseCase.kt`, `SignOutUseCase.kt` (FASE 5); `ShareRoutineUseCase.kt`, `ImportSharedRoutineUseCase.kt`, `ImportRoutineFromImageUseCase.kt`, `BuildWorkoutExportUseCase.kt` (FASE 6); `ConnectGarminUseCase.kt`, `DisconnectGarminUseCase.kt`, `EnqueueGarminUploadUseCase.kt`, `RetryFailedGarminUploadsUseCase.kt`, `UploadPendingGarminActivitiesUseCase.kt` (Garmin) | Use cases del dominio |
| `domain/calc/` | `ActiveSetWeight.kt`, `AgeCalculator.kt`, `ExerciseProgressAggregator.kt`, `ExerciseSetGrouping.kt` (FASE 6), `NumberFormatter.kt`, `RoutineOrdering.kt`, `SetInputValidator.kt` (FASE 5), `SharedRoutineSanitizer.kt` (FASE 6), `SpanishWeekdays.kt`, `TextSanitizer.kt` (FASE 6), `Validators.kt`, `WeekRange.kt`, `WeightConverter.kt`, `WeightInputParser.kt`, `WorkoutExportDataBuilder.kt` (FASE 6), `WorkoutMath.kt`, `GarminActivityPlanner.kt`, `GarminExerciseMapper.kt` (Garmin) | Cálculos puros, 100% cubiertos por tests unitarios |

## `data/` — implementación de datos

| Paquete | Archivos | Contenido |
|---|---|---|
| `data/remote/dto/` | `Dtos.kt` (con DTOs nuevos `SharedRoutineImportDto` FASE 6) | DTOs `@Serializable` con `@SerialName` snake_case, espejo del esquema Supabase |
| `data/remote/datasource/` | Interfaz + `Supabase*RemoteDataSource` para: `AiImportRemoteDataSource`, `AuthDataSource`, `ExerciseRemoteDataSource`, `ProfileRemoteDataSource`, `ProgressRemoteDataSource`, `RoutineRemoteDataSource`, `SharingRemoteDataSource` (actualizada FASE 6), `TemplateRemoteDataSource`, `WorkoutRemoteDataSource`; más `DataSourceModule.kt` (`@Binds`) | Acceso a Supabase (Postgrest/Auth/Functions) detrás de interfaces, para poder testear los repos con fakes |
| `data/mapper/` | `ActiveWorkoutEntityMapper.kt`, `AuthStateMapper.kt`, `AuthUserMapper.kt`, `DateMappers.kt`, `ExerciseMapper.kt`, `PendingWorkoutMapper.kt`, `ProfileMapper.kt`, `ProgressMapper.kt`, `RoutineMapper.kt`, `SharingMapper.kt` (actualizado FASE 6), `TemplateMapper.kt`, `WorkoutMapper.kt` | DTO ↔ dominio y entidad Room ↔ dominio |
| `data/repository/` | `ActiveWorkoutRepositoryImpl.kt`, `AiImportErrorMapper.kt` (FASE 6), `AiImportRepositoryImpl.kt` (actualizado FASE 6), `AuthRepositoryImpl.kt`, `ExerciseRepositoryImpl.kt`, `ImageRepositoryImpl.kt` (FASE 6), `LocalDataRepositoryImpl.kt` (FASE 5, actualizado con `GarminUploadDao`), `PendingWorkoutRepositoryImpl.kt`, `PreferencesRepositoryImpl.kt`, `ProfileRepositoryImpl.kt`, `ProgressRepositoryImpl.kt`, `RepositoryModule.kt` (`@Binds`), `RoutineRepositoryImpl.kt`, `SharingRepositoryImpl.kt` (actualizado FASE 6), `TemplateRepositoryImpl.kt`, `WorkoutExportRepositoryImpl.kt` (FASE 6), `WorkoutHistoryRepositoryImpl.kt` | Las 12 + 2 implementaciones de `domain/repository` (FASE 6) |
| `data/export/` | `ExportFileNames.kt`, `ExportFileWriter.kt`, `ExportPalette.kt`, `PageCursor.kt`, `WorkoutExportRepositoryImpl.kt`, `WorkoutPdfRenderer.kt`, `WorkoutStoryRenderer.kt` | Generación de PDF/JPEG de entrenamientos (FASE 6) |
| `data/image/` | `ImageRepositoryImpl.kt`, `ImageSizing.kt` | Compresión/EXIF/base64 para la importación con IA (FASE 6) |
| `data/garmin/` | `GarminAccountRepositoryImpl.kt`, `GarminActivityRepositoryImpl.kt`, `GarminUploadRepositoryImpl.kt`, `GarminTokenManager.kt`, `GarminCall.kt` (`garminCall`, `GarminApiException`), `GarminModule.kt` (`@Binds`); `fit/` (`FitWriter.kt`, `FitBaseType.kt`, `FitCrc.kt`, `FitTime.kt`, `StrengthActivityFitEncoder.kt`); `remote/` (`GarminAuthRemoteDataSource.kt`/`KtorGarminAuthRemoteDataSource`, `GarminActivityRemoteDataSource.kt`/`KtorGarminActivityRemoteDataSource`, `GarminDtos.kt`, `GarminEndpoints.kt`, `GarminHttpClientFactory.kt`, `JwtClaims.kt`); `local/` (`GarminTokenStore.kt`/`EncryptedGarminTokenStore`); `mapper/` (`GarminSnapshotMapper.kt`) | Cliente propio de la API no oficial de Garmin Connect: login/MFA, refresh de tokens, encoder FIT propio, subida de actividades (ver `arquitectura.md`) |

## `feature/` — pantallas (Compose + ViewModel)

```
feature/
├── auth/                    LoginScreen/ViewModel, OnboardingScreen/ViewModel, GoogleCredentialClient, NonceGenerator
├── root/                    SpotterRoot, RootViewModel (guardia de sesión + deep link pendiente), ConfigErrorScreen
├── common/                  ObserveAsEvents, ErrorMessages, ValidationMessages, RoutineExerciseSummary, 
│                            ShareIntents (FASE 6), ActiveWorkoutBanner (FASE 5),
│                            DateFormats (FASE 5), SectionState (FASE 5)
├── profile/                 ProfileScreen/ViewModel — identidad, datos físicos, estadísticas, 
│                            unidad kg/lb, cerrar sesión con SignOutUseCase (FASE 5)
│                            BirthDateInput (FASE 5), ProfileGoalLabels (FASE 5)
│                            sección Garmin Connect (GarminSettingsSection, ver feature/garmin/)
├── exercise/                ExerciseDetailScreen/ViewModel, ExerciseLabels
├── routines/
│   ├── list/                RoutinesScreen/ViewModel, ArchivedRoutinesScreen/ViewModel
│   ├── edit/                RoutineEditScreen/ViewModel (crear y editar)
│   ├── detail/               RoutineDetailScreen/ViewModel (días, drag&drop, mover de día, agregar ejercicio)
│   │                        con ShareRoutineUseCase (FASE 6)
│   └── addexercise/          AddExerciseScreen/ViewModel
├── templates/                TemplateLabels, list/TemplatesScreen/ViewModel, detail/TemplateDetailScreen/ViewModel
├── dashboard/               DashboardScreen/ViewModel (FASE 5), DashboardFormatters (FASE 5)
├── workout/                 WorkoutScreen/ViewModel (entrenamiento activo, timers, series, último entrenamiento)
├── history/                 list/HistoryScreen/ViewModel, detail/SessionDetailScreen/ViewModel (FASE 5,
│                            con BuildWorkoutExportUseCase FASE 6), share/ShareWorkoutSheet (FASE 6),
│                            detail/GarminSessionUploadViewModel (acción "Subir a Garmin")
├── progress/                ProgressScreen/ViewModel (FASE 5)
├── importroutine/           code/ImportCodeScreen/ViewModel, image/ImportImageScreen/ViewModel,
│                            image/AiImportErrorKind (FASE 6)
└── garmin/                  GarminErrorMessages (GarminError → string);
                             connect/GarminConnectScreen/ViewModel (login + MFA + disclaimer);
                             settings/GarminSettingsSection/ViewModel (sección en Perfil: conectar/
                             desconectar, auto-upload, reintentar fallidos)
```

## Recursos (`app/src/main/res/`)

`values/strings.xml` (incluye `<plurals>` para "N ejercicio(s)"/"N serie(s)"/"N rep(s)"/"N
rutina(s)"), `values/colors.xml`, `values/themes.xml`, `xml/data_extraction_rules.xml`,
`xml/file_paths.xml` (`FileProvider`, usado recién en FASE 6), `xml/network_security_config.xml`,
más `drawable/`, `font/`, `mipmap-*/`, `raw/`.

## Tests (`app/src/test/java/com/lucho314/spotter/`)

Un test unitario por clase de producción con lógica no trivial, más `testutil/` con fakes
compartidos (`FakeAead`, `FakeAuthDataSource`, `FakeAuthRepository`, `FakeExerciseRemoteDataSource`,
`FakeExerciseRepository`, `FakeIdGenerator`, `FakeLogger`, `FakeLocalDataRepository` (FASE 5),
`FakePreferencesRepository`, `FakeProfileRepository` (FASE 5), `FakeProgressRepository` (FASE 5),
`FakeProfileRemoteDataSource` (FASE 5), `FakeRoutineRemoteDataSource`, `FakeRoutineRepository`,
`FakeSharingRemoteDataSource`, `FakeTemplateRepository`, `FakeTimeProvider`, `FakeWorkoutHistoryRepository`,
`FakeWorkoutRemoteDataSource`, `FakeAiImportRemoteDataSource`, `CountingLazy`, `MainDispatcherRule`,
`FakeGarminAccountRepository`, `FakeGarminActivityRepository`, `FakeGarminAuthRemoteDataSource`,
`FakeGarminTokenStore`, `FakeGarminUploadRepository`, `FakeGarminUploadScheduler` (Garmin)).
Los tests de Room (`ActiveWorkoutDaoTest`, `CachedPayloadDaoTest`, `LocalDataRepositoryImplTest` (FASE 5),
`PendingWorkoutDaoTest`, `GarminUploadDaoTest`, `SpotterDatabaseMigrationTest` — Garmin, reconstruye el
esquema v1 a mano porque los assets de `MigrationTestHelper` no están disponibles a los tests
unitarios locales) corren con Robolectric sobre una base en memoria.

**785 `@Test`** en el código (conteo de anotaciones), **verificados el 2026-09-28 con
`./gradlew :app:testDebugUnitTest` en una máquina con Android SDK real: 785 tests, 0 fallos**
(`assembleDebug`/`assembleRelease` también compilan; `lintDebug` falla por 34 errores preexistentes
no relacionados, ver `README.md`). Tests nuevos de la integración con Garmin Connect: `FitCrcTest`,
`FitWriterTest`, `StrengthActivityFitEncoderTest` (round-trip contra el FIT SDK oficial, solo en
tests), `GarminAccountRepositoryImplTest`, `GarminActivityRepositoryImplTest`,
`GarminTokenManagerTest`, `EncryptedGarminTokenStoreTest`, `KtorGarminActivityRemoteDataSourceTest`,
`KtorGarminAuthRemoteDataSourceTest`, `GarminActivityPlannerTest`, `GarminExerciseMapperTest`,
`ConnectGarminUseCaseTest`, `DisconnectGarminUseCaseTest`, `EnqueueGarminUploadUseCaseTest`,
`RetryFailedGarminUploadsUseCaseTest`, `UploadPendingGarminActivitiesUseCaseTest`,
`GarminUploadWorkerTest`, `GarminUploadDaoTest`, `SpotterDatabaseMigrationTest`,
`GarminConnectViewModelTest`, `GarminSettingsViewModelTest`, `GarminSessionUploadViewModelTest`.
Antes de esta corrida, el conteo histórico documentado tras FASE 7 era de 571 `@Test`, de los
cuales 473 (dominio, ViewModels, capa de datos y navegación) se habían verificado sin SDK en un
arnés JVM y el resto sin compilar; ver `changelog.md` para el detalle fase a fase. Tests de FASE 7 (correcciones):
agregado `runCurrent()` en `DashboardViewModelTest`, `HistoryViewModelTest`, `ProgressViewModelTest`,
`ProfileViewModelTest`, `SessionDetailViewModelTest`; `CompletableDeferred` gates en
`FakeImageRepository`/`FakeRoutineRepository` para tests de doble toque (`ImportImageViewModelTest`,
`ImportCodeViewModelTest`); `@OptIn(ExperimentalCoroutinesApi)` agregado a `ImportCodeViewModelTest`.
Tests nuevos de FASE 6: `TextSanitizerTest`, `SharedRoutineSanitizerTest`, `ExerciseSetGroupingTest`,
`WorkoutExportDataBuilderTest`, `ShareRoutineUseCaseTest`, `ImportSharedRoutineUseCaseTest`,
`ImportRoutineFromImageUseCaseTest`, `BuildWorkoutExportUseCaseTest`, `AiImportErrorKindTest`, `ImageSizingTest`,
`PageCursorTest`, `ExportFileNamesTest`, `DeepLinkParserTest`, `RouteArgsTest`, `RoutineDetailViewModelTest`,
`SessionDetailViewModelTest` (con `onExport`), `ImportCodeViewModelTest`, `ImportImageViewModelTest`, `AiImportRepositoryImplTest`,
`SharingRepositoryImplTest`, `DtoDecodingTest`. Tests de FASE 5: `GetDashboardStatsUseCaseTest`, `GetExerciseProgressUseCaseTest`,
`GetProfileOverviewUseCaseTest`, `UpdateProfileUseCaseTest`, `SignOutUseCaseTest`, `SetInputValidatorTest`, `HistoryViewModelTest`,
`ProgressViewModelTest`, `DashboardViewModelTest`, `DashboardFormattersTest`, `ProfileViewModelTest`, `WorkoutViewModelTest`,
`LocalDataRepositoryImplTest`, `ProfileRepositoryImplTest`, `LineChartGeometryTest`, `BirthDateInputTest`, `SpotterDateFormatsTest`.

## Documentación (`docs/`)

`MIGRATION_PLAN.md` (plan completo), `review_carryover.md` (ítems diferidos), `backend/` (esquema
en vivo de Supabase y contexto de B1/B2), `arquitectura.md`, `estructura.md` (este archivo),
`componentes.md`, `changelog.md`.
