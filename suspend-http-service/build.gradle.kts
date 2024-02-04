plugins {
    id("armeria.playground.kotlin-application-conventions")
}

dependencies {
    implementation(libs.kotlinx.coroutines.core)

    // Core Armeria dependencies
    implementation(armeriaDeps.armeria)
    implementation(armeriaDeps.armeria.kotlin)

    // Logging
    runtimeOnly(armeriaDeps.logback14)
    implementation(armeriaDeps.armeria.logback14)
}

application {
    // Define the main class for the application.
    mainClass.set("armeria.playground.app.AppKt")
}
