plugins {
    kotlin("multiplatform")
    kotlin("plugin.serialization")
}

kotlin {
    jvm()
    // Etapa 2: aquí se agregan androidTarget(), wasmJs() e iOS para los clientes.
    sourceSets {
        commonMain.dependencies {
            api("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
        }
    }
}
