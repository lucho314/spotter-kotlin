# Plan de migración: Spotter (React Native/Expo) a Android nativo en Kotlin

Fuente, solo lectura: `E:\Spotter`. Destino: `E:\Spoter Kotlin`. Ahí ya existen `CLAUDE.md`, `.mcp.json`, `skills-lock.json` y `.claude/`, y **no se tocan**. El directorio no es un repo git.

Todo se verificó leyendo el código fuente completo, consultando la API REST en vivo con la anon key pública (solo lecturas) y los metadatos de Maven. **Las versiones listadas están comprobadas contra `compileSdk 36`, que es lo que hay instalado.**

---

## 1. Objetivo

Al terminar tiene que cumplirse todo esto, y cada punto se puede comprobar:

1. **Compila.** `gradlew assembleDebug`, `gradlew testDebugUnitTest` y (desde la fase 7) `gradlew assembleRelease lintDebug` terminan OK con el JBR de Android Studio.
2. **Mismo backend.** Usa el mismo proyecto Supabase: tablas, RLS y la edge function `parse-routine-image`. **No hay cambios de schema.** Los problemas del backend quedan listados como ítems separados (sección 8) que requieren aprobación del usuario.
3. **Paridad funcional** con la sección 5 (inventario), con los bugs de la sección 7 corregidos del lado cliente.
4. **Entrenamiento activo resistente.** Sobrevive a la muerte del proceso (Room). Los entrenamientos terminados se guardan primero en un outbox local de Room y se sincronizan con WorkManager de forma idempotente: sin duplicados y sin pérdidas.
5. **Seguridad:**
   - Sesión cifrada (Tink AEAD + Android Keystore, persistida en DataStore).
   - URL y anon key por `local.properties` → `BuildConfig`, nunca commiteadas.
   - Sin tráfico en claro.
   - R8 activo en release.
   - Sin logs de tokens ni PII en release.
   - Deep links validados.
   - `allowBackup=false`.
6. **Tests unitarios** (JUnit4 + coroutines-test + Turbine + Truth + Robolectric para Room) de ViewModels, use cases, repositorios con fakes, mappers y cálculos. Todos pasan.

---

## 2. Toolchain local (verificado) y cómo compilar

**Lo que hay instalado:**

| Componente | Estado |
|---|---|
| JDK de Android Studio | `C:\Program Files\Android\Android Studio\jbr` = OpenJDK 21.0.9. La variable de usuario `JAVA_HOME` ya apunta ahí. |
| `java` del PATH | Temurin 25. **No usarlo:** es incompatible. |
| Android SDK | `C:\Users\remoto\AppData\Local\Android\Sdk` (`ANDROID_HOME` definido). |
| Plataformas SDK | `android-36`, `android-36.1`. No hay `android-37` ni `cmdline-tools`. |
| build-tools | 35.0.0, 36.0.0, 36.1.0, 37.0.0 |
| Gradle | No hay instalación global; hay caché en `~/.gradle/wrapper/dists/gradle-8.14.3-bin`. Hay internet. |

**Crear el wrapper** (sin gradle global). Se copian scripts y jar del proyecto RN; el jar del wrapper no depende de la versión:

```bash
cd "/e/Spoter Kotlin"
mkdir -p gradle/wrapper
cp "/e/Spotter/android/gradlew" "/e/Spotter/android/gradlew.bat" .
cp "/e/Spotter/android/gradle/wrapper/gradle-wrapper.jar" gradle/wrapper/
cat > gradle/wrapper/gradle-wrapper.properties <<'EOF'
distributionBase=GRADLE_USER_HOME
distributionPath=wrapper/dists
distributionUrl=https\://services.gradle.org/distributions/gradle-9.6.0-bin.zip
networkTimeout=10000
validateDistributionUrl=true
zipStoreBase=GRADLE_USER_HOME
zipStorePath=wrapper/dists
EOF
chmod +x gradlew
```

AGP 9.4.x exige como mínimo Gradle 9.6.0. Después de crear `settings.gradle.kts`, correr `./gradlew wrapper --gradle-version 9.6.0` para regenerar los scripts.

**Crear `local.properties`** con los valores de `E:\Spotter\.env`. **No imprimir los secretos:**

```bash
cd "/e/Spoter Kotlin"
set -a; . "/e/Spotter/.env"; set +a
{ echo 'sdk.dir=C\:\\Users\\remoto\\AppData\\Local\\Android\\Sdk'
  echo "SUPABASE_URL=$EXPO_PUBLIC_SUPABASE_URL"
  echo "SUPABASE_ANON_KEY=$EXPO_PUBLIC_SUPABASE_ANON_KEY"
  echo "GOOGLE_WEB_CLIENT_ID="; } > local.properties
```

**Comandos de build y test** (Git Bash). Poner el JBR en PATH siempre:

```bash
export JAVA_HOME="/c/Program Files/Android/Android Studio/jbr"
export PATH="$JAVA_HOME/bin:$PATH"
cd "/e/Spoter Kotlin"
./gradlew assembleDebug
./gradlew testDebugUnitTest
./gradlew testDebugUnitTest --tests "com.lucho314.spotter.domain.calc.*"   # un subconjunto
./gradlew assembleRelease lintDebug                                          # fase 7
```

Equivalente en PowerShell: `$env:JAVA_HOME="C:\Program Files\Android\Android Studio\jbr"; .\gradlew.bat assembleDebug`.

La ruta del proyecto tiene un espacio (`Spoter Kotlin`): hay que citarla siempre.

---

## 3. Registro de decisiones de arquitectura (ADR)

| # | Decisión | Justificación |
|---|---|---|
| A1 | **Un solo módulo `:app`** con paquetes `core/`, `domain/`, `data/`, `feature/<x>/` y fronteras estrictas: `feature` → `domain` ← `data`; `feature` nunca importa `data`. | La app tiene ~10k líneas y un solo desarrollador. La guía de modularización de Google justifica multi-módulo por equipos o tiempos de build; aquí agregaría convention plugins y riesgo para un implementador agente. Queda preparada para extraer `:core:*` y `:feature:*` más adelante, porque los paquetes ya reflejan esos módulos. |
| A2 | **Capas** según la *Guide to app architecture*. UI: Compose + ViewModel + `StateFlow<UiState>` (UDF) y eventos one-shot por `Channel`. Dominio: modelos puros, interfaces de repositorio, use cases solo donde hay lógica real. Datos: repositorios + data sources (remoto Supabase, local Room/DataStore). | Pedido explícito. Los data sources remotos son interfaces, así los repos se testean con fakes sin mockear supabase-kt. |
| A3 | **Offline-first selectivo con Room.** (a) Entrenamiento activo: tablas relacionales, fuente única de verdad. (b) Outbox `pending_workout(+sets)`: finalizar siempre escribe aquí y WorkManager sincroniza. (c) Caché de lectura en forma de *payload JSON tipado* (`cached_payload`) para rutinas, detalle de rutina, catálogo de ejercicios y grupos musculares, así se puede entrenar sin señal. Historial, progreso, plantillas y perfil van solo por red, con estados de error. | Arregla la duplicación y las pérdidas del RN (sección 7). El cache JSON evita ~6 entidades extra: los datos son de lectura mayoritaria y las relaciones se resuelven en el servidor. |
| A4 | **Hilt** con KSP (no kapt: AGP 9 lo prohíbe con Kotlin integrado). | Pedido explícito. |
| A5 | **Navigation Compose 2.9.8** con rutas `@Serializable` type-safe. Un solo `NavHost` dentro de la raíz autenticada; la barra inferior se muestra solo en destinos top-level. **Los deep links no usan `navDeepLink`:** se parsean en `DeepLinkParser` y se encolan hasta que haya sesión. | Evita que un deep link saltee el guardia de autenticación y deja la validación en un solo lugar testeable. |
| A6 | **supabase-kt 3.8.0** (Auth, Postgrest, Functions) + motor Ktor OkHttp 3.5.1. | Cliente oficial. Exige Kotlin ≥ 2.4.0 (lo muestra su pom). |
| A7 | **Login con Google en dos modos.** Primario: *Credential Manager* nativo (ID token, `auth.signInWith(IDToken)` con nonce: hash SHA-256 a Google, nonce crudo a Supabase), cuando `GOOGLE_WEB_CLIENT_ID` no está vacío. Respaldo: OAuth PKCE por navegador con deep link `spotter://auth/callback` y `supabase.handleDeeplinks(intent)`. | Credential Manager es la API recomendada por Google y Supabase para Android: sin navegador, sin superficie de interceptación de deep links y mejor UX. El PKCE ya está configurado en el proyecto Supabase, así funciona desde el día 1 aunque falte crear el cliente OAuth de Android en Google Cloud. |
| A8 | **Sesión cifrada.** `SessionManager` propio: `UserSession` serializada, cifrada con Tink `Aead` (AES256-GCM, keyset protegido por la master key del Android Keystore) y guardada en DataStore. Mismo esquema para `CodeVerifierCache`. | `androidx.security-crypto` está deprecado. Por defecto supabase-kt guarda en SharedPreferences en texto plano. |
| A9 | **Errores tipados.** `AppResult<T>` (`Success`/`Failure(AppError)`) en fronteras de repositorio y use case. `safeCall{}` mapea excepciones y re-lanza `CancellationException`. Validación en el dominio (`ValidationReason`). | Nada de excepciones tragadas; UI determinista. |
| A10 | **Gráfico propio en Compose `Canvas`** (una sola línea) en lugar de Vico. **Drag & drop** con `sh.calvin.reorderable` 3.1.0. | El gráfico es trivial; se evita la inestabilidad de API de Vico. Compose no trae reordenamiento por arrastre incorporado. |
| A11 | **Exportación nativa.** PDF con `android.graphics.pdf.PdfDocument`; historia como **JPEG 1080x1920** dibujado en `Canvas`; se comparte por `FileProvider`. | Elimina la inyección HTML y entrega la historia como imagen, que es lo que se quería (en RN salía como PDF). |
| A12 | **Unidad de peso.** Se guarda siempre en kg (el backend es `weight_kg`). La conversión a kg/lb es solo de presentación y entrada. La unidad se fija al iniciar cada entrenamiento. | La preferencia existía en RN (`stores/app-store.ts`) pero nunca se usaba. |

**Paquete y compilación:**

- `applicationId`/`namespace`: `com.lucho314.spotter`. Debug usa `applicationIdSuffix ".debug"`.
- `minSdk 26` (java.time sin desugaring), `compileSdk 36`, `targetSdk 36`.
- Java/JVM target 17.
- Idioma de UI: español rioplatense, con textos en `res/values/strings.xml`.

---

## 4. Versiones (`gradle/libs.versions.toml`), verificadas en Maven hoy

**Las versiones están elegidas para `compileSdk 36`.** Compose UI 1.12, navigation 2.10, lifecycle 2.11, core 1.19 y hilt-lifecycle-viewmodel-compose 1.4 exigen `compileSdk 37`, que no está instalado. **No subirlas.** Si `checkDebugAarMetadata` falla por una dependencia transitiva que exige 37, forzar la versión anterior o reportarlo.

```toml
[versions]
agp = "9.4.1"
kotlin = "2.4.20"
ksp = "2.3.12"
composeBom = "2026.06.01"        # ui 1.11.4, material3 1.4.0, icons-extended 1.7.8
coreKtx = "1.18.0"
activityCompose = "1.12.4"
lifecycle = "2.10.0"
navigation = "2.9.8"
hilt = "2.60.1"
androidxHilt = "1.3.0"
room = "2.8.5"
work = "2.11.2"
datastore = "1.2.1"
coreSplashscreen = "1.2.0"
supabase = "3.8.0"
ktor = "3.5.1"
serialization = "1.11.0"
coroutines = "1.11.0"
credentials = "1.6.0"
googleId = "1.2.1"
tink = "1.23.0"
coil = "3.5.0"                   # 3.6.x resolves to compose-foundation 1.12.0, which needs compileSdk 37
media3 = "1.11.1"
reorderable = "3.1.0"
exifinterface = "1.4.2"
junit = "4.13.2"
truth = "1.4.5"
turbine = "1.2.1"
robolectric = "4.17"
androidxTestCore = "1.7.0"
androidxTestExtJunit = "1.3.0"

[libraries]
androidx-core-ktx = { group = "androidx.core", name = "core-ktx", version.ref = "coreKtx" }
androidx-core-splashscreen = { group = "androidx.core", name = "core-splashscreen", version.ref = "coreSplashscreen" }
androidx-activity-compose = { group = "androidx.activity", name = "activity-compose", version.ref = "activityCompose" }
androidx-lifecycle-runtime-compose = { group = "androidx.lifecycle", name = "lifecycle-runtime-compose", version.ref = "lifecycle" }
androidx-lifecycle-viewmodel-compose = { group = "androidx.lifecycle", name = "lifecycle-viewmodel-compose", version.ref = "lifecycle" }
androidx-lifecycle-process = { group = "androidx.lifecycle", name = "lifecycle-process", version.ref = "lifecycle" }
androidx-navigation-compose = { group = "androidx.navigation", name = "navigation-compose", version.ref = "navigation" }
androidx-compose-bom = { group = "androidx.compose", name = "compose-bom", version.ref = "composeBom" }
androidx-compose-ui = { group = "androidx.compose.ui", name = "ui" }
androidx-compose-ui-graphics = { group = "androidx.compose.ui", name = "ui-graphics" }
androidx-compose-ui-tooling = { group = "androidx.compose.ui", name = "ui-tooling" }
androidx-compose-ui-tooling-preview = { group = "androidx.compose.ui", name = "ui-tooling-preview" }
androidx-compose-material3 = { group = "androidx.compose.material3", name = "material3" }
androidx-compose-material-icons-extended = { group = "androidx.compose.material", name = "material-icons-extended" }
hilt-android = { group = "com.google.dagger", name = "hilt-android", version.ref = "hilt" }
hilt-compiler = { group = "com.google.dagger", name = "hilt-android-compiler", version.ref = "hilt" }
androidx-hilt-lifecycle-viewmodel-compose = { group = "androidx.hilt", name = "hilt-lifecycle-viewmodel-compose", version.ref = "androidxHilt" }
androidx-hilt-work = { group = "androidx.hilt", name = "hilt-work", version.ref = "androidxHilt" }
androidx-hilt-compiler = { group = "androidx.hilt", name = "hilt-compiler", version.ref = "androidxHilt" }
androidx-room-runtime = { group = "androidx.room", name = "room-runtime", version.ref = "room" }
androidx-room-ktx = { group = "androidx.room", name = "room-ktx", version.ref = "room" }
androidx-room-compiler = { group = "androidx.room", name = "room-compiler", version.ref = "room" }
androidx-room-testing = { group = "androidx.room", name = "room-testing", version.ref = "room" }
androidx-work-runtime-ktx = { group = "androidx.work", name = "work-runtime-ktx", version.ref = "work" }
androidx-work-testing = { group = "androidx.work", name = "work-testing", version.ref = "work" }
androidx-datastore-preferences = { group = "androidx.datastore", name = "datastore-preferences", version.ref = "datastore" }
supabase-bom = { group = "io.github.jan-tennert.supabase", name = "bom", version.ref = "supabase" }
supabase-auth = { group = "io.github.jan-tennert.supabase", name = "auth-kt" }
supabase-postgrest = { group = "io.github.jan-tennert.supabase", name = "postgrest-kt" }
supabase-functions = { group = "io.github.jan-tennert.supabase", name = "functions-kt" }
ktor-client-okhttp = { group = "io.ktor", name = "ktor-client-okhttp", version.ref = "ktor" }
kotlinx-serialization-json = { group = "org.jetbrains.kotlinx", name = "kotlinx-serialization-json", version.ref = "serialization" }
kotlinx-coroutines-test = { group = "org.jetbrains.kotlinx", name = "kotlinx-coroutines-test", version.ref = "coroutines" }
androidx-credentials = { group = "androidx.credentials", name = "credentials", version.ref = "credentials" }
androidx-credentials-play-services-auth = { group = "androidx.credentials", name = "credentials-play-services-auth", version.ref = "credentials" }
googleid = { group = "com.google.android.libraries.identity.googleid", name = "googleid", version.ref = "googleId" }
tink-android = { group = "com.google.crypto.tink", name = "tink-android", version.ref = "tink" }
coil-compose = { group = "io.coil-kt.coil3", name = "coil-compose", version.ref = "coil" }
coil-network-okhttp = { group = "io.coil-kt.coil3", name = "coil-network-okhttp", version.ref = "coil" }
coil-gif = { group = "io.coil-kt.coil3", name = "coil-gif", version.ref = "coil" }
media3-exoplayer = { group = "androidx.media3", name = "media3-exoplayer", version.ref = "media3" }
media3-ui-compose = { group = "androidx.media3", name = "media3-ui-compose", version.ref = "media3" }
reorderable = { group = "sh.calvin.reorderable", name = "reorderable", version.ref = "reorderable" }
androidx-exifinterface = { group = "androidx.exifinterface", name = "exifinterface", version.ref = "exifinterface" }
junit = { group = "junit", name = "junit", version.ref = "junit" }
truth = { group = "com.google.truth", name = "truth", version.ref = "truth" }
turbine = { group = "app.cash.turbine", name = "turbine", version.ref = "turbine" }
robolectric = { group = "org.robolectric", name = "robolectric", version.ref = "robolectric" }
androidx-test-core-ktx = { group = "androidx.test", name = "core-ktx", version.ref = "androidxTestCore" }
androidx-test-ext-junit = { group = "androidx.test.ext", name = "junit", version.ref = "androidxTestExtJunit" }

[plugins]
android-application = { id = "com.android.application", version.ref = "agp" }
kotlin-compose = { id = "org.jetbrains.kotlin.plugin.compose", version.ref = "kotlin" }
kotlin-serialization = { id = "org.jetbrains.kotlin.plugin.serialization", version.ref = "kotlin" }
ksp = { id = "com.google.devtools.ksp", version.ref = "ksp" }
hilt = { id = "com.google.dagger.hilt.android", version.ref = "hilt" }
room = { id = "androidx.room", version.ref = "room" }
```

**AGP 9 trae Kotlin integrado. No aplicar `org.jetbrains.kotlin.android`, porque falla con "Cannot add extension with name 'kotlin'".**

- Las opciones del compilador van en `kotlin { compilerOptions { ... } }`.
- En el `build.gradle.kts` raíz declarar `alias(libs.plugins.kotlin.compose) apply false` y `alias(libs.plugins.kotlin.serialization) apply false`. Eso fija KGP 2.4.20 en el classpath.
- Si aun así el build se queja de la versión de Kotlin, agregar al `plugins {}` raíz `id("org.jetbrains.kotlin.android") version "2.4.20" apply false` y **no aplicarlo** en `:app`.

---

## 5. Inventario de funcionalidades (verificado en el código) → destino Kotlin

