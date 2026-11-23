import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import java.security.MessageDigest
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
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

    // Web-Version als installierbare PWA: ./gradlew :composeApp:wasmJsBrowserDistribution
    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        outputModuleName.set("composeApp")
        browser {
            commonWebpackConfig {
                outputFileName = "composeApp.js"
            }
        }
        binaries.executable()
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
    "dialog-admin-konto", "dialog-admin-verein", "dialog-admin-benutzer", "anmeldung-hell"
)
tasks.register<Copy>("updateDocScreenshots") {
    group = "documentation"
    description = "Rendert die Screens (desktopTest) und kopiert die README-Bilder nach docs/screenshots"
    dependsOn("desktopTest")
    from(layout.buildDirectory.dir("screenshots")) { include(docScreenshots.map { "$it.png" }) }
    into(rootProject.layout.projectDirectory.dir("docs/screenshots"))
}

compose.resources {
    packageOfResClass = "com.handball.stattracker.resources"
    publicResClass = false
}

// PWA: erzeugt nach dem Web-Build sw.js mit der Liste aller App-Dateien und einer Versionskennung aus deren Inhalt,
// damit die App offline startet und Updates vollständig auf einmal kommen.
val pwaDistDir = layout.buildDirectory.dir("dist/wasmJs/productionExecutable")
val pwaTemplate = file("src/wasmJsMain/pwa/sw.template.js")
val pwaServiceWorker = tasks.register("pwaServiceWorker") {
    group = "build"
    description = "Erzeugt den Service Worker (sw.js) der Web-Version"
    inputs.file(pwaTemplate)
    doLast {
        val dir = pwaDistDir.get().asFile
        val files = dir.walkTopDown()
            .filter { it.isFile && it.name != "sw.js" && !it.name.endsWith(".map") && !it.name.endsWith(".LICENSE.txt") }
            .map { it.relativeTo(dir).invariantSeparatorsPath }
            .sorted()
            .toList()
        val digest = MessageDigest.getInstance("SHA-256")
        files.forEach { path ->
            digest.update(path.toByteArray())
            digest.update(dir.resolve(path).readBytes())
        }
        val version = digest.digest().joinToString("") { "%02x".format(it) }.take(12)
        val assets = files.joinToString(",\n  ") { "\"./$it\"" }
        dir.resolve("sw.js").writeText(pwaTemplate.readText().replace("__VERSION__", version).replace("__ASSETS__", assets))
    }
}
tasks.matching { it.name == "wasmJsBrowserDistribution" }.configureEach { finalizedBy(pwaServiceWorker) }

