plugins {
    id("armeria.playground.kotlin-application-conventions")
}

dependencies {
    implementation(libs.kotlinx.coroutines.core)

    // Core Armeria dependencies
    implementation(armeriaDeps.armeria)
    implementation(armeriaDeps.armeria.kotlin)

    // GraphQL
    implementation(armeriaDeps.armeria.graphql)
    implementation(armeriaDeps.armeria.graphql.protocol)

    // Logging
    runtimeOnly(armeriaDeps.logback14)
}

application {
    // Define the main class for the application.
    mainClass.set("armeria.playground.app.AppKt")
}
