# kotlinx.serialization: i serializzatori generati vanno preservati
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class com.changewave.dungeon.** {
    *** Companion;
}
-keepclasseswithmembers class com.changewave.dungeon.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.changewave.dungeon.**$$serializer { *; }
