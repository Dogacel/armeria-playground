plugins {
    id("armeria.playground.kotlin-application-conventions")
    id("org.graalvm.buildtools.native")
}

dependencies {
    implementation(libs.kotlinx.coroutines.core)

    // Core Armeria dependencies
    implementation(armeriaDeps.armeria)
    implementation(armeriaDeps.armeria.kotlin)

    // Logging
    runtimeOnly(armeriaDeps.logback14)
}

application {
    // Define the main class for the application.
    mainClass.set("armeria.playground.app.AppKt")
}

graalvmNative {
    toolchainDetection = true
    binaries {
        create("main-vm") {
            javaLauncher =
                javaToolchains.launcherFor {
                    languageVersion = JavaLanguageVersion.of(21)
                    vendor = JvmVendorSpec.GRAAL_VM
                }
        }
    }
}
