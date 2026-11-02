import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompilationTask

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidLibrary)
    alias(libs.plugins.kotlinSerialization)
    alias(libs.plugins.sqldelight)
}

val generatedEnvDir = layout.buildDirectory.dir("generated/supabaseEnv/commonMain/kotlin")
val rootEnvFile = rootProject.file(".env")

val generateSupabaseEnv = tasks.register("generateSupabaseEnv") {
    inputs.files(rootEnvFile).optional()
    outputs.dir(generatedEnvDir)

    doLast {
        val envMap = mutableMapOf<String, String>()
        if (rootEnvFile.exists()) {
            rootEnvFile.readLines(Charsets.UTF_8).forEach { rawLine ->
                val line = rawLine.trim()
                if (line.isNotEmpty() && !line.startsWith("#") && line.contains("=")) {
                    val idx = line.indexOf('=')
                    val key = line.substring(0, idx).trim()
                    val value = line.substring(idx + 1).trim().removeSurrounding("\"").removeSurrounding("'")
                    envMap[key] = value
                }
            }
        }

        fun escapeKotlin(value: String): String =
            value.replace("\\", "\\\\").replace("\"", "\\\"").replace("$", "\\$")

        val rawAnon = envMap["SUPABASE_ANON_KEY"] ?: System.getenv("SUPABASE_ANON_KEY") ?: ""
        val rawPublic = envMap["SUPABASE_PUBLIC_KEY"] ?: System.getenv("SUPABASE_PUBLIC_KEY") ?: ""
        // Secret Keys (sb_secret_...) niemals in den Client-Build (Web/Mobile) einbetten
        val safeAnonKey = if (rawAnon.startsWith("sb_secret_", ignoreCase = true)) "" else escapeKotlin(rawAnon)
        val publicKey = if (rawPublic.startsWith("sb_secret_", ignoreCase = true)) "" else escapeKotlin(rawPublic)
        val supabaseUrl = escapeKotlin(envMap["SUPABASE_URL"] ?: System.getenv("SUPABASE_URL") ?: "")

        val outFile = generatedEnvDir.get().file("com/handball/stattracker/config/SupabaseEnvGenerated.kt").asFile
        outFile.parentFile.mkdirs()
        outFile.writeText(
            """
            package com.handball.stattracker.config

            internal object SupabaseEnvGenerated {
                const val SUPABASE_ANON_KEY: String = "$safeAnonKey"
                const val SUPABASE_PUBLIC_KEY: String = "$publicKey"
                const val SUPABASE_URL: String = "$supabaseUrl"
            }
            """.trimIndent() + "\n",
            Charsets.UTF_8
        )
    }
}

tasks.withType<KotlinCompilationTask<*>>().configureEach {
    dependsOn(generateSupabaseEnv)
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

    js {
        browser()
    }

    iosArm64()
    iosSimulatorArm64()

    sourceSets {
        commonMain {
            kotlin.srcDir(generatedEnvDir)
            dependencies {
                api(libs.kotlinx.coroutines.core)
                api(libs.kotlinx.serialization.json)
                api(libs.kotlinx.datetime)
                api(libs.ktor.client.core)
                api(libs.ktor.client.content.negotiation)
                api(libs.ktor.serialization.kotlinx.json)
                api(libs.sqldelight.runtime)
            }
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
        androidMain.dependencies {
            implementation(libs.ktor.client.cio)
            implementation(libs.sqldelight.android.driver)
        }
        named("desktopMain").dependencies {
            implementation(libs.ktor.client.cio)
            implementation(libs.sqldelight.sqlite.driver)
            implementation(libs.sqlite.jdbc)
        }
        jsMain.dependencies {
            implementation(libs.ktor.client.js)
        }
        iosMain.dependencies {
            implementation(libs.ktor.client.darwin)
            implementation(libs.sqldelight.native.driver)
        }
    }
}

android {
    namespace = "com.handball.stattracker.shared"
    compileSdk = libs.versions.android.compileSdk.get().toInt()
    defaultConfig {
        minSdk = libs.versions.android.minSdk.get().toInt()
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

sqldelight {
    databases {
        create("HandballDatabase") {
            packageName.set("com.handball.stattracker.db")
        }
    }
}
