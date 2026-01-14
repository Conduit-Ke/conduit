# Room
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-dontwarn androidx.room.paging.**

# Compose
-dontwarn androidx.compose.**
-keep class androidx.compose.** { *; }

# Kotlinx Serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keep,includedescriptorclasses class com.sms.edgenode.**$$serializer { *; }
-keepclassmembers class com.sms.edgenode.** {
    *** Companion;
}
-keepclasseswithmembers class com.sms.edgenode.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Security
-keep class com.sms.edgenode.security.** { *; }
-keep class javax.crypto.** { *; }

# Firebase
-keep class com.google.firebase.** { *; }
-keep class com.google.android.gms.** { *; }

# SQLCipher
-keep class net.sqlcipher.** { *; }
-keep class net.sqlcipher.database.** { *; }
