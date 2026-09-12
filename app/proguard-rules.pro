# kotlinx.serialization: keep generated serializers for our DTOs
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class kotlinx.serialization.json.** { kotlinx.serialization.KSerializer serializer(...); }
-keep,includedescriptorclasses class dev.mausam.home.**$$serializer { *; }
-keepclassmembers class dev.mausam.home.** { *** Companion; }
-keepclasseswithmembers class dev.mausam.home.** { kotlinx.serialization.KSerializer serializer(...); }

# Retrofit: keep generic signatures so suspend/Response<T> return types survive R8 full mode
-keepattributes Signature, Exceptions
-if interface * { @retrofit2.http.* public *** *(...); }
-keep,allowoptimization,allowshrinking,allowobfuscation class <3>

# Glance / WorkManager entry points are declared in the manifest and kept by AGP.
