buildscript {
    dependencies {
        // AGP 9 has built-in Kotlin; this pins it to the same version as the Compose compiler plugin.
        classpath(libs.kotlin.gradle)
    }
}

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
}