| Funcionalidad (fuente) | Notas del RN real | Destino Kotlin |
|---|---|---|
| **Login con Google** (`lib/auth.tsx`, `app/(auth)/login.tsx`, `app/auth/callback.tsx`) | Solo Google OAuth PKCE. `schemas/auth.schema.ts` con email/password existe pero está **sin usar**. | `feature/auth/` + `data/repository/AuthRepositoryImpl` |
| **AuthGuard** (`app/_layout.tsx`) | Redirige según la sesión. | `RootViewModel` + `SpotterRoot` |
| **Onboarding** (`app/(auth)/onboarding.tsx`) | **Inalcanzable** en RN. | Se muestra una vez por usuario (flag en DataStore) |
| **Dashboard** (`app/(tabs)/index.tsx`) | Saludo, sesiones de la semana, tarjeta "última sesión", último PR, 3 rutinas ordenadas por día, estado vacío, pull-to-refresh. | `feature/dashboard/` |
| **Rutinas: lista** (`routines/index.tsx`) | Orden por día de la semana, importar por código (barra), accesos a IA, crear y plantillas, archivar con long-press. | `feature/routines/list/` |
| **Crear rutina** (`routines/create.tsx`, `schemas/routine.schema.ts`) | Nombre 1..50, descripción ≤200, días 1..7. | `feature/routines/edit/`, que además sirve para **editar**: `useUpdateRoutine` existía sin UI |
| **Detalle de rutina** (`routines/[id].tsx`, `components/routines/*`) | Días (Lunes..Domingo) agregar, renombrar y borrar; ejercicios por día; "sin día asignado"; reordenar con drag; editar sets/reps/descanso/día; quitar; compartir código; iniciar entrenamiento. | `feature/routines/detail/` |
| **Agregar ejercicio** (`routines/add-exercise.tsx`) | Catálogo, filtro por grupo muscular, búsqueda, stepper de sets/reps/descanso (descanso con mínimo 15 y paso 15). | `feature/routines/addexercise/` |
| **Plantillas** (`routines/templates/*`, `components/templates/*`, `services/templates.ts`) | Filtros por objetivo y por días (2..6). El detalle muestra días expandibles. "Adoptar" crea **una rutina por día**. | `feature/templates/` |
| **Importar por código** (`app/import/[code].tsx`, `services/sharing.ts`) + deep link `spotter://import/{code}` | Preview y confirmación; nombre con sufijo " (importada)". | `feature/importroutine/code/` |
| **Importar con IA desde imagen** (`routines/import-image.tsx`, `services/ai-routine-import.ts`) | Cámara o galería, base64, edge function. | `feature/importroutine/image/` |
| **Entrenamiento activo** (`app/workout/[routineId].tsx`, `stores/workout-store.ts`, `components/workout/*`) | Sets de peso y reps, completar y descompletar, agregar serie, navegar ejercicios, timer de sesión, timer de descanso con beep, video o imagen del ejercicio, hoja "último entrenamiento", finalizar, cancelar, persistencia (AsyncStorage). | `feature/workout/` + Room + WorkManager + alarma |
| **Sincronización offline** (`services/workout-sync.ts`, `hooks/use-offline-sync.ts`) | Cola en AsyncStorage + NetInfo + toast "Sin conexión". | Outbox Room + `SyncWorkoutsWorker` + `NetworkMonitor` |
| **Historial: lista y detalle** (`history/index.tsx`, `history/[id].tsx`) | Borrar sesión (long-press); editar, agregar y borrar series; compartir. | `feature/history/` |
| **Exportar y compartir entrenamiento** (`services/workout-export.ts`, `workout-share-modal.tsx`) | PDF detallado + "historia" (en RN salía como PDF). | `data/export/` + `feature/history/share/` |
| **Progreso** (`progress.tsx`, `components/progress/*`) | Tabla de PRs (1RM Epley), chips por ejercicio, gráfico. | `feature/progress/` |
| **Detalle de ejercicio** (`app/exercise/[id].tsx`) | Media, badges, músculos secundarios, instrucciones. | `feature/exercise/` |
| **Perfil** (`profile.tsx`) | Avatar, nombre, email, estadísticas (entrenos, PRs, rutinas), datos físicos (peso, altura, nacimiento → edad, objetivo), cerrar sesión. "Notificaciones/Tema/Idioma" eran placeholders sin acción. | `feature/profile/`: los placeholders se reemplazan por "Unidad de peso (kg/lb)" |
| **Preferencia kg/lb** (`stores/app-store.ts`) | **Sin uso en RN.** | `PreferencesRepository` + conversión en toda la UI y en las exportaciones |

---

## 6. Schema de Supabase: DTOs exactos (verificados en vivo)

Los `.sql` del repo fuente están **desactualizados**. La base en vivo tiene además:

- las tablas `routine_days`, `routine_templates`, `template_days` y `template_day_exercises`;
- las columnas `exercises.gif_url` (que en realidad son **URLs .mp4**: 53 de 104 verificadas), `secondary_muscles` (`text[]`), `instructions` (`text[]`), `difficulty`, `category` y `exercisedb_id`;
- `routine_exercises.day_number`, `routines.source_template_id` y `profiles.weight_kg/height_cm/birth_date/fitness_goal`.

Se confirmó que **no existen** `routine_days.created_at`, `routine_exercises.notes`, `workout_sets.notes` ni `workout_sessions.duration_seconds`.

**Configuración JSON del cliente:**

```kotlin
Json { ignoreUnknownKeys = true; explicitNulls = false; encodeDefaults = true; coerceInputValues = true }
```

**Reglas para los DTOs:**

- Los DTO de insert **no llevan valores por defecto de Kotlin** en campos que deben enviarse.
- Los nulos se omiten al codificar, para que se apliquen los defaults de la base. Esto importa para `day_number`, cuyo default o nulabilidad no pudo verificarse.
- Para asignar `NULL` explícito en updates usar `update({ set("col", null as Double?) })`.
- `numeric` → `Double`; `smallint`/`integer` → `Int`.
- `timestamptz` → `String` en el DTO; el mapper lo parsea con `OffsetDateTime.parse(s).toInstant()`.
- `date` → `String "yyyy-MM-dd"` → `LocalDate`.

Archivo: `app/src/main/java/com/lucho314/spotter/data/remote/dto/Dtos.kt`. Todos `@Serializable` y con `@SerialName` en snake_case.

```kotlin
data class MuscleGroupDto(val id: Int, val name: String, @SerialName("name_en") val nameEn: String)
data class ExerciseDto(val id: Int, val name: String, @SerialName("name_en") val nameEn: String,
  @SerialName("muscle_group_id") val muscleGroupId: Int, val equipment: String,
  @SerialName("image_url") val imageUrl: String? = null, @SerialName("gif_url") val gifUrl: String? = null,
  @SerialName("secondary_muscles") val secondaryMuscles: List<String>? = null, val instructions: List<String>? = null,
  val difficulty: String? = null, val category: String? = null, @SerialName("exercisedb_id") val exercisedbId: String? = null,
  @SerialName("muscle_groups") val muscleGroup: MuscleGroupDto? = null)
data class IdDto(val id: String)
data class RoutineDayDto(val id: String, @SerialName("routine_id") val routineId: String, @SerialName("day_number") val dayNumber: Int, val name: String)
data class RoutineExerciseDto(val id: String, @SerialName("routine_id") val routineId: String, @SerialName("exercise_id") val exerciseId: Int,
  @SerialName("sort_order") val sortOrder: Int, @SerialName("day_number") val dayNumber: Int? = null,
  @SerialName("target_sets") val targetSets: Int, @SerialName("target_reps") val targetReps: Int, @SerialName("rest_seconds") val restSeconds: Int,
  @SerialName("exercises") val exercise: ExerciseDto? = null)
data class RoutineSummaryDto(id, @SerialName("user_id") userId, name, description: String? = null, @SerialName("days_per_week") daysPerWeek: Int? = null,
  @SerialName("is_archived") isArchived: Boolean, @SerialName("source_template_id") sourceTemplateId: String? = null,
  @SerialName("created_at") createdAt: String, @SerialName("updated_at") updatedAt: String,
  @SerialName("routine_exercises") routineExercises: List<IdDto> = emptyList(), @SerialName("routine_days") routineDays: List<RoutineDayDto> = emptyList())
data class RoutineDetailDto(/* same scalar fields */, @SerialName("routine_exercises") routineExercises: List<RoutineExerciseDto> = emptyList(), @SerialName("routine_days") routineDays: List<RoutineDayDto> = emptyList())
data class RoutineRefDto(val id: String, val name: String)
data class WorkoutSetDto(id, @SerialName("session_id") sessionId, @SerialName("exercise_id") exerciseId: Int, @SerialName("set_number") setNumber: Int,
  @SerialName("weight_kg") weightKg: Double, reps: Int, rpe: Double? = null, @SerialName("is_warmup") isWarmup: Boolean,
  @SerialName("completed_at") completedAt: String, @SerialName("exercises") exercise: ExerciseDto? = null)
data class WorkoutSessionDto(id, @SerialName("user_id") userId, @SerialName("routine_id") routineId: String? = null, @SerialName("started_at") startedAt: String,
  @SerialName("completed_at") completedAt: String? = null, notes: String? = null, status: String,
  @SerialName("routines") routine: RoutineRefDto? = null, @SerialName("workout_sets") sets: List<WorkoutSetDto> = emptyList())
data class PersonalRecordDto(id, @SerialName("user_id") userId, @SerialName("exercise_id") exerciseId: Int, @SerialName("best_weight_kg") bestWeightKg: Double,
  @SerialName("best_reps_at_weight") bestRepsAtWeight: Int, @SerialName("estimated_1rm") estimated1Rm: Double,
  @SerialName("achieved_at") achievedAt: String, @SerialName("updated_at") updatedAt: String, @SerialName("exercises") exercise: ExerciseDto? = null)
data class ProfileDto(id, @SerialName("display_name") displayName: String, @SerialName("avatar_url") avatarUrl: String? = null,
  @SerialName("weight_kg") weightKg: Double? = null, @SerialName("height_cm") heightCm: Int? = null,
  @SerialName("birth_date") birthDate: String? = null, @SerialName("fitness_goal") fitnessGoal: String? = null)
data class SharedRoutineDto(id, @SerialName("routine_id") routineId, @SerialName("shared_by") sharedBy, @SerialName("share_code") shareCode,
  @SerialName("is_active") isActive: Boolean, @SerialName("created_at") createdAt: String, @SerialName("expires_at") expiresAt: String? = null,
  @SerialName("routines") routine: RoutineDetailDto? = null)
data class RoutineTemplateDto(id, name, @SerialName("name_es") nameEs, description: String? = null, @SerialName("description_es") descriptionEs: String? = null,
  goal: String, difficulty: String, @SerialName("days_per_week") daysPerWeek: Int, @SerialName("is_active") isActive: Boolean,
  @SerialName("sort_order") sortOrder: Int, @SerialName("template_days") days: List<TemplateDayDto> = emptyList())
data class TemplateDayDto(id, @SerialName("template_id") templateId, @SerialName("day_number") dayNumber: Int, name, @SerialName("name_es") nameEs,
  description: String? = null, @SerialName("template_day_exercises") exercises: List<TemplateDayExerciseDto> = emptyList())
data class TemplateDayExerciseDto(id, @SerialName("template_day_id") templateDayId, @SerialName("exercise_id") exerciseId: Int, @SerialName("sort_order") sortOrder: Int,
  @SerialName("target_sets") targetSets: Int, @SerialName("target_reps") targetReps: Int, @SerialName("rest_seconds") restSeconds: Int,
  notes: String? = null, @SerialName("exercises") exercise: ExerciseDto? = null)
// Inserts (no Kotlin defaults on required fields)
data class RoutineInsertDto(userId, name, description: String?, daysPerWeek: Int?, sourceTemplateId: String?)   // + @SerialName
data class RoutineExerciseInsertDto(routineId, exerciseId: Int, sortOrder: Int, dayNumber: Int?, targetSets: Int, targetReps: Int, restSeconds: Int)
data class RoutineDayInsertDto(routineId, dayNumber: Int, name: String)
data class WorkoutSessionInsertDto(id: String, userId, routineId: String?, status: String, startedAt: String, completedAt: String, notes: String?)
data class WorkoutSetInsertDto(id: String, sessionId, exerciseId: Int, setNumber: Int, weightKg: Double, reps: Int, isWarmup: Boolean, completedAt: String)
data class SharedRoutineInsertDto(routineId, sharedBy, shareCode)
data class ParseRoutineImageRequest(@SerialName("image_base64") val imageBase64: String, @SerialName("image_mime_type") val mimeType: String, @SerialName("user_id") val userId: String)
data class ParseRoutineImageResponse(@SerialName("routine_id") val routineId: String? = null, @SerialName("routine_name") val routineName: String? = null, val error: String? = null)
```

**Consultas** (supabase-kt 3.x):

- **Filtrar SIEMPRE por `user_id` las consultas de `routines`.** La RLS en vivo expone a cualquiera las rutinas compartidas de otros usuarios (sección 8).
- **UNIQUE en vivo de `routine_exercises`:** `(routine_id, exercise_id, day_number)`, no `(routine_id, exercise_id)` como dice `01_schema.sql`. Un insert que repita ese trío falla con `23505`.
- Rutinas (lista): `select(Columns.raw("*, routine_exercises(id), routine_days(*)")) { filter { eq("user_id", uid); eq("is_archived", false) }; order("created_at", Order.DESCENDING) }`.
- Rutina (detalle): `"*, routine_days(*), routine_exercises(*, exercises(*, muscle_groups(*)))"`.
- Historial: `"*, routines(id,name)"` con `status = completed`, orden `started_at` desc, paginado con `range(from, to)` de 30 en 30.
- Detalle de sesión: `"*, routines(id,name), workout_sets(*, exercises(*, muscle_groups(*)))"`.
- Última sesión de un ejercicio: `workout_sets` con `"id, session_id, set_number, weight_kg, reps, is_warmup, completed_at, workout_sessions!inner(user_id,status,started_at)"` y filtros `workout_sessions.user_id = uid`, `workout_sessions.status = completed`, `exercise_id = X`, `is_warmup = false`. Orden `completed_at` desc, `limit(200)`. Se agrupa por `session_id` del primer registro.
- Progreso: la misma consulta, `limit(500)`. La agregación por sesión la hace el dominio.
- Conteos: `select(Columns.list("id")) { count(Count.EXACT); limit(0); filter{...} }.countOrNull()`.
- Upsert idempotente: `from("workout_sessions").upsert(dto) { onConflict = "id"; ignoreDuplicates = true }`, e igual para la lista de sets.

---

## 7. Bugs encontrados en el RN que NO deben migrarse

Formato: archivo:línea, problema, y cómo lo evita la versión Kotlin.

### Entrenamiento y sincronización

1. **`services/workout-sync.ts:24-45` — guardado no atómico ni idempotente.** Se inserta la sesión; si falla el insert de sets se encola todo y al reintentar se inserta **otra sesión**. Un timeout después del commit también duplica. **Kotlin:** los ids de sesión y de sets son UUID generados en el cliente y se envían con `upsert(onConflict="id", ignoreDuplicates=true)`. Reintentar no produce efectos nuevos.
2. **`services/workout-sync.ts:71-90` — condición de carrera.** Lee la cola, procesa y sobrescribe con `failed`. Lo que se encoló durante la sincronización se pierde. **Kotlin:** outbox en Room, se borra por fila; worker único con `ExistingWorkPolicy.APPEND_OR_REPLACE`.
3. **`hooks/use-offline-sync.ts:15,31` — solo sincroniza en la transición offline→online.** `wasOfflineRef` arranca en `false`, así que la cola pendiente al abrir la app online nunca se sube. **Kotlin:** se encola trabajo al finalizar y al iniciar sesión o abrir la app, con restricción `NetworkType.CONNECTED`.
4. **`hooks/use-offline-sync.ts:36` — invalida una clave de caché equivocada.** Usa `['workout-sessions']` cuando la real es `['sessions','list']`, así que el historial no se refresca. **Kotlin:** el historial observa `pendingCount` y vuelve a cargar cuando baja.
5. **Cola de pendientes sin usuario.** En `workout-sync.ts` la cola no se asocia al usuario; tras cambiar de cuenta, la RLS la rechaza para siempre. **Kotlin:** outbox con `userId`; solo se sincroniza el del usuario actual; los fallos permanentes se marcan `FAILED` y se muestran con opción de reintentar o descartar.
6. **`stores/workout-store.ts:39-60` + `routines/[id].tsx:58-65` — iniciar pisa el entrenamiento en curso.** Además no hay forma de volver al entrenamiento tras cerrar la app. **Kotlin:** `StartWorkoutUseCase` devuelve `ActiveWorkoutExists` → diálogo "Continuar / Descartar y empezar". El dashboard muestra un banner "Entrenamiento en curso".
7. **`routines/[id].tsx:63` — rutinas con días arrancan con TODOS los ejercicios de todos los días.** **Kotlin:** hoja de selección de día (preselecciona el día de hoy por nombre) más la opción "Sin día asignado" si hay ejercicios huérfanos.
8. **`components/workout/set-row.tsx:20-21` + `workout/[routineId].tsx:262-265` (`key={i}`) — valores de otro ejercicio.** El estado local de los inputs se reutiliza al cambiar de ejercicio y muestra valores del anterior. **Kotlin:** un solo estado en el ViewModel, con claves por `set.id`.
9. **`workout/[routineId].tsx:115-116` y `:74` — decimales con coma.** `parseFloat("72,5")` da 72 y el teclado es-AR usa coma. **Kotlin:** `WeightInputParser` acepta `,` y `.`.
10. **`components/workout/rest-timer.tsx:33-36` — el descanso no se reinicia.** Si se completa otra serie con igual duración mientras corre el timer, no vuelve a empezar. El estado del timer no se persiste y en segundo plano no hay aviso. **Kotlin:** `restEndsAt` persistido en Room y reiniciado en cada serie completada; alarma con notificación "Descanso terminado".
11. **`workout/[routineId].tsx:93-95` — se guarda una sesión vacía** cuando hay 0 series. **Kotlin:** con 0 series completadas solo se ofrece "Descartar".
12. **`services/workouts.ts:126-148` — "último entrenamiento" contaminado.** Incluye sesiones no completadas y series de calentamiento, y agrupa por igualdad de `started_at`. **Kotlin:** filtra `status=completed` y `is_warmup=false` y agrupa por `session_id`.

### Historial y progreso

13. **`history/[id].tsx:185-194` + `services/workouts.ts:119` — la serie agregada queda con fecha de hoy.** Tiene `completed_at = now`, se ubica mal en el progreso y altera `achieved_at` del PR. **Kotlin:** `completed_at = session.completedAt ?: startedAt`; `set_number = max + 1`.
14. **`services/progress.ts:21-22` — el gráfico muestra lo viejo.** `order asc + limit(30)` devuelve las 30 series **más viejas**, y además el gráfico dibuja por serie (`exercise-progress-chart.tsx:30-38`). **Kotlin:** orden desc, límite 500, agregación por sesión (mejor e1RM), últimas 12 sesiones en orden ascendente.
15. **`services/progress.ts:28-30` — la semana empieza el domingo.** Además la tarjeta "ÚLTIMA SESIÓN" muestra "✓" si hay sesiones en la semana (`(tabs)/index.tsx:88-92`), lo que no dice nada. **Kotlin:** semana lunes-domingo en la zona local; "ÚLTIMA SESIÓN" muestra fecha relativa.
16. **`components/exercises/exercise-gif.tsx:28` + `app/exercise/[id].tsx:58` — el detalle nunca muestra el video.** `gif_url` es .mp4 y se pasa a `Image`, así que siempre aparece el placeholder. **Kotlin:** `ExerciseMedia` usa ExoPlayer para .mp4/.webm/.m3u8, Coil con decoder GIF para imágenes, y cae a `image_url`.

### Rutinas

