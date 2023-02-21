/*
 * This file was generated by the Gradle 'init' task.
 *
 * This project uses @Incubating APIs which are subject to change.
 */
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
    compileOnly("org.elasticsearch:elasticsearch:${project.properties["elasticsearchVersion"]}")
    implementation("org.apache.velocity:velocity-engine-core:2.3")
    yamlRestTestRuntimeOnly("junit:junit:4.13.2") {
        exclude("org.hamcrest")
    }
    yamlRestTestRuntimeOnly("org.hamcrest:hamcrest:2.2")
    yamlRestTestRuntimeOnly("org.apache.logging.log4j:log4j-core:2.20.0")
}
