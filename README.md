# Spotter (Android nativo)

Spotter es una app de seguimiento de entrenamientos de gimnasio. Este repositorio es la
reescritura nativa en Kotlin/Jetpack Compose de la app original en React Native/Expo (fuente,
solo lectura: `E:\Spotter`), manteniendo el mismo backend Supabase sin cambios de esquema.

## Estado

**FASES 1 a 6 de 7 implementadas** (aprobadas FASES 1-4 el 2026-09-26; **FASES 5-6 implementadas sin revisión independiente ni compilación del árbol Android**, solo 147 tests de dominio/helpers de FASE 5 verificados en arnés JVM; 571 tests totales escritos). Ver el plan completo en [`docs/MIGRATION_PLAN.md`](docs/MIGRATION_PLAN.md) y el detalle de arquitectura/estructura en `docs/`.

Implementado hoy:
- Autenticación con Google (Credential Manager, con fallback a OAuth PKCE por navegador) y sesión
  cifrada en disco.
- Onboarding, guardia de sesión (`RootViewModel`) y navegación type-safe (Navigation Compose).
- Capa de datos completa: DTOs, mappers, data sources remotos (Supabase) y Room v1 (caché de
  lectura, entrenamiento activo y outbox de sincronización) para todas las entidades del dominio.
- Gestión de rutinas: lista, archivo, edición (crear/editar), detalle (días, drag & drop, mover de
  día, agregar ejercicio), plantillas (lista, detalle, adopción con compensación transaccional) y
  detalle de ejercicio con video/gif/imagen.
- Entrenamiento activo: inicio desde una rutina (con selector de día y diálogo si ya hay uno en
  curso), registro de series con estado persistido en Room (sobrevive a la muerte del proceso),
  temporizador de descanso con alarma y notificación "Descanso terminado" en segundo plano,
  finalizar/descartar con relay al dashboard, y sincronización offline del outbox con WorkManager.
- **Historial, progreso, dashboard y perfil completo** (FASE 5): lista de entrenamientos con
  paginación, detalle de sesión editable, gráfico de progreso por ejercicio, dashboard con
  estadísticas de la semana y últimas rutinas, perfil con datos físicos editables, unidad kg/lb,
  y `SignOutUseCase` con limpieza de Room y advertencia de entrenamientos sin sincronizar. Banner
  "Entrenamiento en curso" compartido en Dashboard y Rutinas. Cinco use cases nuevos: `GetDashboardStatsUseCase`,
  `GetExerciseProgressUseCase`, `GetProfileOverviewUseCase`, `UpdateProfileUseCase`, `SignOutUseCase`.
- **FASE 6 implementada (sin revisión ni compilación):** compartir rutina (`RoutineDetailScreen`,
  Action Send + portapapeles), importar por código (`ImportCodeRoute` con preview y validación),
  importar con IA desde imagen (`ImportImageRoute` con cámara/galería, re-codificación EXIF-less,
  validación UUID y propiedad), exportar entrenamiento en PDF A4 paginado e imagen "historia"
  1080×1920 (`SessionDetailScreen` con `ShareWorkoutSheet`). Nuevos use cases: `ShareRoutineUseCase`,
  `ImportSharedRoutineUseCase`, `ImportRoutineFromImageUseCase`, `BuildWorkoutExportUseCase`. Se
  elimina `ComingSoonScreen` (sin usos). Puertos nuevos: `ImageRepository`, `WorkoutExportRepository`.
  Cálculos puros: `TextSanitizer`, `SharedRoutineSanitizer`, `ExerciseSetGrouping`,
  `WorkoutExportDataBuilder`. Fix: `socketTimeoutMillis = 120 s` en `SupabaseAiImportRemoteDataSource`
  (OkHttp cortaba a los 10 s).
- 571 tests unitarios totales (147 de dominio + helpers puros verificados sin Android SDK en
  arnés JVM; el resto de ViewModel/Room/Feature escritos pero sin compilar por falta de SDK).

**Pendiente (FASE 7, ver "Planificado" en `docs/*.md`):** endurecimiento de release y verificación
en dispositivo/emulador real.

**FASES 5-6 sin verificación en dispositivo/emulador real ni compilación del árbol Android** — ver
sección "Compilación" más abajo.

## Requisitos

- **JDK de Android Studio** (no el `java` del PATH): `C:\Program Files\Android\Android Studio\jbr`
  (OpenJDK 21). Un JDK distinto (p. ej. Temurin 25) puede ser incompatible con este AGP/Gradle.