17. **`components/routines/exercise-list-item.tsx:33-40` — "Mover a día" no hace nada.** `handleSave` nunca envía `day_number`. **Kotlin:** el patch incluye `dayNumber`.
18. **`components/routines/sortable-exercise-list.tsx:176-185` — lista desactualizada tras editar.** Solo se resincroniza cuando cambian los ids, así que las ediciones de sets/reps no se ven. **Kotlin:** la lista se deriva del estado del ViewModel.
19. **`services/routines.ts:131-139` — reordenar ignora errores.** N updates en paralelo con errores descartados dejan un orden parcial silencioso. **Kotlin:** updates secuenciales; ante el primer error se aborta y se refresca desde el servidor.
20. **`routines/add-exercise.tsx:91` — orden y día mal asignados.** `sort_order` = total de la rutina y no se envía `day_number`. **Kotlin:** `AddExerciseRoute(routineId, dayNumber)`; `sort_order` = máximo del día + 1.
21. **`services/sharing.ts` — compartir e importar:**
    - `:3-6` código con `Math.random`. **Kotlin:** `SecureRandom`.
    - `:8-17` crea un registro nuevo en cada "compartir". **Kotlin:** reutiliza el share activo; reintenta ante colisión 23505.
    - `:19-28` ignora `expires_at`. **Kotlin:** lo valida.
    - `:30-77` importación no transaccional. **Kotlin:** si falla, borra la rutina creada (el cascade limpia ejercicios y días).
22. **`services/templates.ts:52-96` — adoptar plantilla no es transaccional** y deja rutinas a medias. **Kotlin:** compensación: borra las rutinas creadas si falla.

### Autenticación y privacidad

23. **`app/auth/callback.tsx:13,39,41,44` y `app/_layout.tsx:128-134` — se loguea el código de autorización PKCE y las URLs de deep link.** **Kotlin:** `Logger` no-op en release más `-assumenosideeffects` para `Log`; nunca se loguean URLs ni tokens.
24. **`app/auth/callback.tsx:33-36` — el upsert de `profiles` siempre falla.** No hay política INSERT de RLS y el error se ignora. **Kotlin:** `update(display_name)` con `eq("id", uid)` y error registrado sin PII.
25. **`lib/auth.tsx:264` — `prompt: 'consent'` + `access_type: 'offline'`** fuerzan la pantalla de consentimiento en cada login. **Kotlin:** no se envían.
26. **`lib/auth.tsx:275-278` + `app/_layout.tsx:47-51` — el cierre de sesión no limpia datos.** El caché de React Query (persistido en AsyncStorage en texto plano) y el store del workout sobreviven, así que el siguiente usuario del dispositivo ve datos del anterior. **Kotlin:** `SignOutUseCase` limpia Room, las preferencias por usuario y la alarma, y avisa si hay entrenamientos sin sincronizar.

### Perfil

27. **`(tabs)/profile.tsx`:**
    - `:36-40` edad aproximada (365.25 días) y parseo de fecha en UTC, que corre un día. **Kotlin:** `Period.between(LocalDate)`.
    - `:169-183` sin validación (fechas imposibles, pesos absurdos), con errores silenciosos. **Kotlin:** validación y mensajes.
    - `:67-87` los errores de conteo se ignoran.
    - `:305-321` opciones sin acción. **Kotlin:** se reemplazan por "Unidad de peso".

### Exportación

28. **`services/workout-export.ts:77,294,407` — HTML sin escapar.** Nombres de rutinas importadas o compartidas se insertan tal cual. `:515-529` la "historia" se exporta como PDF. Siempre dice "kg". **Kotlin:** dibujo nativo, JPEG 1080x1920 y unidad según preferencia.

### Menores

29. **Textos y ordenamiento:**
    - `routines/index.tsx:565` dice "12 programas" y `create.tsx` dice "18 programas", ambos fijos en el código. En vivo hay 18. **Kotlin:** el conteo sale de los datos.
    - `routines/index.tsx:474` pasa el código a mayúsculas, lo que rompe los códigos hex en minúscula del trigger. **Kotlin:** `ShareCode.parse` normaliza según el formato.
    - `workout/[routineId].tsx:355` usa `colors.surfaceVariant`, que no existe.

---

## 8. Hallazgos del backend: ítems SEPARADOS que requieren aprobación del usuario

**No se implementan en este plan. El cliente Kotlin funciona sin ellos.**

**Decisión del usuario (2026-09-25): no modificar el backend.** Se revisó y aprobó una corrección para B1, B2 (paso 1) y B6 — migraciones, edge function reforzada y rollback, todo listo para aplicar en `supabase/_proposed/` — pero el usuario decidió no desplegarla por ahora. El backend en vivo sigue exactamente como está: RLS con roles `{public}` en las políticas de lectura compartida, `shared_insert_own`/`shared_update_own` sin chequeo de dueño de la rutina, `parse-routine-image` en su v10 (confía en `user_id` del body, usa la *service role key*, sin cuota de IA). Ver `supabase/_proposed/README.md` para el detalle de la corrección y cómo aplicarla si se reconsidera.

- **B1 (CRÍTICO) — `supabase/functions/parse-routine-image/index.ts:16-25,114-120`.** Confía en `user_id` del body y usa la *service role key*. Cualquier llamador con la anon key (que es pública) puede crear rutinas en la cuenta de otro usuario.
  - Arreglo: obtener el usuario del header `Authorization` con `supabase.auth.getUser(jwt)` e ignorar `user_id`.
  - También: validar `exercise_id` contra la lista (líneas 127-152, hoy es parcial y no transaccional) y no devolver el texto de error del gateway (98-101).
  - El cliente Kotlin sigue enviando `user_id` por compatibilidad (la función en vivo lo necesita).
- **B2 (ALTO, verificado en vivo con rol `anon`) — datos compartidos legibles sin login.** Sin iniciar sesión se pueden leer **todos** los `shared_routines` activos (9, códigos enumerables) y las rutinas vinculadas (4 `routines`, 33 `routine_exercises`, 3 `routine_days`).
  - Origen: `02_rls.sql:445-447` (`shared_select_by_code USING (is_active)`) y una política en vivo sobre `routines`/`routine_exercises`/`routine_days` que no está en el repo.
  - Hallazgo adicional (2026-09-25): `shared_insert_own` y `shared_update_own` solo comprueban `auth.uid() = shared_by`, no que la rutina compartida sea del usuario. Cualquier usuario logueado puede crear (o reapuntar) un share hacia una rutina ajena y luego leerla con la política `_shared`.
  - Arreglo: restringir `TO authenticated` y exponer RPCs `SECURITY DEFINER` `get_shared_routine(p_code)` e `import_shared_routine(p_code)` (esta última transaccional); exigir dueño de la rutina en `shared_insert_own`/`shared_update_own`.
- **B3 (MEDIO) — `01_schema.sql:174-203` — los PR no se recalculan.** El trigger solo corre en INSERT; editar o borrar series o sesiones deja PR obsoletos o inexistentes. Arreglo: recalcular por `(user_id, exercise_id)` en UPDATE/DELETE.
- **B4 (BAJO) — funciones sin `search_path`.** `handle_new_user` y `update_personal_record` son `SECURITY DEFINER` sin `SET search_path = public` (advertencia del advisor de Supabase).
  - Bug relacionado (detectado el 2026-09-25): el trigger `public.generate_share_code()` tiene `search_path` vacío pero llama a `gen_random_bytes` sin calificar el esquema. Falla si `share_code` llega `NULL` (la app RN siempre lo manda, así que hoy no se dispara, pero cualquier insert que lo omita rompe). Arreglo: calificar como `extensions.gen_random_bytes` (o el esquema donde viva `pgcrypto` en vivo).
- **B5 (MANTENIMIENTO) — `database/*.sql` no refleja la base en vivo.** Faltan tablas de plantillas y días y la política DELETE de `workout_sets` (commit b72fe44). Sugerencia: `supabase db pull`.
- **B6 (MEDIO) — sin límite de uso en la función de IA.** No hay rate limit por usuario ni tope de tamaño de imagen (abuso de costo).

---

## 9. Diseño detallado

### 9.1 Estructura de paquetes

Raíz: `E:\Spoter Kotlin\app\src\main\java\com\lucho314\spotter\`.

```
SpotterApp.kt                  @HiltAndroidApp; Configuration.Provider(HiltWorkerFactory); crea canales de notificación
MainActivity.kt                @AndroidEntryPoint; splash; handleDeeplinks(auth); DeepLinkParser(import); setContent{ SpotterRoot() }
core/common/                   AppResult.kt, AppError.kt, ValidationReason.kt, Dispatchers.kt (@IoDispatcher), TimeProvider.kt, IdGenerator.kt, Logger.kt
core/config/AppConfig.kt       data class AppConfig(supabaseUrl, supabaseAnonKey, googleWebClientId: String?) { val isValid }
core/designsystem/theme/       Color.kt, Type.kt, Shape.kt, Spacing.kt, Theme.kt
core/designsystem/component/   SpotterButton (Primary gradiente/Secondary/Ghost; sm 40dp, md 48dp, lg 56dp), SpotterCard, SpotterTextField, StatCard,
                               NumberStepper, ConfirmDialog, LoadingState, ErrorState(retry), EmptyState, ExerciseMedia, LineChart, SectionHeader, Chip
core/network/                  SupabaseModule.kt, SafeCall.kt (safeCall + ErrorMapper), NetworkMonitor.kt
core/security/                 AeadProvider.kt (interface + TinkAeadProvider), EncryptedSessionManager.kt, EncryptedCodeVerifierCache.kt
core/database/                 SpotterDatabase.kt, DatabaseModule.kt, entity/*.kt, dao/*.kt
core/datastore/                DataStoreModule.kt (datastores "secure_auth" y "user_prefs")
core/navigation/               Routes.kt, SpotterNavHost.kt, TopLevelDestination.kt, DeepLink.kt (DeepLinkParser)
core/notifications/            NotificationChannels.kt, RestTimerAlarmScheduler.kt, RestTimerReceiver.kt
core/work/                     SyncWorkoutsWorker.kt, SyncScheduler.kt
domain/model/                  modelos puros (ver 9.3)
domain/repository/             interfaces (ver 9.4)
domain/usecase/                use cases (ver 9.5)
domain/calc/                   WorkoutMath, WeightConverter, WeightInputParser, WeekRange, AgeCalculator, RoutineOrdering, SpanishWeekdays,
                               ExerciseProgressAggregator, NumberFormatter, Validators
data/remote/dto/Dtos.kt
data/remote/datasource/        interfaces *RemoteDataSource + Supabase*RemoteDataSource
data/mapper/                   *Mapper.kt (DTO↔dominio, entidad↔dominio)
data/repository/               *RepositoryImpl.kt + RepositoryModule.kt (@Binds)
data/export/                   WorkoutExportDataBuilder.kt (puro), WorkoutPdfRenderer.kt, WorkoutStoryRenderer.kt, ExportFileWriter.kt
data/image/ImageEncoder.kt     reducir (≤1600px), rotar por EXIF, JPEG 80, base64 NO_WRAP, tope 4 MB
feature/auth/                  LoginScreen, LoginViewModel, GoogleCredentialClient, NonceGenerator, OnboardingScreen, OnboardingViewModel
feature/root/                  SpotterRoot.kt, RootViewModel.kt, ConfigErrorScreen.kt
feature/dashboard/ feature/routines/{list,detail,edit,addexercise}/ feature/templates/{list,detail}/
feature/importroutine/{code,image}/ feature/workout/ feature/history/{list,detail,share}/ feature/progress/ feature/profile/ feature/exercise/
```

### 9.2 Errores y resultados

```kotlin
sealed interface AppError {
  data object Network : AppError
  data object Unauthorized : AppError
  data object NotFound : AppError
  data class Conflict(val detail: String? = null) : AppError
  data class Validation(val reason: ValidationReason) : AppError
  data class Server(val code: String? = null, val message: String? = null) : AppError
  data class Unknown(val cause: Throwable? = null) : AppError
}
sealed interface AppResult<out T> {
  data class Success<T>(val value: T) : AppResult<T>
  data class Failure(val error: AppError) : AppResult<Nothing>
}
inline fun <T, R> AppResult<T>.map(f: (T) -> R): AppResult<R>
inline fun <T> AppResult<T>.onSuccess(f: (T) -> Unit): AppResult<T>
inline fun <T> AppResult<T>.onFailure(f: (AppError) -> Unit): AppResult<T>
suspend fun <T> safeCall(block: suspend () -> T): AppResult<T>   // catch CancellationException -> rethrow; catch Throwable -> Failure(ErrorMapper.map(t))

enum class ValidationReason { NAME_EMPTY, NAME_TOO_LONG, DESCRIPTION_TOO_LONG, DAYS_PER_WEEK_RANGE, SETS_RANGE, REPS_RANGE, REST_RANGE,
  WEIGHT_INVALID, WEIGHT_RANGE, BODY_WEIGHT_RANGE, HEIGHT_RANGE, BIRTH_DATE_INVALID, SHARE_CODE_INVALID, IMAGE_TOO_LARGE, IMAGE_UNREADABLE,
  NO_EXERCISES, EXERCISE_ALREADY_IN_ROUTINE, DAY_ALREADY_EXISTS }
```

**`ErrorMapper.map(t)`:**

| Excepción | `AppError` |
|---|---|
| `java.io.IOException`, `io.github.jan.supabase.exceptions.HttpRequestException`, `io.ktor.client.plugins.HttpRequestTimeoutException` | `Network` |
| `UnauthorizedRestException`, `io.github.jan.supabase.auth.exception.AuthRestException` | `Unauthorized` |
| `NotFoundRestException` | `NotFound` |
| `PostgrestRestException` con code `"23505"` | `Conflict` |
| `PostgrestRestException` con code `"PGRST116"` | `NotFound` |
| `PostgrestRestException` con code `"42501"` | `Server("42501")` |
| otra `RestException` | `Server(code, error)` |
| `SerializationException` | `Server("decode")` |
| resto | `Unknown` |

La UI traduce `AppError` a textos con `AppError.toMessageRes(): Int`, en `feature/common/ErrorMessages.kt`.

### 9.3 Modelos de dominio (`domain/model/`)

```kotlin
enum class WeightUnit { KG, LB }
enum class Equipment(val apiValue: String) { BARBELL("barbell"), DUMBBELL("dumbbell"), MACHINE("machine"), CABLE("cable"),
  BODYWEIGHT("bodyweight"), KETTLEBELL("kettlebell"), BAND("band"), OTHER("other"); companion object { fun fromApi(v: String?): Equipment } } // desconocido -> OTHER
enum class Difficulty { BEGINNER, INTERMEDIATE, ADVANCED }         // fromApi nullable
enum class ExerciseCategory { COMPOUND, ISOLATION, CARDIO, STRETCH, PLYOMETRIC }
enum class TemplateGoal(val apiValue: String) { STRENGTH("strength"), HYPERTROPHY("hypertrophy"), FAT_LOSS("fat_loss"), GENERAL("general") }
enum class ProfileGoal(val apiValue: String) { GAIN_MUSCLE("gain_muscle"), LOSE_WEIGHT("lose_weight"), MAINTAIN("maintain"), IMPROVE_PERFORMANCE("improve_performance"), OTHER("other") }
data class MuscleGroup(val id: Int, val name: String, val nameEn: String)
data class Exercise(val id: Int, val name: String, val nameEn: String, val muscleGroup: MuscleGroup?, val equipment: Equipment,
  val imageUrl: String?, val mediaUrl: String?, val secondaryMuscles: List<String>, val instructions: List<String>,
  val difficulty: Difficulty?, val category: ExerciseCategory?)
data class RoutineDay(val id: String, val routineId: String, val dayNumber: Int, val name: String)
data class RoutineExercise(val id: String, val routineId: String, val exerciseId: Int, val exercise: Exercise?, val sortOrder: Int,
  val dayNumber: Int?, val targetSets: Int, val targetReps: Int, val restSeconds: Int)
data class RoutineSummary(val id: String, val name: String, val description: String?, val daysPerWeek: Int?, val exerciseCount: Int,
  val days: List<RoutineDay>, val createdAt: Instant)
data class RoutineDetail(val id: String, val userId: String, val name: String, val description: String?, val daysPerWeek: Int?,
  val isArchived: Boolean, val days: List<RoutineDay>, val exercises: List<RoutineExercise>) {
  fun exercisesForDay(dayNumber: Int): List<RoutineExercise>          // ordenados
  val unassignedExercises: List<RoutineExercise>                       // dayNumber nulo o sin fila en days
}
data class RoutineInput(val name: String, val description: String?, val daysPerWeek: Int?)
data class NewRoutineExercise(val exerciseId: Int, val dayNumber: Int?, val targetSets: Int, val targetReps: Int, val restSeconds: Int)
data class RoutineExercisePatch(val targetSets: Int, val targetReps: Int, val restSeconds: Int, val dayNumber: Int?)
data class WorkoutSet(val id: String, val sessionId: String, val exerciseId: Int, val exerciseName: String?, val setNumber: Int,
  val weightKg: Double, val reps: Int, val rpe: Double?, val isWarmup: Boolean, val completedAt: Instant)
data class WorkoutSessionSummary(val id: String, val routineId: String?, val routineName: String?, val startedAt: Instant, val completedAt: Instant?)
data class WorkoutSessionDetail(val id: String, val routineName: String?, val startedAt: Instant, val completedAt: Instant?, val notes: String?, val sets: List<WorkoutSet>)
data class PersonalRecord(val id: String, val exerciseId: Int, val exerciseName: String?, val bestWeightKg: Double, val bestRepsAtWeight: Int,
  val estimated1RmKg: Double, val achievedAt: Instant, val updatedAt: Instant)
data class LastExerciseSession(val sessionId: String, val date: Instant, val sets: List<WorkoutSet>)
data class ExerciseProgressPoint(val sessionId: String, val date: Instant, val bestE1RmKg: Double, val topWeightKg: Double, val volumeKg: Double)
data class Profile(val id: String, val displayName: String, val avatarUrl: String?, val weightKg: Double?, val heightCm: Int?,
  val birthDate: LocalDate?, val goal: ProfileGoal?)
data class ProfileStats(val totalSessions: Int, val totalPrs: Int, val totalRoutines: Int)
data class DashboardStats(val sessionsThisWeek: Int, val lastSessionAt: Instant?, val latestPr: PersonalRecord?, val pendingSyncCount: Int)
data class RoutineTemplateSummary(val id: String, val name: String, val description: String?, val goal: TemplateGoal, val difficulty: Difficulty, val daysPerWeek: Int)
data class TemplateExercise(val exerciseId: Int, val exercise: Exercise?, val sortOrder: Int, val targetSets: Int, val targetReps: Int, val restSeconds: Int)
data class TemplateDay(val id: String, val dayNumber: Int, val name: String, val description: String?, val exercises: List<TemplateExercise>)
data class TemplateDetail(val summary: RoutineTemplateSummary, val days: List<TemplateDay>)
@JvmInline value class ShareCode private constructor(val value: String) {
  companion object {
    private val CLIENT = Regex("^[A-HJ-NP-Z2-9]{8}$"); private val HEX = Regex("^[0-9a-f]{12}$")
    fun parse(raw: String?): ShareCode?   // trim; si en mayúsculas coincide con CLIENT -> mayúsculas; si en minúsculas coincide con HEX -> minúsculas; si no, null
    fun generate(random: java.security.SecureRandom): ShareCode // alfabeto "ABCDEFGHJKLMNPQRSTUVWXYZ23456789", 8 caracteres
  }
}
data class SharedRoutinePreview(val code: ShareCode, val routineName: String, val exerciseCount: Int, val dayCount: Int)
data class AuthUser(val id: String, val email: String?, val displayName: String, val avatarUrl: String?)
sealed interface AuthState { data object Loading : AuthState; data object SignedOut : AuthState; data class SignedIn(val user: AuthUser) : AuthState }
// Entrenamiento activo
data class ActiveWorkout(val sessionId: String, val userId: String, val routineId: String?, val routineName: String, val dayName: String?,
  val startedAt: Instant, val weightUnit: WeightUnit, val currentExerciseIndex: Int, val rest: RestTimer?, val exercises: List<ActiveExercise>) {
  val completedSetCount: Int
}
data class ActiveExercise(val rowId: Long, val position: Int, val exerciseId: Int, val name: String, val equipment: Equipment,
  val mediaUrl: String?, val imageUrl: String?, val targetSets: Int, val targetReps: Int, val restSeconds: Int, val sets: List<ActiveSet>)
data class ActiveSet(val id: String, val setNumber: Int, val weightText: String, val repsText: String, val isWarmup: Boolean, val completedAt: Instant?) {
  val isCompleted get() = completedAt != null
}
data class RestTimer(val endsAt: Instant, val totalSeconds: Int) { fun remainingSeconds(now: Instant): Int /* >=0, redondeo hacia arriba */ }
data class PendingWorkout(val id: String, val userId: String, val routineId: String?, val startedAt: Instant, val completedAt: Instant,
  val notes: String?, val sets: List<PendingSet>, val status: PendingStatus, val lastError: String?)
