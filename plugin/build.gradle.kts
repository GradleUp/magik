
plugins {
    // Apply the Java Gradle plugin development plugin to add support for developing Gradle plugins
    `java-gradle-plugin`

    // Apply the Kotlin JVM plugin to add support for Kotlin.
    id("org.jetbrains.kotlin.jvm") version "1.5.30"

    `kotlin-dsl`

    id("com.gradle.plugin-publish") version "0.15.0"

    `maven-publish`
}

repositories {
    mavenCentral()
    gradlePluginPortal()
}

dependencies {
    // Align versions of all Kotlin components
    implementation(platform(kotlin("bom")))

    // Use the Kotlin JDK 8 standard library.
    implementation(kotlin("stdlib-jdk8"))

    // Use the Kotlin test library.
    testImplementation(kotlin("test"))

    // Use the Kotlin JUnit integration.
    testImplementation(kotlin("test-junit"))

    implementation(platform("org.http4k:http4k-bom:4.9.0.2"))
    implementation("org.http4k:http4k-core")
    implementation("org.http4k:http4k-server-netty")
    implementation("org.http4k:http4k-client-apache")

    implementation("com.google.code.gson:gson:2.8.7")
}

group = "com.github.elect86"
version = "0.1.8"

gradlePlugin {
    // Define the plugin
    plugins.create("magik") {
        id = "elect86.magik"
        displayName = "Maven repository on Github in Kotlin"
        description = "publish directly on your github repository acting as a maven repository"
        implementationClass = "magik.MagikPlugin"
    }
}

pluginBundle {
    website = "https://github.com/elect86/magik"
    vcsUrl = "https://github.com/elect86/magik"
    tags = listOf("github", "repository", "maven", "kotlin", "publish", "publishing")
}

// Add a source set for the functional test suite
val functionalTestSourceSet = sourceSets.create("functionalTest") {}

gradlePlugin.testSourceSets(functionalTestSourceSet)
configurations["functionalTestImplementation"].extendsFrom(configurations["testImplementation"])

// Add a task to run the functional tests
val functionalTest by tasks.registering(Test::class) {
    testClassesDirs = functionalTestSourceSet.output.classesDirs
    classpath = functionalTestSourceSet.runtimeClasspath
}

tasks.check {
    // Run the functional tests as part of `check`
    dependsOn(functionalTest)
}

tasks.compileJava {
    sourceCompatibility = "1.8"
    targetCompatibility = "1.8"
}