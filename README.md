# Spotter (Android nativo)

Spotter es una app de seguimiento de entrenamientos de gimnasio. Este repositorio es la
reescritura nativa en Kotlin/Jetpack Compose de la app original en React Native/Expo (fuente,
solo lectura: `E:\Spotter`), manteniendo el mismo backend Supabase sin cambios de esquema.

## Estado

**Todas las FASES (1-7) implementadas:** FASES 1-4 aprobadas el 2026-09-26; **FASES 5-7 implementadas sin revisión independiente, FASES 5-6 sin compilación de Android**. Arnés JVM: 473 tests verificados en un arnés JVM ampliado (dominio, helpers puros, los 19 ViewModels, capa de datos remota, mappers, repositorios y navegación, compilados contra supabase-kt/Ktor/kotlinx-serialization reales). **FASE 7 Parte A (configuración, seguridad, tests, accesibilidad):** implementada SIN revisión independiente; **Parte B (build Android, lint, R8, release):** pendiente en máquina del usuario. Ver el plan completo en [`docs/MIGRATION_PLAN.md`](docs/MIGRATION_PLAN.md) y el detalle de arquitectura/estructura en `docs/`.

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
- **Integración con Garmin Connect:** al finalizar un entrenamiento (y también manualmente desde
  "Subir a Garmin" en el detalle de sesión), Spotter puede subirlo a Garmin Connect como actividad
  "Strength Training" (`Sport.TRAINING` / `SubSport.STRENGTH_TRAINING`). Login por email/contraseña
  (con MFA) contra la API no oficial de Garmin Connect (sin evasión de detección de bots; CAPTCHA/
  bloqueo/límite de tasa se muestran como error); la contraseña nunca se persiste, los tokens se
  cifran con Tink en DataStore. El archivo FIT (series, repeticiones, peso, categoría de ejercicio)
  se genera con un encoder propio, sin la licencia restrictiva del SDK oficial de Garmin en runtime.
  Subida en segundo plano con WorkManager (`GarminUploadWorker`/`GarminUploadScheduler`), idempotente
  vía una tabla Room propia (`garmin_upload`, un 409/"Duplicate Activity" de Garmin se trata como
  éxito). Sección "Garmin Connect" en `ProfileScreen` (conectar/desconectar, subida automática,
  reintentar fallidos) y pantalla `GarminConnectRoute` (credenciales + MFA + aviso de API no
  oficial). Las actividades subidas se ven en Garmin Connect (web/app/estadísticas) pero **no** se
  copian al historial on-device del reloj. Detalle completo en [`docs/arquitectura.md`](docs/arquitectura.md)
  y [`docs/componentes.md`](docs/componentes.md).
- **785 tests unitarios totales**, verificados con `./gradlew :app:testDebugUnitTest` en una
  máquina con Android SDK real (785 @Test, 0 fallos); `assembleDebug` y `assembleRelease` también
  compilan sin errores. El detalle histórico de qué se había verificado sin SDK en cada fase (antes
  de esta corrida) está en [`docs/changelog.md`](docs/changelog.md).

**FASE 7:** endurecimiento y release implementados en Parte A (configuración, seguridad,
  accesibilidad, 15 tests de FASES 5-6 corregidos). Verificación en dispositivo/emulador real
  (Parte B: assembleDebug, tests, lint, assembleRelease, R8) pendiente en máquina del usuario.

**Integración con Garmin Connect (2026-09-28):** implementada sin revisión independiente. Permite
subir un entrenamiento finalizado a Garmin Connect como actividad "Strength Training". En esta
sesión se compiló y probó por primera vez el árbol completo de Android en una máquina con SDK:
`./gradlew :app:testDebugUnitTest` (**785 @Test, 0 fallos**), `:app:assembleDebug` y
`:app:assembleRelease` terminan sin errores. `:app:lintDebug` sigue fallando por 34 errores
preexistentes **no relacionados** con esta función (`LocalContextGetResourceValueCall` en varias
pantallas, `BidiSpoofing` en `TextSanitizer.kt`, formato de `local.properties`), pendientes de una
limpieza aparte. No se probó con un dispositivo/cuenta Garmin real ni hubo revisión independiente
de este cambio. Detalle en [`docs/changelog.md`](docs/changelog.md),
[`docs/arquitectura.md`](docs/arquitectura.md) y [`docs/componentes.md`](docs/componentes.md).

