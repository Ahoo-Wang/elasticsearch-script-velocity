plugins {
    id("elasticsearch.esplugin")
    id("elasticsearch.yaml-rest-test")
}

esplugin {
    name = "elasticsearch-script-velocity"
    description = "Elasticsearch Script Plugin for Velocity"
    classname = "me.ahoo.elasticsearch.script.velocity.VelocityPlugin"
    version = properties["version"] as String
    licenseFile = rootProject.file("LICENSE")
}

repositories {
    mavenLocal()
    mavenCentral()
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
}

dependencies {
    compileOnly(libs.elasticsearch)
    implementation(libs.velocity)
    yamlRestTestRuntimeOnly(libs.junit) {
        exclude("org.hamcrest")
    }
    yamlRestTestRuntimeOnly(libs.hamcrest)
    yamlRestTestRuntimeOnly(libs.log4j)
}
