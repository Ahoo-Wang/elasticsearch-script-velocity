rootProject.name = "elasticsearch-script-velocity"

buildscript {
    repositories {
        mavenCentral()
    }
    dependencies {
        classpath("org.elasticsearch.gradle:build-tools:8.11.1")
    }
}
