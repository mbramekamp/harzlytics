import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

kotlin {
    androidTarget {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_21)
        }
    }

    jvm("desktop") {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_21)
        }
    }

    listOf(
        iosArm64(),
        iosSimulatorArm64()
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "ComposeApp"
            isStatic = true
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(projects.shared)
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.materialIconsExtended)
            implementation(compose.ui)
            implementation(compose.components.resources)
        }
        androidMain.dependencies {
            implementation(libs.androidx.activity.compose)
        }
        named("desktopMain").dependencies {
            implementation(compose.desktop.currentOs)
            implementation(libs.kotlinx.coroutines.swing)
        }
        named("desktopTest") {
            // Demo-Daten für die Bildschirm-Vorschau (liegen im Test-Quellordner von :shared)
            kotlin.srcDir("../shared/src/commonTest/kotlin/com/handball/stattracker/repository")
            dependencies {
                implementation(kotlin("test"))
            }
        }
    }
}

android {
    namespace = "com.handball.stattracker"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "com.handball.stattracker"
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        versionCode = 1
        versionName = "1.0.0"
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
    testOptions {
        unitTests.isIncludeAndroidResources = true
    }
}

androidComponents {
    beforeVariants { variantBuilder ->
        (variantBuilder as? com.android.build.api.variant.HasUnitTestBuilder)?.enableUnitTest = true
    }
}

compose.desktop {
    application {
        mainClass = "com.handball.stattracker.MainKt"

        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "HandballStatTracker"
            packageVersion = "1.0.0"
        }
    }
}

tasks.withType<Test>().configureEach {
    // Vorschau-Bilder ohne Fenster rendern (Skia-Software-Rendering)
    systemProperty("java.awt.headless", "true")
    systemProperty("screenshot.dir", layout.buildDirectory.dir("screenshots").get().asFile.absolutePath)
}

// Zeichnet die Screens neu und kopiert die für das README gewählten nach docs/screenshots/
val docScreenshots = listOf(
    "desktop-hell-home", "desktop-dunkel-home", "desktop-hell-spiel", "desktop-hell-kader",
    "desktop-hell-tabelle", "mobil-hell-home", "mobil-dunkel-home", "mobil-hell-spieler-profil",
    "dialog-admin-konto", "dialog-admin-verein", "dialog-admin-benutzer"
)
tasks.register<Copy>("updateDocScreenshots") {
    group = "documentation"
    description = "Rendert die Screens (desktopTest) und kopiert die README-Bilder nach docs/screenshots"
    dependsOn("desktopTest")
    from(layout.buildDirectory.dir("screenshots")) { include(docScreenshots.map { "$it.png" }) }
    into(rootProject.layout.projectDirectory.dir("docs/screenshots"))
}
