buildscript {
    // The Android plugin shares the root classloader with the Kotlin plugins below.
    if (findProperty("domainOnly") != "true") {
        repositories {
            google()
            mavenCentral()
        }
        dependencies {
            classpath(libs.agp)
        }
    }
}

plugins {
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
}