data class PendingSet(val id: String, val exerciseId: Int, val setNumber: Int, val weightKg: Double, val reps: Int, val isWarmup: Boolean, val completedAt: Instant)
enum class PendingStatus { PENDING, FAILED }
```

### 9.4 Interfaces de repositorio (`domain/repository/`)

```kotlin
interface AuthRepository {
  val authState: Flow<AuthState>
  fun currentUser(): AuthUser?
  suspend fun signInWithGoogleIdToken(idToken: String, rawNonce: String): AppResult<Unit>
  suspend fun startGoogleOAuth(): AppResult<Unit>         // abre el navegador; la sesión llega por handleDeeplinks
  suspend fun syncProfileDisplayName(user: AuthUser): AppResult<Unit>
  suspend fun signOut(): AppResult<Unit>                  // si la red falla, SignOutScope.LOCAL
}
interface ExerciseRepository {
  fun observeCatalog(): Flow<List<Exercise>>; fun observeMuscleGroups(): Flow<List<MuscleGroup>>
  suspend fun refreshCatalog(force: Boolean = false): AppResult<Unit>   // TTL 24 h
  suspend fun getExercise(id: Int): AppResult<Exercise>                 // primero caché, después red
}
interface RoutineRepository {
  fun observeRoutines(userId: String): Flow<List<RoutineSummary>>; suspend fun refreshRoutines(userId: String): AppResult<Unit>
  fun observeArchivedRoutines(userId: String): Flow<List<RoutineSummary>>; suspend fun refreshArchived(userId: String): AppResult<Unit>
  fun observeRoutine(routineId: String): Flow<RoutineDetail?>; suspend fun refreshRoutine(routineId: String): AppResult<Unit>
  suspend fun createRoutine(userId: String, input: RoutineInput, sourceTemplateId: String? = null): AppResult<String>
  suspend fun updateRoutine(routineId: String, input: RoutineInput): AppResult<Unit>
  suspend fun setArchived(userId: String, routineId: String, archived: Boolean): AppResult<Unit>
  suspend fun deleteRoutine(routineId: String): AppResult<Unit>          // solo para compensaciones
  suspend fun addExercise(routineId: String, input: NewRoutineExercise, sortOrder: Int): AppResult<Unit>
  suspend fun addExercises(routineId: String, items: List<Pair<NewRoutineExercise, Int>>): AppResult<Unit> // insert en lote
  suspend fun updateRoutineExercise(routineId: String, routineExerciseId: String, patch: RoutineExercisePatch): AppResult<Unit>
  suspend fun removeExercise(routineId: String, routineExerciseId: String): AppResult<Unit>
  suspend fun reorderExercises(routineId: String, orderedIds: List<String>): AppResult<Unit>
  suspend fun addDays(routineId: String, days: List<Pair<Int, String>>): AppResult<Unit>
  suspend fun renameDay(routineId: String, dayId: String, name: String): AppResult<Unit>
  suspend fun deleteDay(routineId: String, dayId: String): AppResult<Unit>
}
interface TemplateRepository {
  suspend fun getTemplates(goal: TemplateGoal?, daysPerWeek: Int?): AppResult<List<RoutineTemplateSummary>>
  suspend fun getTemplate(id: String): AppResult<TemplateDetail>
}
interface WorkoutHistoryRepository {
  suspend fun getSessions(userId: String, page: Int, pageSize: Int = 30): AppResult<List<WorkoutSessionSummary>>
  suspend fun getSession(sessionId: String): AppResult<WorkoutSessionDetail>
  suspend fun updateSet(setId: String, weightKg: Double, reps: Int): AppResult<Unit>
  suspend fun addSet(sessionId: String, exerciseId: Int, setNumber: Int, weightKg: Double, reps: Int, completedAt: Instant): AppResult<Unit>
  suspend fun deleteSet(setId: String): AppResult<Unit>
  suspend fun deleteSession(sessionId: String): AppResult<Unit>       // count exacto; 0 -> Server("not_deleted")
  suspend fun getLastSession(userId: String, exerciseId: Int): AppResult<LastExerciseSession?>
  suspend fun getCompletedSince(userId: String, since: Instant): AppResult<Int>
  suspend fun getLastCompletedAt(userId: String): AppResult<Instant?>
  suspend fun countCompleted(userId: String): AppResult<Int>
}
interface ProgressRepository {
  suspend fun getPersonalRecords(userId: String): AppResult<List<PersonalRecord>>   // orden estimated_1rm desc
  suspend fun getLatestPersonalRecord(userId: String): AppResult<PersonalRecord?>   // orden updated_at desc, limit 1
  suspend fun countPersonalRecords(userId: String): AppResult<Int>
  suspend fun getExerciseSets(userId: String, exerciseId: Int, limit: Int = 500): AppResult<List<WorkoutSet>>
}
interface ProfileRepository {
  suspend fun getProfile(userId: String): AppResult<Profile>
  suspend fun updatePhysical(userId: String, weightKg: Double?, heightCm: Int?, birthDate: LocalDate?, goal: ProfileGoal?): AppResult<Unit>
  suspend fun countActiveRoutines(userId: String): AppResult<Int>
}
interface SharingRepository {
  suspend fun findActiveShare(routineId: String, userId: String): AppResult<ShareCode?>
  suspend fun createShare(routineId: String, userId: String, code: ShareCode): AppResult<ShareCode>
  suspend fun getSharedRoutine(code: ShareCode): AppResult<Pair<SharedRoutineDto /*o modelo interno*/, Instant?>?>  // ver 9.5 ImportSharedRoutineUseCase
}
interface AiImportRepository { suspend fun importFromImage(userId: String, base64Jpeg: String): AppResult<Pair<String, String>> } // (routineId, routineName)
interface ActiveWorkoutRepository {
  fun observeActive(userId: String): Flow<ActiveWorkout?>
  suspend fun getActive(userId: String): ActiveWorkout?
  suspend fun start(workout: ActiveWorkout)
  suspend fun updateSetInputs(setId: String, weightText: String, repsText: String)
  suspend fun setCompleted(setId: String, completedAt: Instant?)
  suspend fun addSet(exerciseRowId: Long, setId: String, weightText: String, repsText: String)
  suspend fun setCurrentExercise(sessionId: String, index: Int)
  suspend fun setRestTimer(sessionId: String, rest: RestTimer?)
  suspend fun discard(sessionId: String)
  suspend fun moveToOutbox(sessionId: String, pending: PendingWorkout)   // una sola transacción Room
}
interface PendingWorkoutRepository {
  fun observeCount(userId: String): Flow<Int>; fun observeFailed(userId: String): Flow<List<PendingWorkout>>
  suspend fun getPending(userId: String): List<PendingWorkout>
  suspend fun upload(workout: PendingWorkout): AppResult<Unit>     // upsert idempotente de sesión y sets
  suspend fun delete(id: String); suspend fun markFailed(id: String, error: String); suspend fun resetToPending(id: String)
  suspend fun recordAttempt(id: String, error: String)
}
interface PreferencesRepository {
  val weightUnit: Flow<WeightUnit>; suspend fun setWeightUnit(unit: WeightUnit)
  fun isOnboardingDone(userId: String): Flow<Boolean>; suspend fun setOnboardingDone(userId: String)
  suspend fun clearUserScoped()
}
```

`SharingRepository.getSharedRoutine` puede devolver un modelo interno propio en lugar de `SharedRoutineDto`, como `SharedRoutineContent(name, description, daysPerWeek, exercises, days, expiresAt)` en `domain/model`. **Recomendado usar el modelo de dominio**; el dominio no debe ver DTOs.

### 9.5 Use cases (`domain/usecase/`)

Todos son clases con `@Inject constructor` y `suspend operator fun invoke(...)`.

- **`StartWorkoutUseCase(routineRepo, activeRepo, prefs, idGen, time)`:** `invoke(userId, routineId, day: DaySelection, replaceExisting: Boolean): AppResult<StartResult>`.
  - `sealed interface DaySelection { data class Day(val dayNumber: Int); data object Unassigned; data object All }`. `All` solo aplica si la rutina no tiene días.
  - `sealed interface StartResult { data class Started(val sessionId: String); data class ActiveWorkoutExists(val existing: ActiveWorkout) }`.
  - Arma una instantánea desde la rutina en caché: nombre y media del ejercicio, target, descanso; `targetSets` sets con `repsText = targetReps.toString()` y `weightText = ""`.
  - Si no hay ejercicios → `Validation(NO_EXERCISES)`.
- **`UpdateSetInputUseCase`:** actualización de texto; solo persiste, no valida.
- **`ToggleSetCompletionUseCase(activeRepo, alarmScheduler, time)`:** `invoke(workout, exerciseRowId, setId): AppResult<Unit>`.
  - Al completar valida con `WeightInputParser` y `Validators.reps`. En `BODYWEIGHT` el peso vacío vale 0.
  - Setea `completedAt = now`, `rest = RestTimer(now + restSeconds, restSeconds)` y agenda la alarma (siempre reinicia).
  - Al descompletar limpia `completedAt`; el timer no se toca.
- **`FinishWorkoutUseCase(activeRepo, syncScheduler, alarmScheduler, time)`:** `invoke(userId, sessionId): AppResult<FinishResult>`.
  - `sealed interface FinishResult { data object Saved; data object NothingToSave }`.
  - Solo toma sets completados; convierte a kg según `workout.weightUnit` (redondeo a 2 decimales); `set.id` se reutiliza como id del `workout_set`.
  - Pasa todo al outbox, cancela la alarma y llama a `syncScheduler.schedule()`.
- **`DiscardWorkoutUseCase`:** descarta la sesión y cancela la alarma.
- **`SyncPendingWorkoutsUseCase(auth, pendingRepo)`:** devuelve `SyncOutcome { Done, RetryLater, NoUser }`.
  - Por cada pendiente del usuario actual, con estado `PENDING`, llama a `upload`.
  - `Success` → `delete`.
  - `Network`, `Unauthorized` o `Server` con código nulo o 5xx → `recordAttempt` y `RetryLater`.
  - Otros (`Conflict` no esperado, 42501, 23503, 23514) → `markFailed`.
- **`ShareRoutineUseCase(sharingRepo, random)`:** reutiliza el share activo si existe; si no, genera un código y crea; ante `Conflict` reintenta hasta 3 veces.
- **`ImportSharedRoutineUseCase(sharingRepo, routineRepo, time)`:**
  - `preview(code): AppResult<SharedRoutinePreview>`: `NotFound` si no existe, está inactivo o `expiresAt < now`.
  - `import(code, userId): AppResult<String>`: crea la rutina con nombre `"$name (importada)"`, descripción y días por semana; luego días; luego ejercicios (`dayNumber` se preserva).
  - Si falla un paso posterior a crear la rutina, llama a `routineRepo.deleteRoutine(newId)` y devuelve el error original.
- **`AdoptTemplateUseCase(templateRepo, routineRepo)`:**
  - Por cada día crea la rutina `"${template.name} - ${day.name}"` con `description`, `daysPerWeek = template.daysPerWeek` y `sourceTemplateId`, más sus ejercicios.
  - Si falla, borra las creadas.
  - Devuelve `AppResult<List<String>>`.
- **`ImportRoutineFromImageUseCase(aiRepo)`:** valida `base64.length <= 4_000_000` (si no, `IMAGE_TOO_LARGE`) y llama.
- **`GetDashboardStatsUseCase(history, progress, pending, time, zone)`:** llamadas en paralelo con `coroutineScope { async }`. Semana desde `WeekRange.currentWeekStart`.
- **`GetExerciseProgressUseCase`:** `ProgressRepository.getExerciseSets` → `ExerciseProgressAggregator.aggregate(sets, maxSessions = 12)`.
- **`GetProfileOverviewUseCase`:** perfil más conteos.
- **`UpdateProfileUseCase`:**
  - Peso 20..400 kg (la entrada viene en la unidad preferida y se convierte).
  - Altura 100..250.
  - Nacimiento parseado de `dd/MM/yyyy` con `ResolverStyle.STRICT`; edad 10..100; no futura.
- **`SignOutUseCase(auth, db, prefs, alarmScheduler, workManager)`:**
  - `pendingCount(userId): Int` para el aviso previo.
  - `invoke()`: cancela la alarma, cancela el trabajo único, llama a `auth.signOut()`, luego `database.clearAllTables()` en IO y `prefs.clearUserScoped()`.
  - La unidad de peso se conserva: es preferencia del dispositivo.
- **`BuildWorkoutExportUseCase`:** `WorkoutSessionDetail` + unidad → `WorkoutExportData` (puro, en `data/export/WorkoutExportDataBuilder` o `domain/calc`).

### 9.6 Cálculos puros (`domain/calc/`), todos con tests

- **`WorkoutMath`:**
  - `epley1Rm(weightKg, reps) = weightKg * (1 + reps / 30.0)`, igual que el trigger de la base.
  - `volumeKg(sets, includeWarmups = false)`.
  - `durationMinutes(start, end): Long?`.
  - `formatDuration(min): String`: `"45 min"`, `"1h 5min"`, o `"—"` si es null.
  - `topSet(sets)`: máximo peso; empate → máximo reps.
- **`WeightConverter`:**
  - `KG_PER_LB = 0.45359237`.
  - `fromKg(kg, unit)`.
  - `toKg(value, unit)` redondeado HALF_UP a 2 decimales.
  - `format(kg, unit)`: sin decimales finales ("80", "72.5").
- **`WeightInputParser`:**
  - `parseWeight(text): Double?`: trim, reemplaza `,` por `.`, `toDoubleOrNull`; rechaza negativos, no finitos, más de 2 decimales y más de 1000 kg equivalentes.
  - `parseReps(text): Int?` en 1..200.
- **`WeekRange.currentWeekStart(now, zone)`:** lunes 00:00 local.
- **`AgeCalculator.age(birth, today)`.**
- **`RoutineOrdering`:**
  - Compara días por `dayNumber`, `name`, `id`.
  - Compara ejercicios por `dayNumber` (nulos al final), `sortOrder`, `exerciseId`, `id`.
  - `nextSortOrder(exercises, dayNumber)` y `nextDayNumber(days)`.
- **`SpanishWeekdays`:**
  - `ALL = ["Lunes","Martes","Miércoles","Jueves","Viernes","Sábado","Domingo"]`.
  - `order(name)`: 1..7, 99 si no es día de semana.
  - `abbr`: Lun, Mar, Mié, Jue, Vie, Sáb, Dom.
  - `of(DayOfWeek)`.
  - `sortRoutinesByFirstWeekday(list)`.
- **`ExerciseProgressAggregator`:** agrupa por `sessionId` y fecha de sesión (mínimo `completedAt` del grupo); calcula mejor e1RM, peso tope y volumen; orden ascendente; conserva las últimas N.
- **`NumberFormatter`:** miles con "." (es-AR) y `formatVolume`.
- **`Validators`:**
  - `routineInput`: nombre trim 1..50, descripción ≤200, días 1..7.
  - `routineExercise`: sets 1..20, reps 1..100, descanso 15..600.
  - `workoutSetKg`: 0..1000.
  - Todos devuelven `ValidationReason?`.

### 9.7 Room (`core/database/`), versión 1, `exportSchema = true`

**Entidades** (nombres de columnas en snake_case con `@ColumnInfo`):

- `CachedPayloadEntity(key: String PK, userId: String, json: String, updatedAtEpochMs: Long)`.
  - Claves: `routines:active`, `routines:archived`, `routine:{id}`, `exercises:catalog`, `muscle_groups`.
  - El catálogo usa `userId = ""`.
- `ActiveSessionEntity(id PK, userId, routineId?, routineName, dayName?, startedAtEpochMs, weightUnit: String, currentExerciseIndex: Int, restEndsAtEpochMs: Long?, restTotalSeconds: Int?)`, con índice en `user_id`.
- `ActiveExerciseEntity(id Long autoGenerate PK, sessionId FK→active_session CASCADE, position, exerciseId, name, equipment, mediaUrl?, imageUrl?, targetSets, targetReps, restSeconds)`, con índice en `session_id`.
- `ActiveSetEntity(id String PK, activeExerciseId FK→active_exercise CASCADE, setNumber, weightText, repsText, isWarmup, completedAtEpochMs: Long?)`, con índice.
- `PendingWorkoutEntity(id PK, userId, routineId?, startedAt: String ISO, completedAt: String ISO, notes?, status: String, attempts: Int, lastError: String?, createdAtEpochMs)`.
- `PendingWorkoutSetEntity(id PK, workoutId FK CASCADE, exerciseId, setNumber, weightKg: Double, reps, isWarmup, completedAt: String)`.
- Relaciones: `ActiveSessionWithExercises` y `ActiveExerciseWithSets` con `@Relation`; `PendingWorkoutWithSets`.

**DAOs:**

- **`CachedPayloadDao`:** `observe(key, userId)`, `get(key, userId)`, `@Upsert upsert`, `delete(key)`, `deleteByPrefix(prefix)`.
- **`ActiveWorkoutDao`:**
  - `@Transaction observeByUser(userId): Flow<ActiveSessionWithExercises?>` y `getByUser`.
  - `insertSession`, `insertExercise(): Long`, `insertSets`.
  - Queries `@Query UPDATE` para entradas, completado, índice actual y descanso.
  - `deleteSession(id)`.
  - `@Transaction insertFull(session, exercisesWithSets)`.
- **`PendingWorkoutDao`:**
  - `observeCount(userId)` (`PENDING` + `FAILED`), `observeFailed(userId)`.
  - `@Transaction getPending(userId)` (solo `PENDING`).
  - `insertWorkout`, `insertSets`, `delete`, `markFailed`, `resetToPending`, `recordAttempt`.

La transacción de finalizar va con `database.withTransaction { pendingDao.insert...; activeDao.deleteSession(id) }`.

**Configuración:** `room { schemaDirectory("$projectDir/schemas") }` y `ksp("room-compiler")`.

### 9.8 Seguridad

**`TinkAeadProvider`** implementa `interface AeadProvider { fun aead(): com.google.crypto.tink.Aead }` con carga perezosa:

```kotlin
AeadConfig.register()
AndroidKeysetManager.Builder()
  .withSharedPref(context, "spotter_tink_keyset", "spotter_tink_prefs")
  .withKeyTemplate(KeyTemplates.get("AES256_GCM"))
  .withMasterKeyUri("android-keystore://spotter_master_key")
  .build()
  .keysetHandle
  .getPrimitive(RegistryConfiguration.get(), Aead::class.java)
