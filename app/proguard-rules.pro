# Add project specific ProGuard rules here.
-keep class com.zahidcodes.copynets.data.** { * }
-keepclassmembers class * {
    @androidx.room.* <methods>;
    @androidx.room.* <fields>;
}
-keepattributes *Annotation*, Signature, InnerClasses, EnclosingMethod
-keepclassmembers @androidx.room.Entity class * { * }
-keepclassmembers @androidx.room.Dao interface * { * }
-keepclassmembers class * extends androidx.room.RoomDatabase { * }
