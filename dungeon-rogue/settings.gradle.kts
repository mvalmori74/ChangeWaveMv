pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.PREFER_SETTINGS)
    repositories {
        mavenCentral()
        google()
    }
}

rootProject.name = "dungeon-rogue"
include(":core")

// Il modulo :app richiede Android SDK + Google Maven (dl.google.com).
// Dove non sono disponibili (CI headless, container senza SDK) il modulo viene
// escluso, cosi' `./gradlew :core:test` resta eseguibile. Override esplicito:
//   -PskipAndroid=true   (forza esclusione)
//   -PskipAndroid=false  (forza inclusione)
val explicitSkip: String? = providers.gradleProperty("skipAndroid").orNull
val sdkDetected: Boolean =
    System.getenv("ANDROID_HOME") != null ||
        System.getenv("ANDROID_SDK_ROOT") != null ||
        file("local.properties").exists()

val includeAndroid = when (explicitSkip) {
    "true" -> false
    "false" -> true
    else -> sdkDetected
}

// Il build script del root legge questa property per decidere se mettere
// l'Android Gradle Plugin sul classpath della build.
System.setProperty("dungeon.androidEnabled", includeAndroid.toString())

if (includeAndroid) {
    include(":app")
} else {
    logger.lifecycle("[dungeon-rogue] Android SDK non rilevato: modulo :app escluso dalla build.")
}
