plugins {
    id("elasticsearch.esplugin")
    id("elasticsearch.yaml-rest-test")
    jacoco
    id("me.champeau.jmh") version "0.7.2"
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
    yamlRestTestRuntimeOnly(libs.junit.jupiter.api)
    yamlRestTestRuntimeOnly(libs.hamcrest)
    yamlRestTestRuntimeOnly(libs.log4j)
    testImplementation(libs.junit.jupiter.api)
    testImplementation(libs.junit.jupiter.params)
    testRuntimeOnly(libs.junit.jupiter.engine)
    jmh("org.openjdk.jmh:jmh-core:1.37")
    jmh("org.openjdk.jmh:jmh-generator-annprocess:1.37")
}

tasks.test {
    useJUnitPlatform()
    finalizedBy(tasks.jacocoTestReport)
}
tasks.jacocoTestReport {
    reports {
        xml.required = true
    }
    dependsOn(tasks.test)
}


jmh {
    val DELIMITER = ',';
    val JMH_INCLUDES_KEY = "jmhIncludes"
    val JMH_EXCLUDES_KEY = "jmhExcludes"
    val JMH_THREADS_KEY = "jmhThreads"
    val JMH_MODE_KEY = "jmhMode"

    if (project.hasProperty(JMH_INCLUDES_KEY)) {
        val jmhIncludes = project.properties[JMH_INCLUDES_KEY].toString().split(DELIMITER)
        includes.set(jmhIncludes)
    }
    if (project.hasProperty(JMH_EXCLUDES_KEY)) {
        val jmhExcludes = project.properties[JMH_EXCLUDES_KEY].toString().split(DELIMITER)
        excludes.set(jmhExcludes)
    }

    jmhVersion.set("1.37")
    warmupIterations.set(1)
    iterations.set(1)
    resultFormat.set("json")

    var jmhMode = listOf(
        "thrpt"
    )
    if (project.hasProperty(JMH_MODE_KEY)) {
        jmhMode = project.properties[JMH_MODE_KEY].toString().split(DELIMITER)
    }
    benchmarkMode.set(jmhMode)
    var jmhThreads = 1
    if (project.hasProperty(JMH_THREADS_KEY)) {
        jmhThreads = Integer.valueOf(project.properties[JMH_THREADS_KEY].toString())
    }
    threads.set(jmhThreads)
    fork.set(1)
}