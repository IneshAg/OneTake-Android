# OneTake release (R8) rules.
# Preserve enum values and names used in manual JSON persistence and serialization
-keepclassmembers enum com.onetake.app.models.** {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# Preserve model classes serialized via org.json
-keep class com.onetake.app.models.** { *; }

# Glance AppWidget receivers & providers
-keep class * extends androidx.glance.appwidget.GlanceAppWidgetReceiver { *; }
-keep class * extends androidx.glance.appwidget.GlanceAppWidget { *; }

# ML Kit Vision (Face Detection and Text Recognition)
-keep class com.google.mlkit.vision.** { *; }
-dontwarn com.google.mlkit.vision.**

# CameraX
-keep class androidx.camera.core.** { *; }
-keep class androidx.camera.lifecycle.** { *; }
-keep class androidx.camera.video.** { *; }
-keep class androidx.camera.camera2.** { *; }
