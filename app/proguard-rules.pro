# R8 / ProGuard Keep Rules for empee3

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

# JAudioTagger
-keep class org.jaudiotagger.** { *; }
-dontwarn org.jaudiotagger.**
