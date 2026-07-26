/*
 * I plugin vanno dichiarati anche qui, con `apply false`: è così che finiscono sul classpath
 * della build con una versione nota. Senza questa riga per il multiplatform, Gradle lo trova
 * già caricato dagli altri plugin Kotlin "con versione sconosciuta" e rifiuta di applicarlo.
 */
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.compose.compiler) apply false
}
