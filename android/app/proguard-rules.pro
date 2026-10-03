# Release builds: strip verbose/info/debug logging. Auth routes, breach
# telemetry and sensor vectors are useful on a dev device but have no place in a
# shipped APK. Warnings and errors (Log.w / Log.e) are kept for crash triage.
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
    public static int i(...);
}
