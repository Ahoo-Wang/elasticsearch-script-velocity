rootProject.name = "elasticsearch-script-velocity"

buildscript {
    val elasticVersion = providers.gradleProperty("elastic.version").get()

    repositories {
        mavenCentral()
    }
    dependencies {
        classpath("org.elasticsearch.gradle:build-tools:${elasticVersion}")
    }
}
