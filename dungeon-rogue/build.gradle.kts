// Tutti i plugin della build stanno sullo stesso classpath (quello del progetto
// root): il plugin Kotlin per Android carica per riflessione classi dell'Android
// Gradle Plugin, quindi i due devono vivere nel medesimo classloader. Dichiararli
// in moduli diversi con la DSL `plugins {}` li separa in scope annidati e produce
// "Could not generate a decorated class for type KotlinAndroidTarget".
//
// AGP viene messo sul classpath solo quando il modulo :app fa parte della build
// (decisione presa in settings.gradle.kts, che la comunica via system property):
// si scarica unicamente da Google Maven, e il motore deve restare compilabile
// anche dove quel dominio non e' raggiungibile.
buildscript {
    // ATTENZIONE: queste versioni devono restare allineate a gradle/libs.versions.toml
    // (il version catalog non e' accessibile dentro il blocco buildscript).
    val kotlinVersion = "2.0.21"
    val androidGradlePluginVersion = "8.7.2"
    val androidEnabled = System.getProperty("dungeon.androidEnabled") == "true"

    repositories {
        mavenCentral()
        gradlePluginPortal()
        if (androidEnabled) google()
    }

    dependencies {
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:$kotlinVersion")
        classpath("org.jetbrains.kotlin:kotlin-serialization:$kotlinVersion")
        classpath("org.jetbrains.kotlin:compose-compiler-gradle-plugin:$kotlinVersion")
        if (androidEnabled) {
            classpath("com.android.tools.build:gradle:$androidGradlePluginVersion")
        }
    }
}
