# CopyNest ProGuard / R8 Rules

# Keep essential attributes for Reflection, Serialization, and Stack traces
-keepattributes *Annotation*, Signature, InnerClasses, EnclosingMethod, SourceFile, LineNumberTable

# -----------------------------------------------------------------------------
# CopyNest App Models & Data Layer
# -----------------------------------------------------------------------------
-keep class com.zahidcodes.copynest.data.** { *; }
-keepclassmembers class com.zahidcodes.copynest.data.** { *; }

# -----------------------------------------------------------------------------
# AndroidX Room
# -----------------------------------------------------------------------------
# Keep all Entity classes and their fields/constructors
-keep @androidx.room.Entity class * { *; }
-keepclassmembers @androidx.room.Entity class * { *; }

# Keep all Dao interfaces and methods
-keep @androidx.room.Dao interface * { *; }
-keepclassmembers @androidx.room.Dao interface * { *; }

# Keep RoomDatabase subclasses and public constructors
-keep class * extends androidx.room.RoomDatabase {
    public <init>();
    public <init>(...);
    *;
}

# Keep KSP generated Room Database and DAO implementations (_Impl classes)
# Room uses reflection (Class.forName) to find ClipDatabase_Impl
-keep class **_Impl {
    public <init>(...);
    *;
}

# Keep Room Database constructors (Room 2.7+)
-keep class * implements androidx.room.RoomDatabaseConstructor* { *; }

# Keep annotated Room methods and fields
-keepclassmembers class * {
    @androidx.room.* <fields>;
    @androidx.room.* <methods>;
}

-dontwarn androidx.room.paging.**

# -----------------------------------------------------------------------------
# Moshi
# -----------------------------------------------------------------------------
-dontwarn com.squareup.moshi.**
# Keep Moshi core classes
-keep class com.squareup.moshi.** { *; }
-keep interface com.squareup.moshi.** { *; }

# Keep classes and interfaces annotated with @JsonClass
-keep @com.squareup.moshi.JsonClass class * { *; }
-keep @com.squareup.moshi.JsonClass interface * { *; }
-keepclassmembers @com.squareup.moshi.JsonClass class * { *; }

# Keep generated Moshi JsonAdapters
-keep class * extends com.squareup.moshi.JsonAdapter {
    public <init>(...);
}
-keep class **JsonAdapter {
    public <init>(...);
    *;
}

# Keep annotated Moshi fields and adapter methods
-keepclassmembers class * {
    @com.squareup.moshi.Json <fields>;
    @com.squareup.moshi.Json <methods>;
    @com.squareup.moshi.FromJson <methods>;
    @com.squareup.moshi.ToJson <methods>;
}

# Keep Kotlin metadata needed for reflection/adapter generation
-keep class kotlin.Metadata { *; }
-dontwarn javax.annotation.**

# -----------------------------------------------------------------------------
# Retrofit & OkHttp
# -----------------------------------------------------------------------------
-dontwarn retrofit2.**
-keep class retrofit2.** { *; }
-keepclasseswithmembers class * {
    @retrofit2.http.* <methods>;
}
-dontwarn okhttp3.**
-dontwarn okio.**
-keep class okhttp3.** { *; }

# -----------------------------------------------------------------------------
# Kotlin Coroutines
# -----------------------------------------------------------------------------
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembernames class kotlinx.** {
    volatile <fields>;
}

# -----------------------------------------------------------------------------
# Google Play Services Auth, Drive Sync & In-App Updates
# -----------------------------------------------------------------------------
-keep class com.google.android.gms.auth.** { *; }
-keep class com.google.android.gms.common.** { *; }
-dontwarn com.google.android.gms.**
-keep class com.google.android.play.core.** { *; }
-keep interface com.google.android.play.core.** { *; }
-dontwarn com.google.android.play.core.**

