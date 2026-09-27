# Review carry-over items (from dev-reviewer, non-blocking at approval time)

## From FASE 2 approval (2026-09-26)
- [RESUELTO en FASE 3] `ActiveWorkoutRepositoryImpl.start()` safety net: on SQLiteConstraintException, if the re-query of getByUser returns null, RETHROW (safeCall → Failure) instead of returning Started (transaction rolled back, nothing inserted). Fixed with a test in `ActiveWorkoutRepositoryImplTest`.
- [RESUELTO en FASE 4] `replace(existingSessionId, workout)` doesn't verify the session belongs to workout.userId; StartWorkoutUseCase must always pass the id returned by AlreadyActive. Consider adding the ownership check in the DAO transaction. Fixed: `ActiveWorkoutDao.replaceActive` now checks `getUserIdForSession(existingSessionId)` against `session.userId` and throws `IllegalStateException` (→ `AppError.Unknown` via `safeCall`) on a mismatch, proceeding as before when the row no longer exists at all (nothing to protect). Covered by `ActiveWorkoutDaoTest` and `ActiveWorkoutRepositoryImplTest`. `StartWorkoutUseCase` always passes the id `ActiveWorkoutStartOutcome.AlreadyActive` itself reported.
- [RESUELTO en FASE 3] Legacy RN routines without days have all exercises at day_number=1. When the user creates the first day ("Organizar por días"), nextDayNumber returns 2 and exercises show as "Sin día asignado". UX: if routine has no days and all exercises are on 1, create the first day as day 1 / offer to assign them to it. Fixed via `RoutineOrdering.suggestedFirstDayNumber`, used by `RoutineDetailViewModel.onAddDay`.
- [RESUELTO en FASE 3] `nextDayNumber` may return null (1..7 taken by days or orphans) → show a validation message, never fall back to a default number. Fixed: `ValidationReason.DAYS_MAX_REACHED`, surfaced as a one-shot `RoutineDetailEvent.ActionFailed`.
- [RESUELTO en FASE 3] Moving an exercise to "sin día" (0) may hit 23505 Conflict (same exercise already on day 0) → show a clear message. Fixed: `R.string.error_exercise_already_in_day`, surfaced as a one-shot `RoutineDetailEvent.ActionFailed`.
- [RESUELTO en FASE 4] StartWorkoutUseCase: AlreadyActive → dialog "Continuar / Descartar y empezar" (RN bug #6); use `replace` only in the discard branch. Fixed: `RoutineDetailViewModel.onStartWorkout` + `RoutineDetailEvent.WorkoutAlreadyActive`; `RoutineDetailScreen` shows an `AlertDialog` with "Continuar"/"Descartar y empezar"/"Cancelar", re-invoking `onStartWorkout(day, replaceExisting = true)` with the exact same `DaySelection` the user originally picked. Covered by `RoutineDetailViewModelTest` and `StartWorkoutUseCaseTest`.

## From FASE 3 review cycle 3 (2026-09-26) — FASE 3 NOT yet approved (see blocking item)
- [RESUELTO en ciclo 4] `core/navigation/SpotterNavHost.kt`: `dropUnlessResumed` wraps async-result callbacks (onRoutinesCreated, onSaved, onExerciseAdded, onArchived, Onboarding onDone). Events are consumed from a Channel by `LaunchedEffect(Unit)` even while backgrounded (entry < RESUMED) → event dropped forever. Scenarios: adopt template + Home → no navigation, button re-enabled → duplicate routines on re-tap; save routine + switch app → `saving` stuck true. Fixed: `feature/common/ObserveAsEvents.kt` (lifecycle-aware `repeatOnLifecycle(STARTED)` collector) used across all 6 FASE 3 screens; the 5 result callbacks in `SpotterNavHost.kt` are now unguarded (dropUnlessResumed kept only on user-click callbacks); KDoc added on both the helper and the nav host explaining the distinction; covered by `ObserveAsEventsBehaviorTest`.
- [RESUELTO en FASE 4] Unassigned bucket for routines without days: `unassignedBucketDayNumber` returns 1, but template-adopted routines have exercises on 0 → mixing 0/1; "Organizar por días" then moves only the ones on 1. Use the single day number already shared by existing unassigned exercises, else 0/1 rule. Fixed: `RoutineOrdering.unassignedBucketDayNumber` now reuses whichever single `dayNumber` the routine's (all-unassigned, since it has no days) exercises already share, falling back to `1` only when there isn't one - the same rule as `suggestedFirstDayNumber`. Covered by 4 new cases in `RoutineOrderingTest`.
- [RESUELTO en ciclo 4] Snackbar relay: `savedStateHandle.getStateFlow(...)` without `remember(entry)`; `remove` doesn't emit in lifecycle 2.10.0 → recomposition can cancel an in-flight showSnackbar. Fixed: both `getStateFlow(...)` calls in `SpotterNavHost.kt` (`RoutinesRoute`, `RoutineDetailRoute`) are now wrapped in `remember(entry) { ... }`.
- [RESUELTO en FASE 6] Document inherent risk: cancellation while `createRoutine` is in flight can leave a server-side routine without known id (mitigated by BackHandler). Documented in KDoc of both `ImportSharedRoutineUseCase` and `AdoptTemplateUseCase` + `BackHandler` in both import screens.
- [RESUELTO en ciclo 4] AddExerciseViewModel: disable "Agregar" until RoutineDetail has emitted (otherwise sort_order 0 / UNASSIGNED with empty list). Fixed: `AddExerciseUiState.routineLoaded`, gated on a `routineDetailReady` flag; "Agregar" disabled in `AddExerciseScreen.kt` until true; `onConfirmAdd()` also guards itself defense-in-depth. Covered by a new test in `AddExerciseViewModelTest`.
- [Deferred, accepted] SavedStateHandle for edit form + search query; fewer intermediate refreshes during adoption.

## From FASE 3 approval (review cycle 4, 2026-09-26)
- [RESUELTO en FASE 4] Migrate `feature/auth/LoginScreen.kt` event collection to `ObserveAsEvents` for consistency (mind collectLatest vs scope.launch cancellation semantics). Fixed: `LoginScreen` now uses `ObserveAsEvents(viewModel.events) { ... }`; the actual credential request still runs on `scope.launch` (tied to the composition, not to the lifecycle-gated collector), so it survives a configuration change exactly as the previous `collectLatest` version did - only the *collection* is lifecycle-gated now, not the in-flight request.
- [RESUELTO en FASE 4] `OnboardingViewModel.onStartClick(onDone)` passes a composition lambda into viewModelScope (stale after config change) → migrate to a one-shot event + ObserveAsEvents. Fixed: `OnboardingViewModel.onStartClick()` (no argument) now emits `OnboardingEvent.Done` over a `Channel`; `OnboardingScreen` consumes it with `ObserveAsEvents`. Covered by `OnboardingViewModelTest`.
- [FASE 6+] AddExercise: if RoutineDetail never arrives (no cache + refresh fails), "Agregar" stays disabled with no message → show an error if a non-RoutineDetail entry point is ever added.
- Still open from cycle 3: document cancellation during in-flight createRoutine; SavedStateHandle for form/search; fewer intermediate refreshes during adoption.

## From FASE 4 approval (2026-09-26)
- [RESUELTO en FASE 5] Full sign-out cleanup (`SignOutUseCase`: clear Room - active workout + outbox after warning if unsynced - and per-user preferences). Implemented in `domain/usecase/SignOutUseCase.kt`: cancels alarm and worker, deletes all Room tables via `LocalDataRepository.clearAll()` (atomically, before `auth.signOut()`), calls `signOut()`, deletes per-user preferences with `clearUserScoped()` (preserves kg/lb unit). If Room deletion fails, sign-out is aborted and sync is rescheduled. Covered by `SignOutUseCaseTest` and integrated into `ProfileViewModel.onSignOutConfirmed()`.
- [RESUELTO en FASE 5] The "Entrenamiento en curso" banner now lives on **both** Dashboard and Routines (not just Routines anymore): extracted to `feature/common/ActiveWorkoutBanner.kt`, reusable composable shown conditionally on both screens. The plan considered moving it to Dashboard-only but decided to keep it on both for UX (Routines is the entry point for starting workouts).
- [RESUELTO en FASE 5] `WorkoutScreen`'s finish/discard flow now navigates to Dashboard via relay: `onFinished(online: Boolean)` callback passes the flag to `DashboardRoute` via `savedStateHandle`, and the Dashboard shows "¡Entrenamiento completado!" or "Guardado sin conexión…" accordingly. Discard still does `popBackStack()`. `WorkoutViewModel` adds internal `closing` flag to prevent the `NoActiveWorkout` race (emits only when not closing).
- [Nice-to-have, deferred] `WorkoutViewModel`'s in-app rest-finished beep/haptic is gated by `uiState`'s `WhileSubscribed(5_000)` subscription (i.e., "while the screen is at least STARTED, with a 5s grace tail"), not strict foreground detection - if the rest ends while the screen has been stopped for >5s, reopening the app recomputes and fires the beep once at that point (a "welcome back" cue) rather than staying silent. The background-reliability guarantee (the actual bug fix, RN bug #10) is independent of this: `core/notifications/RestTimerReceiver`'s scheduled alarm always fires the "Descanso terminado" notification regardless.

## Outstanding from FASE 6 (post-implementation, for future review cycles)

- **Compilation verification:** FASES 5-6 code (UI layers for Dashboard/History/Progress/Profile, sharing/importing, ViewModel/Feature tests) were written but **not compiled** due to lack of Android SDK in the dev environment. 234 tests of domain layer and pure helpers (calculations, formatters, validators, sanitizers, sizing, export builders) across FASES 1-6 were verified in a standalone JVM test harness outside the repo. 571 total `@Test` written; test infrastructure exists but not executed via `./gradlew`. When compiled on a machine with Android SDK, `./gradlew assembleDebug testDebugUnitTest` must be run to verify that all types, imports, and API signatures (Compose, Navigation, Room, Canvas/PDF drawing, Activity results, ClipboardManager) resolve correctly.
- **Manual verification on device/emulator:**
  - Deep link `spotter://import/CODE` after login opens `ImportCodeRoute` preview.
  - `ImportImageRoute`: camera picker works without `CAMERA` permission declared; photo rotation (EXIF) is correct; gallery image loads; import progress shows correctly.
  - Routine sharing: button on `RoutineDetailScreen`, clipboard copy, ACTION_SEND opens chooser.
  - PDF export: A4 paginates correctly, header repeats on "cont.", typography/colors match `SpotterColors`.
  - Story export: 1080×1920 JPEG renders correctly, "+N exercise" plurals work, image compresses to ~1-2 MB.
  - Relay flow: imported routine navigates to detail with snackbar; exported file launches share chooser.
  - Cleanup: temporary camera files purged after 1h; exported files purge after 1h of last modification.
- **Carry-over items from earlier phases:** See "Still open" sections in earlier approval entries.

## FASE 7 implementation (post-implementation, Parte A without independent review)

- [RESUELTO] ProGuard rules verified against Maven artifacts; only rules needed for Ktor (java.lang.management
  -dontwarn) and log stripping added; no redundant or app-level keep rules.
- [RESUELTO] data_extraction_rules.xml fixed: now excludes all five domains (root, file, database, sharedpref,
  external) for cloud-backup and device-transfer. **Bug fix:** pre-FASE 7 only excluded `root` — session,
  keyset, and Room workouts were transferred in device-to-device on Android 12+.
- [RESUELTO] Crash in API < 34: `LocalDate.ofInstant` replaced by `timeProvider.now().atZone(...).dayOfWeek`
  in `RoutineDetailViewModel.todayWeekdayName()`.
- [RESUELTO] Media3 `@OptIn(UnstableApi)` added to `ExerciseMedia.LoopingVideo`.
- [RESUELTO] `SecurityException` caught in `RestTimerReceiver.notify()` (permission revoked between check and call).
- [RESUELTO] `RestTimerAlarmScheduler` fallback to inexact alarm if `SCHEDULE_EXACT_ALARM` revoked.
- [RESUELTO] 15 tests of FASES 5-6 fixed: `runCurrent()`, `CompletableDeferred` gates, `@OptIn(ExperimentalCoroutinesApi)`.
- [RESUELTO] Accessibility: 48 dp targets, role/onClickLabel/onLongClickLabel on interactive elements.
- [RESUELTO] `verifyReleaseConfig` validated in 3 scenarios without printing secrets.

## FASE 7 Parte B (pending on user's machine)

- **`./gradlew :app:assembleDebug`** — verify compilation with Android SDK (Compose, Navigation, Room,
  Canvas/PDF, Activity results, ClipboardManager types and APIs).
- **`./gradlew :app:testDebugUnitTest`** — run full test suite on Android test runner (Robolectric for Room tests).
- **`./gradlew :app:lintDebug`** — static analysis; create baseline if only library warnings remain.
- **`./gradlew :app:assembleRelease`** — R8 obfuscation, lintVital; verify `mapping.txt` for `SyncWorkoutsWorker`
  name preservation.
- **APK verification:** no `http://` or `localhost` in app code; `BuildConfig` contains only approved constants.
- **Smoke test on device/emulator:** login, routines, workout offline/online, exports, deep links, import from image.
- **Real signing:** use EAS keystore (same as RN app), `./gradlew assembleRelease bundleRelease`,
  `apksigner verify`, register SHA-1 in Android OAuth client if using Credential Manager.

## Post-FASE 4 fix (2026-09-26)
- [RESUELTO] `core/navigation/SpotterNavHost.kt`: `RoutineDetailScreen`'s start-workout callback (driven by the async `RoutineDetailEvent.WorkoutStarted`, not a click) was wrapped in `dropUnlessResumed`; `ObserveAsEvents` can deliver at STARTED (before ON_RESUME), so the navigation was silently dropped and the workout was created but never opened. Fixed: renamed to `onOpenWorkout`, unguarded; new `NavController.navigateToWorkout()` (`launchSingleTop = true`) used by all three entry points (routines banner, `onOpenWorkout`, rest-finished notification via `SpotterRoot`), which also prevents stacking a second `WorkoutScreen` when the notification is tapped while the workout is already open. Not covered by an automated test (no Compose/Navigation UI test infrastructure) and **not compiled in the session that made the change** (no Android SDK available there) - run `./gradlew assembleDebug testDebugUnitTest` locally.
