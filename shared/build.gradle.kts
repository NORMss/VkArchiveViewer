import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidMultiplatformLibrary)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

// Which OpenCV/OpenBLAS native binaries to bundle for the desktop (jvm) build.
// CI passes -Pjavacpp.platform=<macosx-arm64|windows-x86_64|linux-x86_64> so each
// installer ships only its own OS's natives; locally we auto-detect the host.
// This is what keeps the desktop installer ~130 MB instead of ~460 MB.
val javacppPlatform: String = (project.findProperty("javacpp.platform") as String?)
    ?: run {
        val os = System.getProperty("os.name").lowercase()
        val arch = System.getProperty("os.arch").lowercase()
        val a = if (arch == "aarch64" || arch == "arm64") "arm64" else "x86_64"
        when {
            os.contains("mac") || os.contains("darwin") -> "macosx-$a"
            os.contains("win") -> "windows-x86_64"
            else -> "linux-$a"
        }
    }

kotlin {
    listOf(
        iosArm64(),
        iosSimulatorArm64()
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "Shared"
            isStatic = true
        }
    }
    
    jvm()
    
    js {
        browser()
    }
    
    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        browser()
    }
    
    androidLibrary {
       namespace = "ru.normno.vkarchivereader.shared"
       compileSdk = libs.versions.android.compileSdk.get().toInt()
       minSdk = libs.versions.android.minSdk.get().toInt()
    
       compilerOptions {
           jvmTarget = JvmTarget.JVM_11
       }
       androidResources {
           enable = true
       }
       withHostTest {
           isIncludeAndroidResources = true
       }
    }
    
    sourceSets {
        androidMain.dependencies {
            implementation(libs.compose.uiToolingPreview)
            implementation(libs.ktor.client.okhttp)
            implementation(libs.androidx.activity.compose)
        }
        commonMain.dependencies {
            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.material3)
            implementation(libs.compose.ui)
            // Multiplatform system-back / predictive-back handling (BackHandler).
            implementation("org.jetbrains.compose.ui:ui-backhandler:${libs.versions.composeMultiplatform.get()}")
            implementation(libs.compose.components.resources)
            implementation(libs.compose.uiToolingPreview)
            implementation(libs.androidx.lifecycle.viewmodelCompose)
            implementation(libs.androidx.lifecycle.runtimeCompose)

            implementation(libs.kotlinx.coroutinesCore)

            // Image loading
            implementation(libs.coil.compose)
            implementation(libs.coil.networkKtor)
            implementation(libs.ktor.client.core)
            implementation(libs.okio)

            // Dependency injection
            implementation(libs.koin.core)
            implementation(libs.koin.compose)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
        jvmMain.dependencies {
            implementation(libs.ktor.client.cio)
            // Desktop face recognition (local, on-device)
            implementation(libs.sqlite.jdbc)
            // OpenCV Java classes (pulls javacpp + openblas Java classes transitively)...
            implementation(libs.bytedeco.opencv)
            // ...plus native binaries for the CURRENT platform only. Bundling every
            // platform (via opencv-platform) added ~380 MB of unused .so/.dll/.dylib.
            val opencvV = libs.versions.bytedecoOpencv.get()
            val openblasV = libs.versions.bytedecoOpenblas.get()
            val javacppV = libs.versions.javacpp.get()
            implementation("org.bytedeco:opencv:$opencvV:$javacppPlatform")
            implementation("org.bytedeco:openblas:$openblasV:$javacppPlatform")
            implementation("org.bytedeco:javacpp:$javacppV:$javacppPlatform")
        }
        webMain.dependencies {
            implementation(libs.kotlinx.browser)
        }
        jsMain.dependencies {
            implementation(libs.wrappers.browser)
            implementation(libs.ktor.client.js)
        }
        iosMain.dependencies {
            implementation(libs.ktor.client.darwin)
        }
    }
}

dependencies {
    androidRuntimeClasspath(libs.compose.uiTooling)
}

// Forward the opt-in flag for the (network-hitting) face detection integration test.
tasks.withType<Test>().configureEach {
    System.getProperty("runFaceIntegration")?.let { systemProperty("runFaceIntegration", it) }
}