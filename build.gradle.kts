// Firebase BoM 35 dikompilasi dengan Kotlin 2.4, jadi compiler Kotlin dinaikkan
// dari bawaan AGP (2.2) agar metadata library Firebase bisa dibaca.
buildscript {
    dependencies {
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:2.4.0")
    }
}

// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.android.application) apply false
    id("com.google.gms.google-services") version "4.5.0" apply false
}