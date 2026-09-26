# Strip logs in release builds so no tokens or PII leak through Logcat.
-assumenosideeffects class android.util.Log {
    public static *** v(...);
    public static *** d(...);
    public static *** i(...);
    public static *** w(...);
    public static *** e(...);
}

# kotlinx.serialization keeps serializer() companions for @Serializable classes automatically
# via the compiler plugin; nothing extra required here for our DTOs/models.
