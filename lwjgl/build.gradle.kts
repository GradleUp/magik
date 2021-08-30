
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
    implementation(platform("org.jetbrains.kotlin:kotlin-bom"))

    // Use the Kotlin JDK 8 standard library.
    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8")

    // Use the Kotlin test library.
    testImplementation("org.jetbrains.kotlin:kotlin-test")

    // Use the Kotlin JUnit integration.
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit")
}

group = "com.github.elect86"
version = "0.0.1"

gradlePlugin {
    // Define the plugin
    plugins.create("lwjgl") {
        id = "elect86.lwjgl"
        displayName = "Lwjgl Gradle util"
        description = "Easier Lwjgl dependency management"
        implementationClass = "main.LwjglPlugin"
    }
}

pluginBundle {
    website = "https://github.com/elect86/magik"
    vcsUrl = "https://github.com/elect86/magik"
    tags = listOf("lwjgl", "gradle", "dependency", "easy", "management")
}

// Add a source set for the functional test suite
//val functionalTestSourceSet = sourceSets.create("functionalTest") {}
//
//gradlePlugin.testSourceSets(functionalTestSourceSet)
//configurations["functionalTestImplementation"].extendsFrom(configurations["testImplementation"])
//
//// Add a task to run the functional tests
//val functionalTest by tasks.registering(Test::class) {
//    testClassesDirs = functionalTestSourceSet.output.classesDirs
//    classpath = functionalTestSourceSet.runtimeClasspath
//}
//
//tasks.check {
//    // Run the functional tests as part of `check`
//    dependsOn(functionalTest)
//}
//
//tasks.compileJava {
//
//}