**FASES 5-7 sin verificación en dispositivo/emulador real ni revisión independiente** — el árbol de
Android sí compila y sus tests pasan (ver el párrafo anterior); queda pendiente la prueba manual en
dispositivo. Ver sección "Compilación" más abajo.

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
./gradlew testDebugUnitTest         # tests unitarios (785 @Test totales)
./gradlew assembleRelease           # APK release con R8 (falla si faltan las claves de Supabase)
./gradlew assembleDebug testDebugUnitTest   # build + tests en un solo paso
```

## Firma y release (FASE 7)

### Configuración de la firma

1. Copiar `keystore.properties.example` a `keystore.properties` (gitignored) y completar con los datos del keystore:
   ```properties
   # Para usar la misma upload key que la app RN en Play Store (EAS):
   storeFile=<ruta al .jks o .keystore de upload>
   storePassword=<contraseña del keystore>
   keyAlias=<alias de la clave>
   keyPassword=<contraseña de la clave>
   ```
   Si el archivo no existe, `assembleRelease` genera un APK sin firmar. Si existe pero está incompleto,
   la tarea `verifyReleaseConfig` falla explícitamente sin imprimir secretos.

2. `./gradlew assembleRelease` genera `app/build/outputs/apk/release/app-release-unsigned.apk` (sin
   keystore.properties) o el APK firmado (con la clave).

### Checklist de verificación (Parte B, en máquina del usuario)

**En orden de ejecución desde Git Bash:**

```bash
export JAVA_HOME="/c/Program Files/Android/Android Studio/jbr"
export PATH="$JAVA_HOME/bin:$PATH"
cd "/e/Spoter Kotlin"
```

1. **`./gradlew help`** → valida que los scripts de Gradle parseen correctamente.

2. **`./gradlew :app:assembleDebug`** → errores de compilación acumulados de FASES 5-7. Pegar log.

3. **`./gradlew :app:testDebugUnitTest`** → reporte en `app/build/reports/tests/testDebugUnitTest/index.html`.

4. **`./gradlew :app:lintDebug`** → reporte en `app/build/reports/lint-results-debug.html`. Corregir
   todo lo que apunte a `app/src/...`. Si solo quedan warnings de librerías, crear baseline:
   ```bash
   ./gradlew :app:updateLintBaseline
   # luego editar app/lint-baseline.xml y borrar entradas con location en app/src/
   ./gradlew :app:lintDebug
   ```
   **Estado al 2026-09-28:** falla con 34 errores preexistentes, no relacionados con la integración
   de Garmin (`LocalContextGetResourceValueCall` en varias pantallas, `BidiSpoofing` en
   `TextSanitizer.kt`, formato de `local.properties`) — pendiente una limpieza aparte o el baseline
   de arriba.

5. **`./gradlew :app:assembleRelease`** (sin `keystore.properties`) → verifica R8 y lintVital.
   - Si R8 falla (*Missing class*): abrir `app/build/outputs/mapping/release/missing_rules.txt` y
     copiar a `proguard-rules.pro` solo `-dontwarn` de APIs JVM-only o dependencias opcionales no usadas.

6. **`./gradlew clean assembleDebug testDebugUnitTest lintDebug assembleRelease`** → ciclo completo.

7. **Verificar el APK:**
   ```bash
   ls app/build/outputs/mapping/release/mapping.txt              # Confirma R8 activo
   grep "SyncWorkoutsWorker -> " app/build/outputs/mapping/release/mapping.txt  # Debe coincidir exacto
   grep -rn 'http://\|localhost' app/src/main/java               # Debe salir vacío
   ```

8. **Prueba de humo del release** (con APK unsigned + clave debug o clave real):
   - Login con Google, listar rutinas, entrenar sin red, finalizar, volver a conectar, exportar PDF
     e historia, deep link de importación, importación con IA.
   - Logcat sin logs de la app.

9. **Firma real:** crear o reutilizar keystore (EAS), `./gradlew assembleRelease bundleRelease`,
   `apksigner verify --print-certs`, registrar SHA-1 en cliente OAuth Android si usa Credential Manager.

**⚠️ Nota histórica sobre FASES 5-6 (superada el 2026-09-28):** el código de las pantallas de
dashboard, historial, progreso, perfil completo, compartir/importar rutinas y exportar
entrenamientos se escribió sin Android SDK disponible; tras la FASE 7, solo 473 tests (dominio,
helpers puros, ViewModels, capa de datos y navegación) se habían verificado en un arnés JVM
independiente fuera del repo, sin compilar las pantallas Compose, Room ni los renderers Android.
**Al implementar la integración con Garmin Connect (2026-09-28)** se corrió por primera vez el
build real en una máquina con `compileSdk 36`/`targetSdk 36`/`minSdk 26` y el JDK de Android
Studio: `./gradlew :app:testDebugUnitTest` (785 @Test, 0 fallos), `:app:assembleDebug` y
`:app:assembleRelease` terminan sin errores — esto confirma que las FASES 5-7 también compilan.
Sigue pendiente: `:app:lintDebug` (34 errores preexistentes no relacionados, ver el checklist
arriba) y la verificación en un dispositivo/emulador real.

## Seguridad

- La sesión de Supabase y el code verifier de PKCE se cifran con Tink (AES256-GCM) protegido por
  una clave del Android Keystore antes de guardarse en DataStore; nunca en texto plano (ver
  `docs/arquitectura.md`).
- `SUPABASE_URL`/`SUPABASE_ANON_KEY`/`GOOGLE_WEB_CLIENT_ID` solo viven en `local.properties` →
  `BuildConfig`, nunca commiteados.
- **Backend:** el 2026-09-29 se aplicaron las correcciones B1, B2 paso 1 y B6 en Supabase: la
  importación con IA exige JWT, escribe de forma transaccional y tiene cuota; las lecturas anónimas
  de rutinas compartidas están cerradas. B2 paso 2 espera el retiro de la app React Native anterior.
  Ver [`supabase/_proposed/README.md`](supabase/_proposed/README.md) y
  [`docs/query_index_review_2026-09-29.md`](docs/query_index_review_2026-09-29.md).

## Documentación

- [`docs/arquitectura.md`](docs/arquitectura.md) — capas, DI, flujos de datos, seguridad, decisiones de diseño.
- [`docs/estructura.md`](docs/estructura.md) — árbol de paquetes y responsabilidad de cada uno.
- [`docs/componentes.md`](docs/componentes.md) — clases e interfaces principales, APIs públicas.
- [`docs/changelog.md`](docs/changelog.md) — historial de cambios por fase.
- [`docs/MIGRATION_PLAN.md`](docs/MIGRATION_PLAN.md) — plan de migración completo (inventario RN, bugs evitados, hallazgos de backend).
- [`docs/review_carryover.md`](docs/review_carryover.md) — ítems de revisión pendientes para fases futuras.
- [`docs/backend/`](docs/backend/) — esquema en vivo de Supabase y contexto de los hallazgos B1/B2.
