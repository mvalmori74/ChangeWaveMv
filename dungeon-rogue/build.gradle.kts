// I plugin sono dichiarati qui con `apply false` per fissarne la versione una
// volta sola per tutta la build: i moduli li applicano senza ridichiarare la
// versione, evitando il conflitto "plugin already on the classpath".
//
// L'Android Gradle Plugin NON compare qui di proposito: si scarica solo da
// Google Maven e il modulo :app e' opzionale (vedi settings.gradle.kts), quindi
// dichiararlo nel root impedirebbe di compilare :core dove Google Maven non e'
// raggiungibile.
plugins {
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.compose.compiler) apply false
}
