# Add project specific ProGuard rules here.

# Keep data classes and models
-keep class com.cussou.autotiq.domain.model.** { *; }
-keep class com.cussou.autotiq.data.local.entity.** { *; }

# Keep Hilt generated classes
-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }
-keep class * extends dagger.hilt.android.internal.managers.ViewComponentManager$FragmentContextWrapper { *; }

# Keep Compose and ViewModel classes
-keep class androidx.lifecycle.ViewModel { *; }
-keep class androidx.compose.** { *; }

# Keep Room entities and DAOs
-keep @androidx.room.Entity class *
-keep @androidx.room.Dao class *

# Keep coroutines
-keepclassmembernames class kotlinx.** {
    volatile <fields>;
}

# Keep data class properties (for reflection)
-keepclassmembers class * {
    public <init>(...);
}

# Keep enums
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# Keep Parcelable implementations
-keepclassmembers class * implements android.os.Parcelable {
    public static final ** CREATOR;
}

# Keep serialization
-keepattributes *Annotation*, InnerClasses
-keepattributes Signature, Exception

# OsmDroid
-dontwarn org.osmdroid.**
-keep class org.osmdroid.** { *; }

# Google Play Services Location
-keep class com.google.android.gms.location.** { *; }
-dontwarn com.google.android.gms.**

# WorkManager
-keep class * extends androidx.work.Worker
-keep class * extends androidx.work.CoroutineWorker
-keepclassmembers class * extends androidx.work.Worker {
    public <init>(android.content.Context, androidx.work.WorkerParameters);
}
-keepclassmembers class * extends androidx.work.CoroutineWorker {
    public <init>(android.content.Context, androidx.work.WorkerParameters);
}

# Hilt Worker with AssistedInject (CRITICAL for release builds)
# Without these rules, @HiltWorker classes may fail to instantiate in release builds
-keep class * implements androidx.hilt.work.HiltWorkerFactory { *; }
-keep @androidx.hilt.work.HiltWorker class * { *; }
-keepclassmembers @androidx.hilt.work.HiltWorker class * {
    @dagger.assisted.AssistedInject <init>(...);
}
-keep class dagger.assisted.** { *; }

# Geofencing
-keep class com.google.android.gms.location.Geofence { *; }
-keep class com.google.android.gms.location.GeofencingRequest { *; }
-keep class com.google.android.gms.location.GeofencingClient { *; }
