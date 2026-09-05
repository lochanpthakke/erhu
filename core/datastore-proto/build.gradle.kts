plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.protobuf)
}

protobuf {
    protoc {
        artifact = "com.google.protobuf:protoc:3.25.3"
    }
    generateProtoTasks {
        all().forEach { task ->
            task.builtins {
                getByName("java") {
                    option("lite")
                }
            }
        }
    }
}

dependencies {
    implementation(libs.protobuf.javalite)
}
