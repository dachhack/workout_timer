# Keep kotlinx.serialization generated serializers
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class com.f3.workouttimer.** {
    *** Companion;
}
-keepclasseswithmembers class com.f3.workouttimer.** {
    kotlinx.serialization.KSerializer serializer(...);
}