```

**`EncryptedSessionManager(store: DataStore<Preferences>, aeadProvider, json) : SessionManager`:**

- `saveSession`: `Base64(aead.encrypt(json.encodeToString(session).toByteArray(), AD))` con `AD = "spotter.session".toByteArray()`.
- `loadSessionOrNull`: descifra. Si hay `GeneralSecurityException`, `IllegalArgumentException` o `SerializationException`, borra la clave y devuelve null.
- `loadSession`: `loadSessionOrNull() ?: throw IllegalStateException("No session")`.
- `deleteSession`: borra la clave.

**`EncryptedCodeVerifierCache`:** mismo esquema, clave `pkce_verifier`, `AD = "spotter.pkce"`.

**Manifest:**

- `android:allowBackup="false"` y `android:fullBackupContent="false"`.
- `android:dataExtractionRules="@xml/data_extraction_rules"`, que excluye todo en cloud-backup y device-transfer.
- `android:usesCleartextTraffic="false"`.
- `android:networkSecurityConfig="@xml/network_security_config"`: `base-config cleartextTrafficPermitted="false"` con trust-anchors solo `system`. **Sin pinning:** Supabase rota certificados y el pinning rompería la app.

**Deep links** (en `MainActivity`, `launchMode="singleTask"`):

- `onCreate` y `onNewIntent` → `DeepLinkParser.parse(intent.dataString)`.
- `DeepLink.AuthCallback`: solo si `scheme == "spotter"`, `host == "auth"` y `path == "/callback"`. Se pasa a `supabase.handleDeeplinks(intent, onError = { logger.w("auth callback failed") })`.
- `DeepLink.ImportRoutine(code)`: solo si `host == "import"`, hay un único segmento y `ShareCode.parse` no es null. Se entrega a `RootViewModel.onDeepLink()`, que lo guarda hasta tener sesión.
- La importación **siempre** exige confirmación explícita del usuario.
- Cualquier otro deep link se ignora.

**Otros:**

- Se usan `LocalActivity`/`LocalContext`; la receiver y el `FileProvider` no son exportados.
- `Logger`: `interface Logger { d, w, e }`. `AndroidLogger` solo loguea si `BuildConfig.DEBUG`, nunca recibe tokens y `e()` en release no hace nada.
- supabase-kt: `defaultLogLevel = if (DEBUG) LogLevel.WARNING else LogLevel.NONE`.

**`AppConfig` inválido** (URL vacía o que no empieza con `https://`): `MainActivity` muestra `ConfigErrorScreen` y no toca el cliente. Por eso `MainActivity` inyecta `dagger.Lazy<SupabaseClient>`.

**Build:** se lee `local.properties` y, si falta, `System.getenv`. Para release se registra la tarea `verifyReleaseConfig`, que falla si `SUPABASE_URL` o `SUPABASE_ANON_KEY` están vacíos; `preReleaseBuild` depende de ella.

**Anon key en el APK:** es extraíble y es pública por diseño. La protección real es la RLS, por eso importan B1 y B2.

### 9.9 Cliente Supabase (`core/network/SupabaseModule.kt`)

```kotlin
@Provides @Singleton fun supabase(config: AppConfig, sm: EncryptedSessionManager, cv: EncryptedCodeVerifierCache, json: Json): SupabaseClient =
  createSupabaseClient(config.supabaseUrl, config.supabaseAnonKey) {
    defaultSerializer = KotlinXSerializer(json)
    requestTimeout = 60.seconds            // la edge function de IA tarda varios segundos
    defaultLogLevel = if (BuildConfig.DEBUG) LogLevel.WARNING else LogLevel.NONE
    install(Auth) { flowType = FlowType.PKCE; scheme = "spotter"; host = "auth"; sessionManager = sm; codeVerifierCache = cv
                    alwaysAutoRefresh = true; autoLoadFromStorage = true }
    install(Postgrest); install(Functions)
  }
@Provides fun auth(c: SupabaseClient) = c.auth ; postgrest(c) = c.postgrest ; functions(c) = c.functions
```

**Mapeo de `authState`:**

| `SessionStatus` | `AuthState` |
|---|---|
| `Initializing` | `Loading` |
| `Authenticated` | `SignedIn(user)` |
| `NotAuthenticated` | `SignedOut` |
| `RefreshFailure` | `SignedIn` con el usuario de `currentUserOrNull()` si existe; si no, `Loading` |

**`RefreshFailure` significa error de red: no se debe expulsar al usuario offline.**

`AuthUser.displayName` sale de `userMetadata["full_name"] ?: ["name"] ?: ["display_name"] ?: email.substringBefore("@") ?: "Atleta"`. Esa lógica va en la función pura `buildDisplayName(metadata: JsonObject?, email: String?)`.

**Flujo de Google:**

1. `LoginViewModel.onGoogleClick()`: si `AppConfig.googleWebClientId` no está vacío, genera `rawNonce` (32 bytes de `SecureRandom` en hex) y `hashedNonce` (SHA-256 hex) y emite `LoginEvent.LaunchCredentialRequest(hashedNonce)`.
2. La pantalla llama a `GoogleCredentialClient.getIdToken(activity, serverClientId, hashedNonce)`, que usa `CredentialManager.getCredential` con `GetGoogleIdOption(filterByAuthorizedAccounts = false, autoSelectEnabled = false, nonce)`, y responde con `vm.onCredentialResult(result)`.
3. `sealed interface GoogleIdResult { Success(idToken); Cancelled; NoCredential; Failure(msg) }`.
4. `Success` → `authRepo.signInWithGoogleIdToken(idToken, rawNonce)`. `NoCredential` o `Failure` → `authRepo.startGoogleOAuth()` (respaldo PKCE). `Cancelled` → no hace nada.
5. Si `GOOGLE_WEB_CLIENT_ID` está vacío se usa PKCE directamente: `auth.signInWith(Google, redirectUrl = "spotter://auth/callback")`, sin `prompt`/`access_type`.

Después de `SignedIn`, `RootViewModel` llama una vez por sesión a `authRepo.syncProfileDisplayName(user)`, que es un update, no un upsert.

### 9.10 Navegación (`core/navigation/Routes.kt`)

```kotlin
@Serializable data object DashboardRoute; @Serializable data object RoutinesRoute; @Serializable data object HistoryRoute
@Serializable data object ProgressRoute; @Serializable data object ProfileRoute
@Serializable data class RoutineDetailRoute(val routineId: String)
@Serializable data class RoutineEditRoute(val routineId: String? = null)       // null = crear
@Serializable data class AddExerciseRoute(val routineId: String, val dayNumber: Int? = null)
@Serializable data object ArchivedRoutinesRoute
@Serializable data object TemplatesRoute; @Serializable data class TemplateDetailRoute(val templateId: String)
@Serializable data class ImportCodeRoute(val code: String); @Serializable data object ImportImageRoute
@Serializable data object WorkoutRoute
@Serializable data class SessionDetailRoute(val sessionId: String)
@Serializable data class ExerciseDetailRoute(val exerciseId: Int)
@Serializable data object OnboardingRoute
```

`SpotterRoot` decide según `RootUiState`:

| Estado | Pantalla |
|---|---|
| `Loading` | splash (`installSplashScreen().setKeepOnScreenCondition`) |
| `ConfigError` | `ConfigErrorScreen` |
| `SignedOut` | `LoginScreen` |
| `SignedIn(needsOnboarding)` | `SpotterNavHost` con start `OnboardingRoute` o `DashboardRoute` |

- La barra inferior (Inicio, Rutinas, Historial, Progreso, Perfil) se muestra si el destino actual es top-level.
- Al tocar una pestaña: `popUpTo(start) { saveState = true }; launchSingleTop; restoreState`. Tocar "Rutinas" vuelve siempre a la lista, como en el RN.
- Un código de importación pendiente → `navigate(ImportCodeRoute(code))` una sola vez; luego `consume()`.

### 9.11 Tema (`core/designsystem/theme/`), tomado de `E:\Spotter\constants/`

**Tokens de color** (`object SpotterColors`):

| Token | Valor |
|---|---|
| background | #0E0E0E |
| surface | #0E0E0E |
| surfaceLowest | #000000 |
| surfaceLow | #131313 |
| surfaceContainer | #1A1A1A |
| surfaceHigh | #20201F |
| surfaceHighest | #262626 |
| surfaceBright | #2C2C2C |
| primary | #F4FFC6 |
| primaryContainer | #D1FC00 |
| primaryDim | #C7EF00 |
| onPrimary | #546600 |
| secondary | #00E3FD |
| secondaryContainer | #006875 |
| error | #FF7351 |
| onSurface | #FFFFFF |
| onSurfaceVariant | #ADAAAA |
| outline | #767575 |
| outlineVariant | #484847 |

Colores de objetivo de plantilla: fuerza #FF7351, hipertrofia = secondary, quema de grasa #FCDC43, general = primaryContainer.

**`darkColorScheme` de Material 3** (solo tema oscuro, igual que `userInterfaceStyle: dark`):

- `primary=primaryContainer(#D1FC00)`, `onPrimary=#546600`, `primaryContainer=#D1FC00`, `onPrimaryContainer=#546600`.
- `secondary=#00E3FD`, `onSecondary=#000000`, `secondaryContainer=#006875`.
- `background/surface=#0E0E0E`, `surfaceContainerLowest=#000000`, `surfaceContainerLow=#131313`, `surfaceContainer=#1A1A1A`, `surfaceContainerHigh=#20201F`, `surfaceContainerHighest=#262626`, `surfaceBright=#2C2C2C`.
- `onSurface=#FFFFFF`, `onSurfaceVariant=#ADAAAA`, `outline=#767575`, `outlineVariant=#484847`, `error=#FF7351`.

El botón primario usa un gradiente horizontal `#F4FFC6 → #C7EF00` con texto `#546600`.

**Tipografía** (`FontFamily` desde `res/font`):

| Estilo M3 | Fuente | Peso | Tamaño / interlineado | Tracking |
|---|---|---|---|---|
| displayLarge | Space Grotesk | Bold | 57/64 | -0.25 |
| displayMedium | Space Grotesk | Bold | 45/52 | |
| headlineLarge | Space Grotesk | SemiBold | 32/40 | |
| headlineMedium | Space Grotesk | SemiBold | 28/36 | |
| headlineSmall | Space Grotesk | SemiBold | 24/32 | |
| titleLarge | Inter | SemiBold | 22/28 | |
| titleMedium | Inter | SemiBold | 16/24 | 0.15 |
| titleSmall | Inter | Medium | 14/20 | 0.1 |
| bodyLarge | Inter | Regular | 16/24 | 0.5 |
| bodyMedium | Inter | Regular | 14/20 | 0.25 |
| labelLarge | Inter | Medium | 14/20 | 0.1 |
| labelMedium | Inter | Medium | 12/16 | 0.5 |
| labelSmall | Inter | Medium | 11/16 | 0.5 |

**Fuentes:** copiar desde `E:\Spotter\node_modules\@expo-google-fonts\...` a `app/src/main/res/font/`:

- `space_grotesk_regular.ttf`, `space_grotesk_medium.ttf`, `space_grotesk_semibold.ttf`, `space_grotesk_bold.ttf`
- `inter_regular.ttf`, `inter_medium.ttf`, `inter_semibold.ttf`

**Espaciado, radios y recursos:**

- Espaciado (`object Spacing`): 4, 8, 12, 16, 20, 24, 32, 40, 48, 64 dp.
- Radios: 12 (inputs), 16 (cards), 20 (cards de sesión y chips), 24 (botones y hojas).
- Iconos: `material-icons-extended`.
  - home → `Outlined.Home`; barbell → `FitnessCenter`; time → `History`; trending-up → `AutoMirrored.Outlined.TrendingUp`; person → `Person`; share → `Share`; add-circle → `AddCircleOutline`.
  - trash → `Delete`; pencil → `Edit`; sparkles → `AutoAwesome`; download → `Download`; trophy → `EmojiEvents`; check → `CheckCircle`; reorder → `DragHandle`.
  - camera → `PhotoCamera`; images → `PhotoLibrary`; close → `Close`; back → `AutoMirrored.ArrowBack`; chevron → `AutoMirrored.KeyboardArrowRight`; logout → `AutoMirrored.Logout`; skip → `SkipNext`.
  - Logo de Google: vector propio `res/drawable/ic_google.xml`.
- Ícono de la app: copiar `E:\Spotter\android\app\src\main\res\mipmap-*` (incluye `mipmap-anydpi-v26`) y definir `@color/iconBackground=#0E0E0E`.
- Sonido: `E:\Spotter\assets\sounds\beep.wav` → `res/raw/beep.wav`.

**Etiquetas en español:**

- Equipamiento: barbell "Barra", dumbbell "Mancuernas", machine "Máquina", cable "Polea", bodyweight "Peso corporal", kettlebell "Pesa rusa", band "Banda", other "Otro".
- Dificultad: Principiante / Intermedio / Avanzado.
- Categoría: Compuesto / Aislamiento / Cardio / Estiramiento / Pliométrico.
- Objetivos de plantilla: Fuerza / Hipertrofia / Quema Grasa / General.
- Objetivos de perfil: Ganar músculo / Perder peso / Mantener peso / Mejorar rendimiento / Otro.

---

## 10. Pasos por fase

