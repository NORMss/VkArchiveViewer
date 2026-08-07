import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

dependencies {
    implementation(projects.shared)

    implementation(compose.desktop.currentOs)
    implementation(libs.kotlinx.coroutinesSwing)

    implementation(libs.compose.uiToolingPreview)
}

compose.desktop {
    application {
        mainClass = "ru.normno.vkarchivereader.MainKt"

        nativeDistributions {
            // Dmg — macOS, Exe — Windows installer, Deb — Linux.
            targetFormats(TargetFormat.Dmg, TargetFormat.Exe, TargetFormat.Deb)
            packageName = "VkArchiveReader"
            packageVersion = "1.0.0"
            description = "Удобный просмотр сообщений и медиа из архива выгрузки ВКонтакте"
            vendor = "normno"

            windows {
                menuGroup = "VkArchiveReader"
                // Stable UUID so future versions upgrade instead of installing twice.
                upgradeUuid = "5f7b2c1e-6a3d-4e28-9c11-2f0b8a4d7e93"
                shortcut = true
                // NB: no dirChooser — it makes jpackage emit InstallDirNotEmptyDlg,
                // on which WiX 3.11 light.exe fails with exit code 311.
            }
            macOS {
                bundleID = "ru.normno.vkarchivereader"
            }
        }
    }
}