plugins {
    // Apply the foojay-resolver plugin to allow automatic download of JDKs
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.7.0"
}

rootProject.name = "armeria-playground"
val armeriaVersion = "1.27.0"

dependencyResolutionManagement {
    repositories {
        mavenCentral()
    }

    versionCatalogs {
        create("armeriaDeps") {
            from("com.linecorp.armeria:armeria-version-catalog:$armeriaVersion")
        }
    }
}

include(
    "authentication",
    "logging-context",
    "service-request-context",
    "suspend-http-service",
    "utilities",
)
