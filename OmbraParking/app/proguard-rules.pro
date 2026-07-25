# osmdroid carica alcune classi per riflessione: senza questi keep la mappa resta vuota
# quando si attiva la minificazione.
-keep class org.osmdroid.** { *; }
-dontwarn org.osmdroid.**

# I modelli Overpass sono deserializzati da kotlinx.serialization.
-keepattributes *Annotation*, InnerClasses
-keepclassmembers class com.changewave.ombraparking.core.osm.** {
    *** Companion;
}
-keepclasseswithmembers class com.changewave.ombraparking.core.osm.** {
    kotlinx.serialization.KSerializer serializer(...);
}