- **Android SDK** con `compileSdk`/`targetSdk` 36, `minSdk` 26.
- **Gradle** vía el wrapper del proyecto (no requiere instalación global).
- **Kotlin** 2.4.20 (fijado en `gradle/libs.versions.toml`).
- Git Bash o una shell POSIX para los comandos de abajo (la ruta del proyecto tiene un espacio:
  `Spoter Kotlin`, hay que citarla siempre).

## Configuración

1. Copiar `local.properties.example` a `local.properties` (gitignored) y completar:
   ```properties
   sdk.dir=<ruta al Android SDK>
   SUPABASE_URL=https://xxxxx.supabase.co
   SUPABASE_ANON_KEY=<clave anónima pública del proyecto Supabase>
   GOOGLE_WEB_CLIENT_ID=<opcional: client id OAuth de Google para Credential Manager>
   ```
   Si `GOOGLE_WEB_CLIENT_ID` queda vacío, el login usa el flujo OAuth PKCE por navegador en su
   lugar.
2. Apuntar `JAVA_HOME` al JDK de Android Studio antes de compilar:
   ```bash
   export JAVA_HOME="/c/Program Files/Android/Android Studio/jbr"
   export PATH="$JAVA_HOME/bin:$PATH"
   ```
   (PowerShell: `$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"`).

## Compilar, probar y empaquetar

Desde el directorio del proyecto (con el JDK de Android Studio en el PATH):

```bash
./gradlew assembleDebug            # APK debug
./gradlew testDebugUnitTest         # tests unitarios (571 @Test totales, 147 verificados sin SDK)
./gradlew assembleRelease           # APK release con R8 (falla si faltan las claves de Supabase)
./gradlew assembleDebug testDebugUnitTest   # build + tests en un solo paso
```

**⚠️ Nota sobre FASES 5-6:** el código de las pantallas de dashboard, historial, progreso, perfil
completo, compartir/importar rutinas y exportar entrenamientos está escrito pero **no ha sido
compilado** porque el entorno de desarrollo donde se escribió no tiene Android SDK. Los 147 tests
de dominio y helpers puros (cálculos, conversiones, validaciones, sanitización, formateo) de FASE 5
fueron verificados en un arnés JVM independiente fuera del repo. Para compilar FASES 5-6 en
producción, se requiere:
1. Una máquina con `compileSdk 36`, `targetSdk 36`, `minSdk 26` y el JDK de Android Studio.
2. Correr `./gradlew assembleDebug testDebugUnitTest` para verificar que los tipos, imports y
   firmas de API de Compose/Navigation/Room son correctos.
3. Si la compilación falla, el reporte incluye la lista de errores específicos.

## Seguridad

- La sesión de Supabase y el code verifier de PKCE se cifran con Tink (AES256-GCM) protegido por
  una clave del Android Keystore antes de guardarse en DataStore; nunca en texto plano (ver
  `docs/arquitectura.md`).
- `SUPABASE_URL`/`SUPABASE_ANON_KEY`/`GOOGLE_WEB_CLIENT_ID` solo viven en `local.properties` →
  `BuildConfig`, nunca commiteados.
- **Backend:** el 2026-09-25 se decidió explícitamente **no modificar Supabase**. Los hallazgos de
  seguridad B1–B6 (ver `docs/MIGRATION_PLAN.md` §8) siguen abiertos en el proyecto en vivo. Existe
  una corrección ya revisada y aprobada para B1/B2/B6 en `supabase/_proposed/` (no aplicada); ver
  [`supabase/_proposed/README.md`](supabase/_proposed/README.md) para el detalle y cómo aplicarla
  si se reconsidera. El cliente Kotlin funciona igual sin ella.

## Documentación

- [`docs/arquitectura.md`](docs/arquitectura.md) — capas, DI, flujos de datos, seguridad, decisiones de diseño.
- [`docs/estructura.md`](docs/estructura.md) — árbol de paquetes y responsabilidad de cada uno.
- [`docs/componentes.md`](docs/componentes.md) — clases e interfaces principales, APIs públicas.
- [`docs/changelog.md`](docs/changelog.md) — historial de cambios por fase.
- [`docs/MIGRATION_PLAN.md`](docs/MIGRATION_PLAN.md) — plan de migración completo (inventario RN, bugs evitados, hallazgos de backend).
- [`docs/review_carryover.md`](docs/review_carryover.md) — ítems de revisión pendientes para fases futuras.
- [`docs/backend/`](docs/backend/) — esquema en vivo de Supabase y contexto de los hallazgos B1/B2.
