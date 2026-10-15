rootProject.name = "salones-upt"

pluginManagement {
    repositories { gradlePluginPortal(); google(); mavenCentral() }
}
dependencyResolutionManagement {
    repositories { google(); mavenCentral() }
}
include(":shared", ":server")
