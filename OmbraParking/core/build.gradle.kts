import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    /*
     * Il target JVM serve all'app Android e, soprattutto, ai test: la matematica delle ombre
     * si verifica su una macchina qualsiasi, senza emulatori né Mac.
     */
    jvm {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }

    /*
     * I target iOS si compilano solo su macOS: la configurazione resta comunque qui, così
     * su un Mac non serve toccare niente.
     *
     * Il framework è statico perché è la forma che Xcode collega senza passaggi aggiuntivi
     * di firma, e si chiama OmbraCore: è il nome che il codice Swift importa.
     */
    listOf(iosArm64(), iosSimulatorArm64(), iosX64()).forEach { target ->
        target.binaries.framework {
            baseName = "OmbraCore"
            isStatic = true
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(libs.kotlinx.serialization.json)
            api(libs.kotlinx.datetime)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}