**Cada fase termina con `./gradlew assembleDebug testDebugUnitTest` en verde.** Rutas relativas a `E:\Spoter Kotlin\`. Paquete base `com/lucho314/spotter` = `app/src/main/java/com/lucho314/spotter/` (tests en `app/src/test/java/com/lucho314/spotter/`).

### FASE 1: Scaffold, build, DI, cliente Supabase, sesión segura, autenticación y esqueleto de navegación

1. **Configuración del proyecto:** `.gitignore` (`.gradle/`, `build/`, `/local.properties`, `/keystore.properties`, `*.jks`, `*.keystore`, `.idea/`, `.kotlin/`, `*.apk`, `*.aab`, `captures/`), `local.properties.example` (claves vacías con comentarios), wrapper (sección 2), `settings.gradle.kts` (repos google/mavenCentral, `rootProject.name = "SpotterKotlin"`, `include(":app")`), `build.gradle.kts` raíz (plugins con `apply false`), `gradle.properties` (`org.gradle.jvmargs=-Xmx4g -Dfile.encoding=UTF-8`, `android.useAndroidX=true`, `android.nonTransitiveRClass=true`, `kotlin.code.style=official`) y `gradle/libs.versions.toml` (sección 4).
2. **`app/build.gradle.kts`:**
   - Plugins: `android-application`, `kotlin-compose`, `kotlin-serialization`, `ksp`, `hilt`, `room`.
   - `android { namespace; compileSdk = 36; defaultConfig { minSdk 26; targetSdk 36; versionCode 2; versionName "2.0.0"; buildConfigField("String","SUPABASE_URL", ...); SUPABASE_ANON_KEY; GOOGLE_WEB_CLIENT_ID; testInstrumentationRunner } }`.
   - `buildTypes`: debug con `applicationIdSuffix ".debug"`; release con `isMinifyEnabled = true`, `isShrinkResources = true` y proguard.
   - `compileOptions` 17; `buildFeatures { compose = true; buildConfig = true }`.
   - `testOptions.unitTests { isIncludeAndroidResources = true; isReturnDefaultValues = true }`.
   - `packaging.resources.excludes += listOf("/META-INF/{AL2.0,LGPL2.1}", "META-INF/INDEX.LIST", "META-INF/io.netty.versions.properties")`.
   - `room { schemaDirectory("$projectDir/schemas") }`; dependencias de la sección 4 (BOMs con `platform(...)`, `ksp(...)` para los compiladores de Hilt y Room).
   - Tarea `verifyReleaseConfig`.
3. **`AndroidManifest.xml`:**
   - Permisos `INTERNET`, `ACCESS_NETWORK_STATE`, `POST_NOTIFICATIONS`, `SCHEDULE_EXACT_ALARM`.
   - `application`: `name=.SpotterApp`, flags de backup y cleartext (9.8), tema `Theme.Spotter.Splash` (core-splashscreen, fondo #0E0E0E).
   - `MainActivity`: `exported=true`, `singleTask`, `windowSoftInputMode=adjustResize`, filtros LAUNCHER y VIEW/DEFAULT/BROWSABLE con `<data android:scheme="spotter" android:host="auth"/>` y `<data android:scheme="spotter" android:host="import"/>` en filtros separados.
   - `provider androidx.startup.InitializationProvider` con `<meta-data android:name="androidx.work.WorkManagerInitializer" tools:node="remove"/>`.
   - `FileProvider` `${applicationId}.fileprovider` no exportado.
   - Recursos: `res/xml/network_security_config.xml`, `data_extraction_rules.xml`, `file_paths.xml` (`<cache-path name="exports" path="exports/"/>` y `<cache-path name="camera" path="camera/"/>`), fuentes, mipmaps, `values/colors.xml`, `values/themes.xml`, `values/strings.xml`.
4. **`core/common/*`, `core/config/AppConfig.kt`** (lo provee `ConfigModule` desde `BuildConfig`) y `core/designsystem/theme/*` más componentes básicos (`SpotterButton`, `SpotterCard`, `SpotterTextField`, `LoadingState`, `ErrorState`, `EmptyState`, `ConfirmDialog`).
5. **Seguridad y red:** `core/security/*` (9.8), `core/datastore/DataStoreModule.kt` (`@Named("secure_auth")` y `@Named("user_prefs")`), `core/network/SupabaseModule.kt`, `SafeCall.kt`, `NetworkMonitor.kt` (`callbackFlow` con `ConnectivityManager.registerDefaultNetworkCallback`, `distinctUntilChanged`).
6. **Autenticación:**
   - `domain/model/AuthModels.kt`, `domain/repository/AuthRepository.kt`, `data/repository/AuthRepositoryImpl.kt`, `data/mapper/AuthUserMapper.kt` (`buildDisplayName`).
   - `feature/auth/NonceGenerator.kt`, `GoogleCredentialClient.kt`, `LoginViewModel.kt`, `LoginScreen.kt` (logo "SPOTTER" en displayMedium con letterSpacing 8, subtítulo "Tu diario de entrenamiento", botón "Continuar con Google" / "Conectando...", error como snackbar).
   - `OnboardingScreen/ViewModel` ("BIENVENIDO/A", nombre, texto de bienvenida del RN, botón "Empezar" → `setOnboardingDone` → Dashboard).
7. **Preferencias:** `domain/repository/PreferencesRepository.kt` y `data/repository/PreferencesRepositoryImpl.kt` (DataStore `user_prefs`: `weight_unit`, `onboarding_done_<userId>`).
8. **Raíz y navegación:**
   - `core/navigation/DeepLink.kt` (`sealed interface DeepLink { data object AuthCallback; data class ImportRoutine(val code: ShareCode) }`, `object DeepLinkParser { fun parse(raw: String?): DeepLink? }` con `java.net.URI`) y `domain/model/ShareCode.kt`.
   - `feature/root/RootViewModel.kt` (combina `authState` con el onboarding; deep link pendiente; `SyncScheduler.schedule()` al firmar, lo que se vuelve efectivo en la fase 4), `SpotterRoot.kt`, `core/navigation/Routes.kt`, `SpotterNavHost.kt` con destinos top-level de placeholder ("Próximamente") y `ProfileScreen` mínimo con "Cerrar sesión" (con confirmación).
   - `MainActivity.kt`, `SpotterApp.kt`.
9. **Estilos de código:** evitar `!!`; `collectAsStateWithLifecycle`; los ViewModels exponen `StateFlow` con `stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), initial)`.

**Tests de la fase 1:**

- `core/common/SafeCallTest`: IOException → Network, SerializationException → Server("decode"), RuntimeException → Unknown, CancellationException relanzada.
- `core/security/EncryptedSessionManagerTest`: DataStore sobre archivo temporal con `PreferenceDataStoreFactory.create(scope = TestScope(...)) { tmp }` y `FakeAead` (XOR reversible más AD). Casos: guardar y cargar; borrar; basura en el valor → null y clave borrada.
- `data/mapper/AuthUserMapperTest`: orden de precedencia del display name.
- `feature/auth/NonceGeneratorTest`: SHA-256 hex conocido.
- `feature/auth/LoginViewModelTest` (Turbine, `FakeAuthRepository`, `UnconfinedTestDispatcher` con `Dispatchers.setMain`): con client id emite `LaunchCredentialRequest`; `Success` llama a signIn con el nonce crudo; `NoCredential` hace fallback a OAuth; `Cancelled` no hace nada; error → estado de error.
- `feature/root/RootViewModelTest`: estados de auth, onboarding, deep link pendiente consumido una sola vez.
- `core/navigation/DeepLinkParserTest`: válidos, host o scheme incorrectos, código inválido, múltiples segmentos, query de callback.
- `domain/model/ShareCodeTest`: parse y generate (alfabeto y largo).
- Utilidad `MainDispatcherRule` en `app/src/test/.../testutil/`.

**Aceptación de la fase 1:**

- `assembleDebug` y `testDebugUnitTest` en verde.
- La app instalada muestra el login.
- Con PKCE vuelve por `spotter://auth/callback` y muestra la barra inferior; cerrar sesión vuelve al login.
- `grep -ri "supabase.co\|eyJ" app/src` no encuentra nada (sin secretos en el código).

### FASE 2: Capa de datos y dominio

1. `data/remote/dto/Dtos.kt` (sección 6) y `data/mapper/*`: `ExerciseMapper`, `RoutineMapper`, `WorkoutMapper`, `ProgressMapper`, `ProfileMapper`, `TemplateMapper`, `ActiveWorkoutEntityMapper`, `PendingWorkoutMapper`, con `Instant`/`LocalDate` como en la sección 6.
2. **Data sources:** interfaces y `Supabase*RemoteDataSource` en `data/remote/datasource/`: `ExerciseRemoteDataSource`, `RoutineRemoteDataSource`, `TemplateRemoteDataSource`, `WorkoutRemoteDataSource` (historial, última sesión, series de progreso, conteos, `uploadWorkout`), `ProgressRemoteDataSource`, `ProfileRemoteDataSource`, `SharingRemoteDataSource`, `AiImportRemoteDataSource`. Todas las llamadas pasan por `safeCall` en el repositorio y no en el data source, así los fakes lanzan excepciones simples.
3. **Room:** `core/database/SpotterDatabase.kt`, `entity/*`, `dao/*` (9.7), `DatabaseModule.kt` (nombre `spotter.db`; `fallbackToDestructiveMigration(dropAllTables = true)` **solo** en debug; en release, migraciones explícitas cuando haya v2).
4. **Repositorios** en `data/repository/*Impl.kt` más `RepositoryModule` con `@Binds`. `RoutineRepositoryImpl` y `ExerciseRepositoryImpl` implementan el patrón *cache-then-network*: `observe*` lee `CachedPayloadDao` y decodifica un `@Serializable` interno de caché, que es el propio DTO; `refresh*` trae de la red y hace upsert en el caché. Cada mutación exitosa hace `refreshRoutine(id)` y `refreshRoutines(userId)`.
5. **Dominio:** `domain/model/*`, `domain/repository/*`, `domain/calc/*` (9.6). `ActiveWorkoutRepositoryImpl` y `PendingWorkoutRepositoryImpl`, con `upload`: `upsert` de la sesión y luego `upsert` de la lista de sets, ambos `ignoreDuplicates`.
   - Los use cases de 9.5 **se difieren a la fase que primero los necesita** (ninguno de los tests de la fase 2 los ejercita; sí aparecen en las listas de tests de las fases 3-6): `AdoptTemplateUseCase` → fase 3; `StartWorkoutUseCase`, `UpdateSetInputUseCase`, `ToggleSetCompletionUseCase`, `FinishWorkoutUseCase`, `DiscardWorkoutUseCase`, `SyncPendingWorkoutsUseCase` → fase 4; `GetDashboardStatsUseCase`, `GetExerciseProgressUseCase`, `GetProfileOverviewUseCase`, `UpdateProfileUseCase`, `SignOutUseCase` → fase 5; `ShareRoutineUseCase`, `ImportSharedRoutineUseCase`, `ImportRoutineFromImageUseCase`, `BuildWorkoutExportUseCase` → fase 6.
6. `core/common/TimeProvider` (`fun now(): Instant`, `fun zone(): ZoneId`) e `IdGenerator` (`fun uuid(): String`), con fakes en `testutil/`.

**Tests de la fase 2:**

- Cálculos: `domain/calc/WorkoutMathTest`, `WeightConverterTest` (100 lb → 45.36 kg; ida y vuelta), `WeightInputParserTest` ("72,5", " 80 ", "-1", "abc", "1e3", "72.555"), `WeekRangeTest` (domingo 23:59 y lunes 00:00 en America/Argentina/Buenos_Aires), `AgeCalculatorTest` (día anterior y del cumpleaños, 29/02), `RoutineOrderingTest`, `SpanishWeekdaysTest`, `ExerciseProgressAggregatorTest`, `NumberFormatterTest`, `ValidatorsTest`.
- `data/remote/dto/DtoDecodingTest`: JSON reales de la sección 6, por ejemplo la fila de exercises con `gif_url` .mp4, `secondary_muscles: []` y `difficulty: null`; claves desconocidas ignoradas; `explicitNulls=false` omite nulos en inserts.
- `data/mapper/*MapperTest`: parseo de `timestamptz` con `+00:00` y microsegundos, `Equipment` desconocido → OTHER.
- `data/repository/RoutineRepositoryImplTest`: `FakeRoutineRemoteDataSource` + `FakeCachedPayloadDao`, o Room en memoria con Robolectric. Casos: refresh llena el caché; error de red deja el caché; `reorderExercises` se detiene ante el primer error.
- `core/database/CachedPayloadDaoTest`, `ActiveWorkoutDaoTest` (cascadas, observe, transacción `insertFull`) y `PendingWorkoutDaoTest`. Todos con `@RunWith(RobolectricTestRunner::class)`, `@Config(sdk = [35])` y `Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), SpotterDatabase::class.java).allowMainThreadQueries().build()`.
- `data/repository/PreferencesRepositoryImplTest`.

**Aceptación de la fase 2:** compila, todos los tests pasan y existe `app/schemas/.../1.json`.

### FASE 3: UI de rutinas, plantillas, catálogo y detalle de ejercicio

1. **`feature/routines/list/`:** `RoutinesViewModel` y `RoutinesScreen`.
   - Header "Mis Rutinas" con acciones: importar código (barra con `TextField` en mayúsculas, placeholder "Código (ej: K7MN3QXP)", valida con `ShareCode.parse` y navega a `ImportCodeRoute`), IA (a `ImportImageRoute`, pantalla de la fase 6), crear (a `RoutineEditRoute()`).
   - Tarjeta "Explorar plantillas" con badge "NUEVO".
   - Lista ordenada por `SpanishWeekdays`; `RoutineCard` con nombre, descripción (2 líneas), "N ejercicios", chips de días abreviados o "Nx / semana".
   - Long-press → archivar (confirmación "¿Archivás "X"? Podés recuperarla después.").
   - Entrada "Archivadas" que abre `ArchivedRoutinesRoute` con "Restaurar".
   - Estados vacío y error; pull-to-refresh con `PullToRefreshBox`.
2. **`feature/routines/edit/`:** `RoutineEditViewModel`/`Screen`. Crear (con banner "Usar una plantilla" que muestra el conteo real) y editar. Validación con `Validators`. Al guardar navega al detalle, reemplazando la pantalla de creación.
3. **`feature/routines/detail/`:** `RoutineDetailViewModel`/`Screen`.
   - Top bar con back, nombre, compartir (lo hace la fase 6; aquí queda el botón conectado a un callback no-op **con un TODO explícito en el reporte**, o bien se implementa directamente si se prefiere adelantar el `ShareRoutineUseCase`), "+" (agregar ejercicio al día indicado) y menú (Editar, Archivar).
   - Secciones por día con título editable (hoja con los 7 días de la semana; los usados se deshabilitan) y borrado con advertencia si el día tiene ejercicios.
   - "Sin día asignado"; "Agregar día" / "Organizar por días".
   - Listas reordenables por día con `sh.calvin.reorderable` (`ReorderableItem` + `draggableHandle` + long-press). Al soltar llama a `reorderExercises(routineId, idsDelGrupo)`, que setea `sort_order = índice` solo del grupo; el orden optimista se mantiene hasta el refresh.
   - `ExerciseRow` con nombre (tap → `ExerciseDetailRoute`), "S series × R reps · Ds descanso", editar (hoja con sets/reps/descanso y chips "Mover a día", **enviando `dayNumber`**) y quitar (confirmación).
   - Footer "Iniciar Entrenamiento": en esta fase solo valida que haya ejercicios; el inicio real lo hace la fase 4.
4. **`feature/routines/addexercise/`:** `AddExerciseViewModel`/`Screen`. Buscador (filtro local, insensible a mayúsculas y acentos con `Normalizer`), chips de grupo muscular ("Todos" + grupos), ítems con "grupo · equipamiento en español", ya agregados deshabilitados con check, hoja de configuración con `NumberStepper` (sets mínimo 1; reps mínimo 1; descanso mínimo 15 con paso 15; defaults 3/10/90). Al agregar: `sortOrder = RoutineOrdering.nextSortOrder(exercises, dayNumber)`, snackbar "X agregado" y volver. El error 23505 se muestra como "Ejercicio ya agregado".
5. **`feature/templates/`:** `TemplatesViewModel`/`Screen` (GoalSelector con 4 opciones y icono, DaysSelector 2..6 que se desmarca al volver a tocar, `TemplateCard` con badges de color) y `TemplateDetailViewModel`/`Screen` (badges, descripción, "Estructura del programa", `TemplateDayCard` expandible, tap en ejercicio → detalle, botón "Usar esta rutina" con confirmación "Se crearán N rutinas en tu lista (una por día). ¿Continuar?", `AdoptTemplateUseCase` con el botón deshabilitado mientras corre, luego navega a Rutinas con "¡Rutinas creadas!"). `AdoptTemplateUseCase` (9.5) se implementa recién acá (diferido de la fase 2: sus tests están en esta fase, no en la 2).
6. **`feature/exercise/`:** `ExerciseDetailViewModel`/`Screen` más `core/designsystem/component/ExerciseMedia.kt`.
   - `fun isVideoUrl(url: String) = url.substringBefore('?').lowercase().let { it.endsWith(".mp4") || it.endsWith(".webm") || it.endsWith(".m3u8") }`.
   - Video: `ExoPlayer` recordado en `remember` y liberado en `DisposableEffect`; `repeatMode = REPEAT_MODE_ONE`; `volume = 0f`; `PlayerSurface` de media3-ui-compose; se pausa en `ON_STOP`.
   - Imagen: Coil `AsyncImage` con `ImageLoader` de la app que incluye `AnimatedImageDecoder` (API 28+) o `GifDecoder`, provisto en `SpotterApp` como `SingletonImageLoader.Factory`.
   - Placeholder: icono de pesa sobre `surfaceContainer`.

**Tests de la fase 3:** `RoutinesViewModelTest` (orden, archivar con éxito o error, código inválido sin navegar), `RoutineEditViewModelTest`, `RoutineDetailViewModelTest` (agrupar por día y huérfanos, mover de día envía `dayNumber`, reordenar con error → refresh más mensaje, días deshabilitados), `AddExerciseViewModelTest` (búsqueda con acentos, duplicados, `nextSortOrder` por día), `TemplatesViewModelTest`, `AdoptTemplateUseCaseTest` (compensación borra las rutinas creadas), `ExerciseMediaTest` (`isVideoUrl`).

**Aceptación de la fase 3:** compila, tests verdes y el CRUD completo de rutinas funciona contra Supabase (verificación manual opcional).

**Desviaciones registradas durante la implementación:**

- `AdoptTemplateUseCase(routineRepository)` recibe el `TemplateDetail` ya cargado (no `templateRepository` + `templateId`): la pantalla de detalle de plantilla ya lo tiene en memoria para mostrarlo, así que refetchearlo solo para adoptar sería una llamada de red redundante.
- Las rutinas adoptadas de una plantilla no llevan `routine_days`: sus ejercicios se insertan en `UNASSIGNED_DAY_NUMBER` (no en `1`, que es lo que hacía el RN para toda rutina sin días). Ver el ítem siguiente para por qué `1` es riesgoso.
- Carry-over de rutinas heredadas sin días con ejercicios en `day_number=1`: `RoutineOrdering.suggestedFirstDayNumber` decide que el primer día creado por "Organizar por días"/"Agregar día" reutiliza el número que ya comparten los ejercicios existentes (si hay uno solo) en lugar de `RoutineOrdering.nextDayNumber`, que los trataría como huérfanos a evitar. Documentado en su KDoc.
- `savedStateHandle.toRoute<T>()` (Navigation 2.9.8) no decodifica los argumentos cuando el `SavedStateHandle` no fue poblado por un `NavBackStackEntry` real (se comprobó que devuelve `null` incluso con una entrada simple en el mapa) - los ViewModels de esta fase con argumentos de ruta (`ExerciseDetailViewModel`, `RoutineEditViewModel`, `RoutineDetailViewModel`, `AddExerciseViewModel`, `TemplateDetailViewModel`) leen la clave directamente del `SavedStateHandle` (`savedStateHandle["routineId"]`) en lugar de `toRoute()`, lo que además es más simple de testear.
- `feature/routines/list/ArchivedRoutinesViewModel` (no estaba en el diseño de 9.1, que solo listaba pantallas) se agregó como ViewModel propio en vez de reusar `RoutinesViewModel`: sus acciones (restaurar) y su fuente (`observeArchivedRoutines`) son distintas.
- Item de revisión de FASE 2 resuelto acá de paso (estaba listado para FASE 3/4 en `docs/review_carryover.md`): `ActiveWorkoutRepositoryImpl.start()` ahora relanza si la re-consulta tras `SQLiteConstraintException` no encuentra nada, con test.

**Cambios del ciclo 2 de revisión (2026-09-26):** además de los 14 issues numerados (rename/borrar día y quitar ejercicio ahora piden confirmación; el estado vacío ya no oculta días con la lista de ejercicios vacía; el reordenamiento fallido descarta el orden optimista vía `reorderRevision`; `AdoptTemplateUseCase` compensa también ante cancelación, con `NonCancellable`, y `TemplateDetailScreen` bloquea el back mientras adopta; refresco de la lista de rutinas al volver a la pantalla, con throttle; loading/error separados de forma consistente entre "no hay nada cacheado todavía" (pantalla completa) y "fallo transitorio con datos ya visibles" (snackbar); todos los errores transitorios pasan a eventos one-shot por `Channel` en vez de `StateFlow` persistente, para no perder mensajes idénticos consecutivos; `RoutinesScreen` siempre muestra "Plantillas"/"Archivadas"; snackbars de éxito ("X agregado", "¡Rutinas creadas!") relayados entre pantallas vía `NavBackStackEntry.savedStateHandle`; `daysPerWeek` en la edición de rutina es explícitamente opcional (chips 1..7, tocar de nuevo lo borra); `ExerciseMedia` prioriza un `.gif` animado sobre la imagen estática, cae a la imagen si el video falla, y reanuda en `ON_START`; `NumberStepper` y el menú de opciones tienen `contentDescription`; `TemplatesViewModel` cancela la carga anterior al cambiar de filtro y el spinner ya no oculta los chips), se decidió diferir explícitamente:
  - `SavedStateHandle` para sobrevivir a la muerte de proceso en los campos del formulario de edición y en la búsqueda de agregar ejercicio: nice-to-have, no evaluado como bloqueante para esta fase.

  **Correcciones a afirmaciones de este mismo bloque, hechas en el ciclo 3 (ver más abajo) por ser incorrectas:** se había dicho que `dropUnlessResumed` "no está disponible" en las versiones fijadas del proyecto y que `<plurals>` no hacía falta porque el español no distingue singular/plural para estos casos. Ambas afirmaciones eran falsas: `androidx.lifecycle.compose.dropUnlessResumed` sí existe en `lifecycle-runtime-compose` 2.10.0 (ya era una dependencia del proyecto), y el español sí distingue singular/plural ("1 ejercicio" vs "3 ejercicios"). Las dos quedaron resueltas en el ciclo 3.

**Cambios del ciclo 3 de revisión (2026-09-26):**
- `AddExerciseViewModel`/`RoutineDetailViewModel`: la sección "ya agregado"/`sort_order`/`day_number` para el grupo "sin día asignado" ahora se resuelve sobre `RoutineDetail.unassignedExercises` (el mismo grupo que la UI realmente muestra), no filtrando `dayNumber == X`. Nueva regla compartida `RoutineOrdering.unassignedBucketDayNumber(routine)`: `1` si la rutina no tiene días todavía (rutinas legacy de RN, lista plana), `UNASSIGNED_DAY_NUMBER` (0) si ya tiene algún día real. Esto corrige duplicados visuales y un `sort_order` incorrecto para rutinas legacy con todos sus ejercicios en `day_number=1` sin filas `routine_days`.
- `RoutineExercisePatch` gana un campo `sortOrder: Int?` (threadeado hasta `SupabaseRoutineRemoteDataSource.updateRoutineExercise`, que solo lo envía si no es null): mover un ejercicio a otro día ahora lo agrega al final de ese día (`RoutineOrdering.nextSortOrderIn` del grupo destino) en vez de conservar su `sort_order` viejo, que podía empatar o intercalarse raro con lo que ya había ahí.
- `<plurals>` reales para "N ejercicio(s)" (compartido entre la tarjeta de rutina y la de día de plantilla), "N serie(s)"/"N rep(s)" (compuestos en `feature/common/RoutineExerciseSummary.kt`), "Se creará/crearán N rutina(s)" y el mensaje de borrar día con ejercicios.
- `dropUnlessResumed` real (`androidx.lifecycle.compose`) en todos los `onBack`/`navigate(...)` de `SpotterNavHost.kt` para los callbacks sin argumentos; para los parametrizados (`onRoutineClick: (String) -> Unit`, etc. - `dropUnlessResumed` de la librería solo envuelve `() -> Unit`) se agregaron `dropUnlessResumed1`/`dropUnlessResumed2` locales con la misma semántica (solo ejecutar mientras el `LifecycleOwner` está `RESUMED`).
- Relay de snackbars vía `NavBackStackEntry.savedStateHandle`: ahora se lee con `getStateFlow(key, default).collectAsState()` (observado reactivamente) en vez de `get()` (lectura puntual), y el consumo (`remove(key)`) pasó a ocurrir *antes* de `showSnackbar` en vez de después, para no reproducir el mensaje si el usuario navega fuera y vuelve durante los ~4s que dura.
- `onRoutinesCreated`: `getBackStackEntry(RoutinesRoute)` envuelto en `runCatching`, con fallback a `navigate(RoutinesRoute)` si por algún motivo no está en el back stack.
- El `onDragStopped` del drag & drop de ejercicios ahora compara el orden final contra el orden original del grupo y no llama a `onReorderDayGroup` si no cambió nada (long-press sin mover).
- `RoutineEditScreen`: el botón "Reintentar" del `ErrorState` ahora llama a un nuevo `RoutineEditViewModel.retryLoad()` en vez de `onBack`.
- `RoutinesViewModel.refresh()` (pull-to-refresh, iniciado por el usuario) ahora emite `RoutinesEvent.ActionFailed` si falla con datos ya cacheados en pantalla; `refreshOnResume()` (el refresco silencioso al volver a la pantalla) sigue sin emitir nada ante un fallo. `ArchivedRoutinesViewModel` no tiene pull-to-refresh (su único `refresh()` sale del botón "Reintentar", que solo se muestra sin caché), así que no aplicaba el mismo cambio.
- `AdoptTemplateUseCaseTest`: el test de cancelación ahora cancela un `Job` real (`launch { useCase(...) }` con un fake que se queda esperando un `CompletableDeferred` nunca completado, luego `job.cancel()`), en vez de simular la excepción directamente.

**Cambios del ciclo 4 de revisión (2026-09-26) — bug bloqueante introducido en el ciclo 3:**
- Bug: en `SpotterNavHost.kt`, `dropUnlessResumed`/`dropUnlessResumed1` envolvían callbacks que no son clicks de usuario sino *resultados* de una operación async (`onRoutinesCreated`, `onSaved`, `onExerciseAdded`, `onArchived`, el `onDone` de Onboarding). Esos eventos vienen de un `Channel` consumido por un `LaunchedEffect(Unit) { events.collect {...} }`, que sigue corriendo aunque la pantalla pase a segundo plano (composición sobrevive a `ON_STOP`, solo se cancela al *disponer*). Si el evento llegaba estando la entry por debajo de `RESUMED`, igual se consumía del canal - y el guard de `dropUnlessResumed` en el callback lo descartaba, para siempre. Escenarios reales: adoptar una plantilla de 6 días y apretar Home a mitad de camino → `RoutinesCreated` se pierde, el botón vuelve a habilitarse al volver → un segundo tap crea rutinas duplicadas; guardar una rutina y cambiar de app antes de que vuelva la red → `Saved` se pierde, `saving` queda `true` para siempre.
- Fix: nuevo `feature/common/ObserveAsEvents.kt`, un helper `@Composable` que colecta el `Flow` de eventos con `LocalLifecycleOwner.current.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED)` en vez de un `LaunchedEffect(Unit)` plano - así el `Channel` retiene el evento en su buffer mientras la colección está pausada, y lo entrega recién cuando la pantalla vuelve a `STARTED`. Adoptado en los 6 screens de FASE 3 (`RoutinesScreen`, `ArchivedRoutinesScreen`, `RoutineDetailScreen`, `RoutineEditScreen`, `AddExerciseScreen`, `TemplateDetailScreen`). Los 5 callbacks-resultado en `SpotterNavHost.kt` quedaron sin guard (ya no hace falta: para cuando corren, la colección ya está al menos en `STARTED`); `dropUnlessResumed`/`dropUnlessResumed1`/`dropUnlessResumed2` quedan reservados para clicks de usuario. KDoc agregado tanto en `ObserveAsEvents.kt` como en `SpotterNavHost.kt` explicando la distinción click-guard vs. resultado-de-evento. Cubierto por `ObserveAsEventsBehaviorTest` (ejercita `repeatOnLifecycle` + `Channel` directo contra un `LifecycleRegistry` real, con Robolectric, ya que el proyecto no tiene infraestructura de test de UI de Compose).
- **Desviación deliberada:** `feature/auth/LoginScreen.kt` (FASE 1, ya aprobada) usa un patrón similar (`collectLatest` dentro de un `LaunchedEffect` plano) que en teoría tiene el mismo problema de fondo, pero no fue nombrado en el reporte de este ciclo y tocar código de una fase ya aprobada no estaba en el alcance pedido - se deja fuera a propósito, para que el próximo ciclo/fase lo evalúe explícitamente.
- Carry-over "cheap" resueltos de paso (pedido explícito de este ciclo, marcados en `docs/review_carryover.md`):
  - Snackbar relay: los dos `getStateFlow(...)` de `SpotterNavHost.kt` (`RoutinesRoute`, `RoutineDetailRoute`) ahora están envueltos en `remember(entry) { ... }`, para que una recomposición no le entregue a `collectAsState` una referencia "nueva" del mismo Flow y cancele un `showSnackbar` en curso.
  - `AddExerciseViewModel`: nuevo `AddExerciseUiState.routineLoaded` (gateado por un `routineDetailReady: MutableStateFlow<Boolean>` que se pone en `true` en el primer `onEach` de `RoutineDetail`); "Agregar" queda deshabilitado en `AddExerciseScreen` hasta que sea `true`, y `onConfirmAdd()` se guarda a sí mismo igual (defensa en profundidad). Test nuevo en `AddExerciseViewModelTest` que gatea la primera emisión de `observeRoutine` con un `CompletableDeferred`.

### FASE 4: Entrenamiento activo, timers y sincronización offline

1. **Inicio desde `RoutineDetailScreen`:**
   - Si la rutina tiene días, abre `DayPickerSheet`: días con ejercicios, preselección del nombre de hoy y "Sin día asignado" si aplica.
   - Luego `StartWorkoutUseCase`. Si devuelve `ActiveWorkoutExists`, diálogo "Tenés un entrenamiento en curso (nombre). ¿Continuarlo o empezar uno nuevo?" con Continuar (navega), Descartar y empezar, Cancelar.
   - En API 33+ se pide `POST_NOTIFICATIONS` la primera vez, con `rememberLauncherForActivityResult`. Si se deniega, sigue sin notificación.
2. **`feature/workout/WorkoutViewModel`:**
   - Observa `activeRepo.observeActive(userId)`.
   - Mantiene un `MutableStateFlow<Map<setId, InputDraft>>` de borradores de texto que se superponen al estado de Room. Persiste con `debounce(300)` por set y hace flush inmediato al completar o cambiar de ejercicio y en `onCleared`.
   - Ticker `flow { while(true) { emit(time.now()); delay(1000) } }` para la sesión y el descanso.
   - Cuando el descanso llega a 0 con la app en primer plano: `RestFinished` → beep (`MediaPlayer.create(ctx, R.raw.beep)` liberado al terminar) más háptico, y se limpia el timer.
   - Acciones: `onWeightChange`, `onRepsChange`, `onToggleSet`, `onAddSet` (copia peso y reps de la última serie; si no hay, reps = objetivo), `onSelectExercise(i)`, `onSkipRest`, `onFinish`, `onDiscard`, `onShowLastSession`.
3. **`WorkoutScreen`** (pantalla completa, sin barra inferior; `BackHandler` pide confirmar descarte):
   - Header: cerrar ("Cancelar entrenamiento / ¿Salís sin guardar?"), timer de sesión "mm:ss" / "h:mm:ss", "N series registradas", botón "Finalizar".
   - Puntos de ejercicios tocables.
   - "EJERCICIO i DE n", nombre con botón de historial que abre `LastSessionSheet` ("Último entrenamiento", fecha, tabla # / peso / reps, "Volumen total"; sin red → "Sin conexión"), "S series × R reps", `ExerciseMedia`.
   - `RestTimerCard` ("DESCANSO", segundos en displayLarge color secondary, botón "Saltar").
   - Tabla con encabezado `#`, `KG`/`LB`, `REPS`. `SetRow`: los campos son `OutlinedTextField` con `KeyboardType.Decimal` y `KeyboardType.Number` y clave `set.id`; una fila completada muestra texto y fondo `primaryContainer.copy(alpha = .06f)`; tocar el check alterna.
   - "Agregar serie" con borde punteado. Botones "← Anterior" / "Siguiente →".
   - Errores de validación en snackbar: "Ingresá peso y repeticiones", "Peso inválido".
4. **Finalizar:**
   - Con 0 completadas: diálogo "No registraste ninguna serie. ¿Descartar entrenamiento?" (Descartar / Volver).
   - Si no: "¿Terminaste tu sesión?" → `FinishWorkoutUseCase` → navega a Dashboard con snackbar "¡Entrenamiento completado!" si `NetworkMonitor` está online, o "Guardado sin conexión. Se sincronizará cuando vuelva internet".
5. **Alarma de descanso** en `core/notifications/`:
   - `NotificationChannels.create(context)`: canal `rest_timer`, importancia HIGH, sonido por defecto.
   - `RestTimerAlarmScheduler` (interfaz más `AndroidRestTimerAlarmScheduler`): `schedule(endsAt)` con `AlarmManager.setExactAndAllowWhileIdle(RTC_WAKEUP, …)` si `canScheduleExactAlarms()`; si no, `setAndAllowWhileIdle`; `cancel()`. `PendingIntent` con `FLAG_IMMUTABLE`.
   - `RestTimerReceiver` (no exportado): si `ProcessLifecycleOwner.get().lifecycle.currentState.isAtLeast(STARTED)` no hace nada; si no, y el permiso está concedido, notifica "Descanso terminado" con `contentIntent` a `MainActivity` y extra `open_workout=true`, que `RootViewModel` traduce a navegar a `WorkoutRoute`.
6. **Sincronización:**
   - `core/work/SyncWorkoutsWorker`: `@HiltWorker`, `CoroutineWorker`, usa `SyncPendingWorkoutsUseCase`; `Done`/`NoUser` → `success()`, `RetryLater` → `retry()`.
   - `SyncScheduler.schedule()`: `enqueueUniqueWork("sync_workouts", APPEND_OR_REPLACE, OneTimeWorkRequestBuilder<SyncWorkoutsWorker>().setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()).setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS).build())`.
   - `SpotterApp`: `@Inject lateinit var workerFactory: HiltWorkerFactory` y `override val workManagerConfiguration get() = Configuration.Builder().setWorkerFactory(workerFactory).build()`.
   - `RootViewModel` llama a `schedule()` al pasar a `SignedIn`.
7. **Banner en el dashboard** (placeholder de esta fase): "Entrenamiento en curso · Continuar" cuando `observeActive` no es null. Banner offline global (`NetworkMonitor`): "Sin conexión — los entrenamientos se guardan localmente".
8. Use cases de `domain/usecase/` (9.5) que se implementan en esta fase (diferidos de la fase 2: sus tests están acá, no en la 2): `StartWorkoutUseCase`, `UpdateSetInputUseCase`, `ToggleSetCompletionUseCase`, `FinishWorkoutUseCase`, `DiscardWorkoutUseCase`, `SyncPendingWorkoutsUseCase`.

**Tests de la fase 4:**

- `StartWorkoutUseCaseTest`: filtro por día, instantánea, `NO_EXERCISES`, `ActiveWorkoutExists`, reemplazo.
- `ToggleSetCompletionUseCaseTest`: coma decimal, peso vacío en BODYWEIGHT → 0, peso vacío en BARBELL → error, reinicio del timer en la segunda serie, descompletar.
- `FinishWorkoutUseCaseTest`: conversión lb → kg, solo series completadas, ids reutilizados, vacío → `NothingToSave`, se agenda la sincronización y se cancela la alarma.
- `SyncPendingWorkoutsUseCaseTest`: éxito borra; Network → RetryLater con fila conservada y `attempts` + 1; 42501 → FAILED; filas de otro usuario intactas; subir dos veces no rompe (fake remoto que registra upserts).
- `SyncWorkoutsWorkerTest`: Robolectric, `TestListenableWorkerBuilder<SyncWorkoutsWorker>(context).setWorkerFactory(fakeFactory)`.
- `WorkoutViewModelTest`: restauración tras muerte del proceso (repo con sesión existente → el estado muestra ejercicio, series y timer); debounce con `advanceTimeBy`; descanso que termina emite `RestFinished`.
- `RestTimerTest`: `remainingSeconds`.
- `ActiveWorkoutRepositoryImplTest`: `moveToOutbox` es atómico (Room en memoria).

**Aceptación de la fase 4:**

- Tests verdes.
- **Prueba manual:** iniciar un entrenamiento, completar series, forzar el cierre del proceso (`adb shell am kill com.lucho314.spotter.debug` con la app en segundo plano) y reabrir; el estado se conserva, incluido el descanso.
- En modo avión, finalizar lo deja pendiente; al volver la red se sube una sola sesión.

**Desviaciones registradas durante la implementación:**

- **Banner "Entrenamiento en curso" en `RoutinesScreen`, no en un dashboard.** El paso 7 de esta fase lo describe como "placeholder de esta fase" en el dashboard, pero `feature/dashboard/` no existe todavía (es de la fase 5: `DashboardRoute` sigue siendo `ComingSoonScreen`). `RoutinesViewModel` observa `ActiveWorkoutRepository.observeActive(userId)` y expone `activeWorkoutRoutineName`; `RoutinesScreen` muestra `ActiveWorkoutBanner` cuando no es null, con `onResumeWorkoutClick` navegando a `WorkoutRoute`. Es la pantalla de nivel superior más cercana ya implementada, y la que originalmente inicia el entrenamiento. Cuando la fase 5 tenga un dashboard real, evaluar si el banner queda en ambas pantallas o se muda.
- **`WorkoutScreen.onFinished`/`onDiscarded` hacen `popBackStack()`, no "navega a Dashboard".** Por la misma razón (no hay `DashboardRoute` real todavía): el mensaje de éxito/offline se muestra como snackbar en la propia `WorkoutScreen` antes de volver atrás, en vez de relayarse a una pantalla de dashboard que no tiene `SnackbarHost`. A revisar en la fase 5.
- **`domain/calc/ActiveSetWeight`** (no estaba en la lista explícita de la sección 9.6): factorizado durante la implementación porque `ToggleSetCompletionUseCase` (validar antes de completar) y `FinishWorkoutUseCase` (convertir a kg al finalizar) necesitaban exactamente la misma regla "el peso vacío es inválido, salvo en `BODYWEIGHT`, donde vale 0" - separarlo evita que ambos usos diverjan con el tiempo.
- **Cierre de sesión: solo se cancelan la alarma y el worker de sincronización en esta fase**, no toda la limpieza de Room/preferencias por usuario (el carry-over pedía "al menos" eso). `SignOutUseCase` completo, con el aviso de "N entrenamientos sin sincronizar" y el borrado de Room, es explícitamente de la fase 5 (sección 9.5, sección 10 fase 5) - tocarlo ahora hubiera significado adelantar código y tests de esa fase. `ProfileViewModel.onSignOutConfirmed()` ya llama a `RestTimerAlarmScheduler.cancel()` y `SyncScheduler.cancel()` antes de `authRepository.signOut()`, cubierto por `ProfileViewModelTest`.
- **Item de revisión de FASE 2/3 resuelto en esta fase** (estaba listado para "FASE 4" en `docs/review_carryover.md`): `ActiveWorkoutDao.replaceActive` ahora verifica que la sesión a reemplazar pertenezca al mismo usuario que la nueva (`getUserIdForSession`), con test en `ActiveWorkoutDaoTest` y `ActiveWorkoutRepositoryImplTest`.
- **Carry-over de FASE 3 resuelto de paso**: `RoutineOrdering.unassignedBucketDayNumber` ahora reutiliza el `dayNumber` que ya comparten los ejercicios de una rutina sin días (en vez de asumir siempre `1`), así una rutina adoptada de una plantilla (que usa `UNASSIGNED_DAY_NUMBER` = 0) no termina con un "día 1" fantasma la primera vez que se le agrega un ejercicio.
- **Carry-over de FASE 3 resuelto de paso**: `feature/auth/LoginScreen.kt` y `OnboardingViewModel`/`OnboardingScreen` migrados al patrón evento-único + `ObserveAsEvents` (antes: `collectLatest` plano y una lambda de composición pasada a `viewModelScope`, respectivamente).

### FASE 5: Historial, progreso, dashboard y perfil completo

1. **`feature/history/list/`:** `HistoryViewModel`/`Screen`.
   - Banner de pendientes ("N entrenamientos pendientes de sincronizar") y de fallidos (lista con "Reintentar" → `resetToPending` + `schedule`, y "Descartar" con confirmación).
   - Lista paginada (30 por página, "Cargar más" / scroll infinito). `SessionCard`: nombre de rutina o "Entrenamiento libre", fecha "lunes 3 de marzo" (es-AR con `DateTimeFormatter.ofPattern("EEEE d 'de' MMMM", Locale("es","AR"))`), duración.
   - Long-press → borrar (confirmación con la fecha). Se refresca al bajar `pendingCount`.
2. **`feature/history/detail/`:** `SessionDetailViewModel`/`Screen`.
   - Hero: duración y volumen, sin calentamientos.
   - Bloques por ejercicio con series ordenadas. `EditableSetRow` con estado de guardado **por fila** (`savingSetId`), edición con validación en la unidad del usuario.
   - Borrar con confirmación. "Agregar serie" usa `setNumber = max + 1`, copia peso y reps de la última y `completedAt = session.completedAt ?: startedAt`.
   - Compartir → `ShareWorkoutSheet` (la fase 6 lo completa). Por ahora el botón abre la hoja con opciones deshabilitadas o se oculta hasta la fase 6.
3. **`feature/progress/`:** `ProgressViewModel`/`Screen`. "Records Personales" (nombre, "P kg × R reps", 1RM con un decimal y trofeo; en la unidad del usuario), chips por ejercicio (tocar el activo lo deselecciona), badge del PR y `LineChart` (propio en `Canvas`: ejes mínimos, puntos, etiquetas "d/M" de los últimos 8 puntos, color secondary). Pull-to-refresh.
4. **`feature/dashboard/`:** `DashboardViewModel`/`Screen` con `GetDashboardStatsUseCase` y rutinas desde el caché.
   - Saludo según la hora (BUEN DÍA, BUENAS TARDES, BUENAS NOCHES) y nombre.
   - `StatCard` "ESTA SEMANA" (sesiones) y "ÚLTIMA SESIÓN" ("Hoy", "Ayer", "Hace N días", "-").
   - CTA "Iniciar Entrenamiento" (a Rutinas) o banner "Continuar entrenamiento".
   - Tarjeta "ÚLTIMO PR" (nombre, e1RM con un decimal más la unidad y "(1RM est.)").
   - "Mis Rutinas" (top 3 por día de la semana más "Ver todas"), estado vacío "¡Empezá ahora!".
   - Chip de pendientes; pull-to-refresh. Los errores parciales no bloquean: cada sección maneja el suyo.
5. **`feature/profile/`:** `ProfileViewModel`/`Screen`.
   - Avatar (Coil con `avatarUrl` de la metadata) o inicial; nombre; email.
   - Estadísticas ENTRENOS / PRs / RUTINAS.
   - "DATOS FÍSICOS": Peso (en la unidad preferida), Altura, Edad (edita la fecha "DD/MM/AAAA" con autoformato de barras), Objetivo (diálogo de selección). Diálogos con validación y mensajes.
   - "CONFIGURACIÓN": "Unidad de peso" con `SegmentedButton` kg/lb.
   - "Cerrar sesión": si `pendingCount > 0` avisa "Tenés N entrenamientos sin sincronizar; si cerrás sesión se perderán" y luego ejecuta `SignOutUseCase`.
6. Use cases de `domain/usecase/` (9.5) que se implementan en esta fase (diferidos de la fase 2: sus tests están acá, no en la 2): `GetDashboardStatsUseCase`, `GetExerciseProgressUseCase`, `GetProfileOverviewUseCase`, `UpdateProfileUseCase`, `SignOutUseCase`.

**Tests de la fase 5:** `HistoryViewModelTest` (paginación, borrado, reintentar y descartar fallidos), `SessionDetailViewModelTest` (validación, `setNumber = max + 1`, `completedAt` de la sesión, guardado por fila), `ProgressViewModelTest`, `GetDashboardStatsUseCaseTest` (semana desde lunes, pendientes, fallo parcial), `DashboardViewModelTest`, `UpdateProfileUseCaseTest` (rangos, fechas imposibles como 31/02, futuras, conversión lb → kg), `ProfileViewModelTest`, `SignOutUseCaseTest` (limpia la base y las preferencias por usuario, cancela la alarma, conserva la unidad).

**Aceptación de la fase 5:** tests verdes; las cinco pestañas son funcionales. Sin compilación del árbol Android en la sesión de implementación (no había SDK disponible): 147 tests de dominio + helpers puros verificados en arnés JVM independiente; resto de ViewModel/Room/UI escritos sin compilar.

**Desviaciones registradas durante la implementación:**

1. **`DashboardStats`:** model con tres `AppResult` independientes (sesiones, última sesión, PR) en vez de un solo `AppResult<DashboardStats>`. Permite que un error parcial no bloquee el resto (p. ej. si falla el PR pero llegan las sesiones, se muestran las sesiones). No incluye `pendingSyncCount`: el conteo de pendientes se observa directamente en `DashboardViewModel` desde Room en vivo.
2. **Paginación:** `WorkoutHistoryRepository.getSessions(offset, limit)` en lugar de `(page, pageSize)`. Es más robusto ante duplicados y borrados intermedios: `distinctBy(id)` se aplica al concatenar.
3. **Recuento de filas afectadas:** `updateSet`, `deleteSet` y `updatePhysical` devuelven `Int` y aplican `requirePositiveOrNotFound()`. Bajo RLS, un update/delete sin permiso devuelve 0 filas sin lanzar excepción: esto lo convierte en `NotFound`.
4. **`SignOutUseCase` con `LocalDataRepository`:** el use case borra Room usando una interfaz (no `SpotterDatabase` ni `WorkManager`, que son Android). Si falla el borrado, **no cierra sesión** y reprograma la sincronización para reintentar. La limpieza va **antes** de `signOut()` para evitar que otro usuario se log-in y vea un outbox anterior.
5. **`risk()`:** en lugar de solo contar pendientes, devuelve `SignOutRisk(unsyncedWorkouts, hasActiveWorkout)`. El `ProfileScreen` muestra la advertencia correspondiente.
6. **Banner compartido:** "Entrenamiento en curso" vive en Dashboard **y** Rutinas (no solo Dashboard). Se extrajo a `feature/common/ActiveWorkoutBanner.kt`.
7. **Relay del snackbar:** al finalizar, `WorkoutScreen.onFinished(online)` navega al Dashboard pasando el flag en `savedStateHandle` bajo la clave `KEY_WORKOUT_FINISHED_ONLINE`. El Dashboard lo lee, consume y muestra el snackbar. Descartar sigue con `popBackStack()`.
8. **Carrera `NoActiveWorkout`:** `WorkoutViewModel` agrega `private var closing = false`, que se pone en `true` al empezar `onFinish`/`onDiscard`, y se restaura a `false` si la operación falla. Mientras es `true`, el `combine` no emite `NoActiveWorkout`, evitando la carrera que hacía saltar atrás y perder el snackbar.
9. **Historial:** "Cargar más" en vez de scroll infinito (detalle técnico: con scroll infinito y offset paginado, si se sincroniza una sesión mientras se pagina, el offset se corre; "Cargar más" deja control explícito).
10. **Progreso:** chips sin preselección. Arranca con un texto de ayuda "Elegí un ejercicio"; tocar un chip lo selecciona, tocar el mismo lo deselecciona. Los chips salen de los ejercicios que tienen PR.
11. **Perfil:** campo vacío (tras `trim`) borra el dato (peso, altura, nacimiento). Objetivo admite "Sin especificar" (`null`).
12. **Onboarding otra vez:** `clearUserScoped()` borra `onboarding_done_*`, así el mismo usuario ve onboarding de nuevo tras sign-out. Es lo que el plan especifica; se puede cambiar en `PreferencesRepositoryImpl.clearUserScoped()` si se decide conservarlo.

### FASE 6: Compartir e importar rutinas, importación con IA y exportación de entrenamientos

1. **Compartir rutina:** `ShareRoutineUseCase` conectado en `RoutineDetailScreen`. Envía `Intent.ACTION_SEND` (`text/plain`) con "Te comparto mi rutina "X" en Spotter\nCódigo: CODE\nspotter://import/CODE" vía `Intent.createChooser`, y además copia el código al portapapeles (`ClipboardManager`) con snackbar "Código copiado".
2. **`feature/importroutine/code/`:** `ImportCodeViewModel`/`Screen`.
   - Recibe `ImportCodeRoute(code)`; revalida con `ShareCode.parse`. Si es inválido → "Código inválido o expirado" **sin tocar la red**.
   - Preview: "RUTINA COMPARTIDA", nombre, "N ejercicios".
   - "Importar rutina" → `ImportSharedRoutineUseCase.import` → reemplaza la pantalla por `RoutineDetailRoute(newId)` con "Rutina importada". "Cancelar" vuelve.
3. **`feature/importroutine/image/`:** `ImportImageViewModel`/`Screen`.
   - Botones "Cámara" (`ActivityResultContracts.TakePicture` con Uri de `FileProvider` en `cache/camera/`; no requiere el permiso CAMERA, **no declararlo**) y "Galería" (`PickVisualMedia(ImageOnly)`).
   - Preview con "Cambiar imagen".
   - "Importar rutina" → `ImageEncoder.encode(uri)`: decodifica con `inSampleSize`, rota por EXIF, reduce a ≤1600 px, JPEG 80 y base64 `NO_WRAP`, en IO. Luego `ImportRoutineFromImageUseCase`.
   - Loading "Analizando rutina con IA... Puede tardar unos segundos"; back deshabilitado mientras carga.
   - Éxito → `RoutineDetailRoute` con ""X" creada".
   - Error: `AiImportRemoteDataSource` captura `RestException` e intenta decodificar `{"error": "..."}` del mensaje o cuerpo. Se muestra un mensaje genérico en español; el detalle solo se loguea en debug.
   - Los archivos temporales de cámara se borran al salir.
4. **Exportación en `data/export/`:**
   - `WorkoutExportDataBuilder.build(detail, unit, locale)` → `WorkoutExportData(routineName, dateText, durationText, totalVolumeText, totalSets, exercises: List<ExportExercise(name, sets, volumeText, summary)>)`. Es puro: agrupa por ejercicio en el orden de aparición, ordena por `setNumber`, excluye calentamientos del volumen, `summary = "S × R reps — P unidad"` sobre el top set, y "+N ejercicio(s) más".
   - `WorkoutPdfRenderer.render(data, fonts): File`: `PdfDocument` A4 de 595x842 pt, paginando cuando no entra; mismo diseño y colores que el HTML del RN (header con la marca "SPOTTER" en lima, fecha, hero con duración en cyan, volumen en lima, series y ejercicios; bloques por ejercicio con tabla # / Peso / Reps / RPE; footer "Generado con Spotter").
   - `WorkoutStoryRenderer.render(data): File`: Bitmap 1080x1920, dibujo equivalente a `generateStoryCanvasHtml` (`workout-export.ts:537-668`) con máximo 4 ejercicios, truncado con "…"; JPEG calidad 92.
   - `ExportFileWriter`: escribe en `cacheDir/exports/`, borra archivos viejos (más de 1 h), devuelve el Uri de `FileProvider`.
   - `feature/history/share/ShareWorkoutSheet`: opciones "PDF Detallado" y "Historia" con loading, luego `ACTION_SEND` con `FLAG_GRANT_READ_URI_PERMISSION` (`application/pdf` o `image/jpeg`). Los errores van a snackbar.
5. Use cases de `domain/usecase/` (9.5) que se implementan en esta fase (diferidos de la fase 2: sus tests están acá, no en la 2): `ShareRoutineUseCase`, `ImportSharedRoutineUseCase`, `ImportRoutineFromImageUseCase`, `BuildWorkoutExportUseCase`.

**Tests de la fase 6:** `ShareRoutineUseCaseTest` (reutiliza el activo, reintenta ante Conflict hasta 3 veces), `ImportSharedRoutineUseCaseTest` (expirado → NotFound, sufijo del nombre, días y ejercicios copiados, compensación ante fallo), `ImportCodeViewModelTest` (código inválido sin llamadas), `ImportRoutineFromImageUseCaseTest` (límite de tamaño), `ImportImageViewModelTest` (mapeo de errores), `WorkoutExportDataBuilderTest` (agrupación, orden, volumen sin calentamientos, lb, "+2 ejercicios más", duración null → "—").

**Aceptación de la fase 6:** tests verdes; el deep link `adb shell am start -a android.intent.action.VIEW -d "spotter://import/ABCDEFGH" com.lucho314.spotter.debug` abre la vista previa (o el error) solo después del login.

**Desviaciones registradas durante la implementación:**

1. **Ubicación de `WorkoutExportDataBuilder`:** va en `domain/calc/`, no en `data/export/`, porque `BuildWorkoutExportUseCase` lo delega y la feature necesita acceso a través del use case. Así queda testeable en el arnés JVM (que incluye `domain/` completo).
2. **Forma estructurada de `WorkoutExportData`:** en vez de un objeto plano con textos de UI, lleva números (peso en kg, volumen, duración en minutos, sets totales) y textos numéricos ya formateados (ej. "45 min", "12.345 kg", "8" para RPE). Los plurales ("3 reps", "+2 ejercicios más", "1RM est.") y las etiquetas de encabezado se arman en los renderers con `getQuantityString(context.resources, @plurals)`, así no hay HTML/template en el dominio.
3. **Orden de ejercicios en la exportación:** es `completedAt` mínimo por ejercicio y luego `exerciseId`, idéntico a `SessionDetailViewModel.buildBlocks` que ahora delega en `ExerciseSetGrouping`. Es el "orden de aparición" de una unificación de sets por ejercicio, no el de Postgrest que no está garantizado.
4. **`AiImportRepository.importFromImage` devuelve `AiImportedRoutine`:** el texto de error de la función nunca se propaga a la UI ni a un `AppError.Server(message)`. Solo el código de error (`TIMEOUT`, `REJECTED`, etc., del nuevo enum `AiImportErrorCodes`) se pone en `AppError.Server(code)`. El mapeo de excepciones es propio en `AiImportErrorMapper`: `HttpRequestTimeoutException` → `TIMEOUT`, `413 RestException` → `Validation(IMAGE_TOO_LARGE)`, JSON inválido → `INVALID_RESPONSE`, etc.
5. **`ImportRoutineFromImageUseCase` recibe `userId` y `routineRepository`:** valida que el UUID devuelto por la función sea formato UUID válido, y que la rutina tras un re-fetch con el filtro `user_id` exista de verdad (frente a B2: imposibilidad de que el servidor devuelva una rutina de otro usuario). El nombre mostrado sale de la base (re-fetch), no de la respuesta. En fallos ambiguos (timeout, red), refresca la lista completa de rutinas para que una creada igual aparezca.
6. **Socket timeout:** `SupabaseAiImportRemoteDataSource.timeout { socketTimeoutMillis = 120_000; requestTimeoutMillis = 120_000 }`. Contexto: Ktor 3.5.1 con OkHttp solo configura `readTimeout` si `socketTimeoutMillis` es explícitamente ≠ null; de lo contrario, queda el default de OkHttp de 10 segundos. La función puede tardar más de 10s procesando la imagen. Corrigiendo ambos junto mantiene ambos timeouts sincronizados.
7. **Lectura del share con DTO liviano:** nueva consulta `SharedRoutineImportDto` (sin `id` del usuario que creó, sin catálogo de ejercicios - solo la estructura mínima). Validación en cliente: el `share_code` devuelto debe coincidir exacto con el pedido, e `is_active` debe ser true.
8. **Sanitización completa de la rutina compartida:** `SharedRoutineSanitizer.sanitize()` recorta nombres/descripciones, rango de series/reps/descanso, normaliza días (1..7), deduplica ejercicios por `(exerciseId, dayNumber)`, y limita a 100 ejercicios totales → devuelve null si hay más (error `SHARED_ROUTINE_INVALID`). Nombre importado: `"<original> (importada)"` recortado a 50 caracteres totales. `dayNumber` fuera de rango se pone en `UNASSIGNED_DAY_NUMBER` (0).
9. **`ShareRoutineUseCase(userId, routine: RoutineDetail)`:** genera un código con `SecureRandom` y reintenta ante `Conflict` hasta 3 intentos en total (no 3 reintentos). Nunca comparte una rutina con `userId !=` del parámetro (defensa B2). `findActiveShare` falla → propaga el error sin crear ciego.
10. **Puertos nuevos:** `ImageRepository` (interfaz, impl en `data/image/`) y `WorkoutExportRepository` (interfaz, impl en `data/export/`) para no mezclar lógica de IO/canvas en `domain/`.
11. **Sin cambios:** Gradle, AndroidManifest.xml, Room schema, backend.
12. **Compilación:** 234 tests de dominio y helpers puros pasan en arnés JVM. Tests Gradle (ViewModel/Feature, 100+) están escritos pero sin compilar (sin Android SDK en el ambiente de implementación). Sin verificación en dispositivo real del deep link, del diseño visual PDF/historia, ni de la limpieza de archivos temporales.

### FASE 7: Endurecimiento y release

1. **`app/proguard-rules.pro`:**
   ```
   -keepattributes *Annotation*, InnerClasses, Signature, Exceptions, EnclosingMethod
   -keep,includedescriptorclasses class com.lucho314.spotter.**$$serializer { *; }
   -keepclassmembers class com.lucho314.spotter.** { *** Companion; }
   -keepclasseswithmembers class com.lucho314.spotter.** { kotlinx.serialization.KSerializer serializer(...); }
   -dontwarn org.slf4j.**
   -dontwarn com.google.errorprone.annotations.**
   -dontwarn javax.annotation.**
   -assumenosideeffects class android.util.Log { public static int v(...); public static int d(...); public static int i(...); public static int w(...); }
   ```
   Agregar `-dontwarn` puntuales solo si R8 lo pide, e indicar cuáles en el reporte.
2. **Firma:** `keystore.properties` opcional (`storeFile`, `storePassword`, `keyAlias`, `keyPassword`) leído en `build.gradle.kts`. Si existe, se usa `signingConfigs.release`; si no, el APK release sale sin firmar. **Nunca commitear.** Agregar `keystore.properties.example`.
3. **Lint:** `lint { abortOnError = true; checkReleaseBuilds = true; warningsAsErrors = false }`. Corregir errores; documentar en `lint-baseline.xml` solo lo que sea de librerías.
4. **Revisión de seguridad** (anotar hallazgos en el reporte; no escribir archivos de informe):
   - Grep de `Log.` fuera de `AndroidLogger`.
   - Sin `println` y sin `!!` injustificados.
   - Intents y `PendingIntent` inmutables; receiver y provider no exportados.
   - `tools:targetApi` correcto; `android:exported` explícito en todos los componentes.
5. **Accesibilidad:** `contentDescription` en iconos accionables y objetivos táctiles ≥48 dp.
6. **Comprobaciones finales:** `./gradlew clean assembleDebug testDebugUnitTest lintDebug assembleRelease`. Verificar con `grep` que `app/build/outputs/apk/release/*.apk` no contiene la cadena "localhost" ni `http://`, y que `BuildConfig` release no expone nada fuera de URL y anon key.

**Aceptación de la fase 7:** los cuatro comandos en verde; R8 activo (`mapping.txt` generado); ningún log de debug en release.

---

## 11. Pruebas (resumen de comandos)

```bash
export JAVA_HOME="/c/Program Files/Android/Android Studio/jbr"; export PATH="$JAVA_HOME/bin:$PATH"; cd "/e/Spoter Kotlin"
./gradlew testDebugUnitTest                         # todos los tests unitarios (JVM + Robolectric)
./gradlew testDebugUnitTest --tests "*ViewModelTest"
./gradlew testDebugUnitTest --tests "*DaoTest"      # Robolectric; la primera vez descarga android-all (necesita internet)
./gradlew assembleDebug assembleRelease lintDebug
```

**Convenciones de test:**

- `testutil/MainDispatcherRule`, `FakeTimeProvider`, `FakeIdGenerator`, `Fake*Repository` y `Fake*RemoteDataSource` en `app/src/test/java/com/lucho314/spotter/testutil/`.
- Turbine para los flows: `vm.uiState.test { ... }`.
- Truth para las aserciones.
- Nada de mocks de supabase-kt: se testea a través de las interfaces.
- No hay tests instrumentados (no hay emulador garantizado).

---

## 12. Riesgos y casos borde

- **`compileSdk 37`.** Si alguna dependencia transitiva sube a una versión que exige 37, falla `checkDebugAarMetadata`. Forzar versiones con `constraints { implementation("grupo:artefacto") { version { strictly("x") } } }` o reportarlo. Alternativa: instalar la plataforma 37 desde el SDK Manager de Android Studio y usar las versiones más nuevas; requiere acción del usuario.
- **Versión de Kotlin con AGP 9.** Si KGP 2.4.20 no se toma, aplicar lo de la sección 4 (declarar kotlin-android con `apply false` en la raíz).
- **KSP 2.3.12 con Kotlin 2.4.20.** Si KSP falla, probar el KSP estable anterior compatible y reportarlo.
- **Nulabilidad y default de `routine_exercises.day_number` y la restricción `UNIQUE(routine_id, exercise_id)`** no se pudieron verificar (requieren la MCP de Supabase o SQL). El DTO es nullable y se omite si es null; 23505 → "Ejercicio ya agregado". Como en el RN, no se puede repetir un ejercicio en dos días de la misma rutina.
- **`RefreshFailure` offline.** Mantener la sesión: el usuario debe poder entrenar sin red. Con el token vencido y offline, el outbox espera y el worker reintenta cuando el refresh funcione.
- **Descifrado de sesión fallido** (keystore invalidado o cambio de dispositivo): se borra y el usuario vuelve a loguearse.
- **Alarmas exactas.** En Android 14 `SCHEDULE_EXACT_ALARM` viene denegado por defecto; el fallback inexacto puede sonar tarde en Doze, lo cual es aceptable. En primer plano el timer de la UI es preciso.
- **ExoPlayer en listas.** Solo se instancia en el detalle y en el ejercicio actual del workout, nunca en listas, para evitar fugas.
- **Doble tap** en finalizar, adoptar, importar o compartir: deshabilitar el botón mientras corre y bloquear con un `Mutex` en el ViewModel.
- **Zona horaria.** Los cálculos de semana y "Hoy/Ayer" usan `ZoneId.systemDefault()` inyectado vía `TimeProvider`.
- **Números.** Aceptar coma y punto; mostrar según es-AR (miles con ".").
- **Deep link de import** mientras hay un entrenamiento activo: navega igual; el entrenamiento sigue accesible por el banner.
- **Cambio de unidad durante un entrenamiento:** no afecta la sesión en curso (unidad fijada al iniciar).
- **Edge function:** puede tardar más de 60 s con imágenes grandes. La reducción a 1600 px lo mitiga; ante timeout se muestra "La IA tardó demasiado, intentá de nuevo".
- **Límites de Postgres:** `numeric(6,2)` y `smallint` quedan cubiertos por los validadores (peso ≤1000 kg, reps ≤200).
- **Conflicto de scheme.** Si la app RN sigue instalada, ambas registran `spotter://`: el sistema muestra un selector o la RN intercepta el callback. PKCE impide robar la sesión, pero conviene desinstalar la app RN durante las pruebas.

---

## 13. Preguntas abiertas (con valor por defecto)

1. **Google Credential Manager.** Requiere crear en Google Cloud un cliente OAuth "Android" (paquete `com.lucho314.spotter` y `.debug`, más el SHA-1 de debug y release) y poner el **Web client ID** en `GOOGLE_WEB_CLIENT_ID`. **Por defecto:** vacío, y se usa OAuth PKCE por navegador (ya funciona con la configuración actual de Supabase).
2. **`applicationId`.** **Por defecto:** `com.lucho314.spotter`, lo que permite reemplazar la app RN en Play Store si se usa la misma keystore de EAS. Debug lleva sufijo `.debug`.
3. **Ítems del backend B1-B6** (sección 8): el usuario decidió (2026-09-25) no modificar el backend. Existe una corrección revisada y lista para aplicar en `supabase/_proposed/` si se reconsidera. **Por defecto:** no se tocan.
4. **Ícono en alta resolución.** **Por defecto:** se reutilizan los `mipmap-*` generados por Expo en `E:\Spotter\android\app\src\main\res`.
