# kotlinx.serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class com.worobeyko.metroekb.data.** {
    *** Companion;
}
-keepclasseswithmembers class com.worobeyko.metroekb.data.** {
    kotlinx.serialization.KSerializer serializer(...);
}
