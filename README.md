# Spotter (Android nativo)

Spotter es una app de seguimiento de entrenamientos de gimnasio. Este repositorio es la
reescritura nativa en Kotlin/Jetpack Compose de la app original en React Native/Expo (fuente,
solo lectura: `E:\Spotter`), manteniendo el mismo backend Supabase sin cambios de esquema.

## Estado

**FASES 1 a 4 de 7 implementadas y aprobadas** (revisión del 2026-09-26). Ver el plan completo
en [`docs/MIGRATION_PLAN.md`](docs/MIGRATION_PLAN.md) y el detalle de arquitectura/estructura en
`docs/`.

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
  finalizar/descartar, y sincronización offline del outbox con WorkManager.
- Perfil mínimo (identidad + cerrar sesión, que cancela la alarma y la sincronización).

**Pendiente (fases 5 a 7, ver "Planificado" en `docs/*.md`):** historial, progreso, dashboard,
perfil completo (datos físicos, preferencia kg/lb, `SignOutUseCase` con limpieza de Room),
compartir/importar rutinas (código e IA), exportación de entrenamientos y endurecimiento de
release. Las pantallas de Dashboard, Historial y Progreso hoy son placeholders (`ComingSoonScreen`);
el banner "Entrenamiento en curso" vive por ahora en la pantalla de Rutinas.

No verificado todavía en un dispositivo/emulador real (solo build + tests unitarios).

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

Desde `E:\Spoter Kotlin\` (con el JDK correcto en el PATH):

```bash
./gradlew assembleDebug            # APK debug
./gradlew testDebugUnitTest         # tests unitarios (355 @Test tras FASE 4)
./gradlew assembleRelease           # APK release con R8 (falla si faltan las claves de Supabase)
./gradlew assembleDebug testDebugUnitTest   # build + tests en un solo paso
```

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
