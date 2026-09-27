# Spotter R8 rules. Every library in this app ships its own consumer rules (verified against the
# artifacts for FASE 7): kotlinx.serialization 1.11 (Companion/serializer()/INSTANCE/$$serializer,
# so no rules for our @Serializable DTOs, Room payloads or Navigation routes), Ktor 3.5,
# OkHttp 5 (also -dontwarn org.bouncycastle/conscrypt/openjsse/javax.annotation globally), Tink,
# Coil 3, coroutines, Hilt, and the AndroidX/Media3/Credentials artifacts. Only add a rule here for
# something R8 actually reports (app/build/outputs/mapping/release/missing_rules.txt), with a comment.

# Readable release stack traces (retrace with mapping.txt).
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Strip android.util.Log calls in release (ours are already gated by BuildConfig.DEBUG in
# AndroidLogger; this also silences libraries) so no tokens or PII reach Logcat.
-assumenosideeffects class android.util.Log {
    public static *** v(...);
    public static *** d(...);
    public static *** i(...);
    public static *** w(...);
    public static *** e(...);
    public static *** wtf(...);
    public static *** println(...);
}

# Ktor: io.ktor.util.debug.IntellijIdeaDebugDetector references the JVM-only java.lang.management
# API (absent from android.jar); never executed on Android. Without this R8 fails with "Missing class".
-dontwarn java.lang.management.ManagementFactory
-dontwarn java.lang.management.RuntimeMXBean
