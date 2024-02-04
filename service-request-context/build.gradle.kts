plugins {
    id("armeria.playground.kotlin-application-conventions")

    alias(libs.plugins.protobuf)
}

dependencies {
    implementation(libs.kotlinx.coroutines.core)

    // Core Armeria dependencies
    implementation(armeriaDeps.armeria)
    implementation(armeriaDeps.armeria.kotlin)

    // Grpc
    implementation(armeriaDeps.armeria.grpc)
    implementation(armeriaDeps.armeria.grpc.kotlin)
    implementation(libs.bundles.grpc.kotlin)

    // Logging
    runtimeOnly(armeriaDeps.logback14)
}

application {
    // Define the main class for the application.
    mainClass.set("armeria.playground.app.AppKt")
}

protobuf {
    protoc {
        artifact = libs.protoc.asProvider().get().toString()
    }
    plugins {
        create("grpc") {
            artifact = libs.protoc.gen.grpc.java.get().toString()
        }
        create("grpckt") {
            artifact = libs.protoc.gen.grpc.kotlin.get().toString() + ":jdk8@jar"
        }
    }
    generateProtoTasks {
        all().forEach {
            it.plugins {
                create("grpc")
                create("grpckt")
            }
            it.builtins {
                create("kotlin")
            }
        }
    }
}
