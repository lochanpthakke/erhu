# R8 / ProGuard Keep Rules for erhu

# Room Persistence
-keep class * extends androidx.room.RoomDatabase
-dontwarn androidx.room.paging.**

# Media3 / ExoPlayer
-keep class androidx.media3.** { *; }
-dontwarn androidx.media3.**

# Hilt Dependency Injection
-keep class * extends dagger.hilt.android.internal.managers.ViewComponentManager$FragmentContextWrapper
-keepclassmembers,allowobfuscation class * {
    @javax.inject.Inject <init>(...);
}

# Retrofit & Kotlinx Serialization
-keepattributes Signature, InnerClasses, EnclosingMethod
-keepclassmembers class * {
    @kotlinx.serialization.Serializable <fields>;
}
-keepclassmembers class * {
    @retrofit2.http.* <methods>;
}

# Protobuf lite (DataStore protos): the generated message classes are read by field name at
# runtime, and the protobuf-javalite jar ships no keep rules of its own.
-keepclassmembers class * extends com.google.protobuf.GeneratedMessageLite { <fields>; }

# MediaPipe (VYBE's on-device embedder): its native code looks Java classes and methods up by name
# over JNI, and the tasks-text/tasks-core AARs ship no keep rules of their own.
-keep class com.google.mediapipe.** { *; }
-dontwarn com.google.mediapipe.**
# MediaPipe logs through Flogger, which finds its caller on the stack by class name: renamed, it fails
# with "no caller found on the stack" in Graph.<clinit> (seen in a release build on 2026-10-17).
-keep class com.google.common.flogger.** { *; }
-dontwarn com.google.common.flogger.**

# snakeyaml-engine (LRCLIB word-timed lyrics): no keep rules shipped; keep it intact.
-keep class org.snakeyaml.engine.** { *; }

# JAudioTagger
-keep class org.jaudiotagger.** { *; }
-dontwarn org.jaudiotagger.**

# Navigation routes are @Serializable objects looked up by type. The library's own rules keep an
# object's serializer only while its INSTANCE is used somewhere, so a route that is registered but
# no longer navigated to directly (e.g. removed from the drawer) lost it and crashed at startup.
-keepclassmembers @kotlinx.serialization.Serializable class com.erhu.erhu.navigation.** {
    public static ** INSTANCE;
    kotlinx.serialization.KSerializer serializer(...);
}
-keepclassmembers class com.erhu.erhu.navigation.**$Companion {
    kotlinx.serialization.KSerializer serializer(...);
}
