pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "HexaEmpresarialEdition"

include(":domain")

// `-PdomainOnly=true` builds only the pure-Kotlin domain module (no Android SDK required).
if (providers.gradleProperty("domainOnly").orNull != "true") {
    include(":app")
}
