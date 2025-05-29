plugins {
    id("armeria.playground.kotlin-application-conventions")
    id("org.pkl-lang") version "0.26.2"
}

dependencies {
    implementation(libs.kotlinx.coroutines.core)

    // Core Armeria dependencies
    implementation(armeriaDeps.armeria)
    implementation(armeriaDeps.armeria.kotlin)

    // GraphQL
    implementation(armeriaDeps.armeria.graphql)
    implementation(armeriaDeps.armeria.graphql.protocol)

    // Annotation Processing
    implementation("io.github.classgraph:classgraph:4.8.174")
    implementation(kotlin("reflect"))

    // Config
    implementation("org.pkl-lang:pkl-config-kotlin:0.26.2")

    // Logging
    runtimeOnly(armeriaDeps.logback14)
}

pkl {
    kotlinCodeGenerators {
        register("configClasses") {
            sourceModules.set(files("src/main/resources/application_config.pkl"))
            generateKdoc.set(true)
        }
    }

    tests {
        register("testPkl") {
            sourceModules.addAll(files("application_config_local.pkl", "application_config_prod.pkl"))
            junitReportsDir.set(layout.buildDirectory.dir("reports"))
            overwrite.set(false)
        }
    }
}

application {
    // Define the main class for the application.
    mainClass.set("armeria.playground.app.AppKt")
}
