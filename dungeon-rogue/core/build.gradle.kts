plugins {
    `java-library`
    application
    id("org.jetbrains.kotlin.jvm")
    id("org.jetbrains.kotlin.plugin.serialization")
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

dependencies {
    api(libs.kotlinx.serialization.json)
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}

application {
    // Runner testuale: consente di giocare e collaudare il motore senza Android.
    mainClass.set("com.changewave.dungeon.cli.TerminalGame")
}

// Esportazione della colonna sonora in WAV, per ascoltarla senza Android.
tasks.register<JavaExec>("runMusicExport") {
    group = "application"
    description = "Genera file WAV della musica procedurale per le varie profondita'"
    mainClass.set("com.changewave.dungeon.cli.MusicExport")
    classpath = sourceSets["main"].runtimeClasspath
}

tasks.test {
    useJUnitPlatform()
    testLogging { events("passed", "failed", "skipped") }
}
