plugins {
    // Apply the Java Gradle plugin development plugin to add support for developing Gradle plugins
    `java-gradle-plugin`
    // Apply the Kotlin JVM plugin to add support for Kotlin.
    embeddedKotlin("jvm")
    `kotlin-dsl`
    id("com.gradle.plugin-publish") version "1.2.1"
    `maven-publish`
}

repositories {
    mavenCentral()
    gradlePluginPortal()
}

dependencies {
    // Align versions of all Kotlin components
    implementation(platform(kotlin("bom", version = embeddedKotlinVersion)))

    // Use the Kotlin JDK 8 standard library.
    implementation(kotlin("stdlib"))

    // Use the Kotlin test library.
    testImplementation(kotlin("test"))

    // Use the Kotlin JUnit integration.
    testImplementation(kotlin("test-junit"))

    implementation(platform("org.http4k:http4k-bom:5.14.0.0"))
    implementation("org.http4k:http4k-core")
    implementation("org.http4k:http4k-server-netty")
    implementation("org.http4k:http4k-client-apache")
    implementation("org.http4k:http4k-connect")
    implementation("org.http4k:http4k-connect-github")
    implementation("org.http4k:http4k-format-jackson")

    implementation("com.google.code.gson:gson:2.10.1")
}

group = "com.github.elect86"
version = "0.3.4"

publishing {
    publications.create<MavenPublication>("maven") {
        artifactId = rootProject.name
    }
}

gradlePlugin {
    website = "https://github.com/elect86/magik"
    vcsUrl = website
    // Define the plugin
    plugins.create("magik") {
        id = "elect86.magik"
        implementationClass = "magik.MagikPlugin"
        displayName = "easier developers' life publishing on Github"
        description = "publish directly on your Github repository acting as a Maven repository or use Github Packages without hassle"
        tags = listOf("github", "repository", "maven", "kotlin", "publish", "publishing")
    }
}

kotlin.jvmToolchain {
    languageVersion.set(JavaLanguageVersion.of(8))
}

tasks.withType<JavaCompile>().configureEach {
    options.release = 8
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


tasks {
//    register("copyRuntimeLibs", Copy::class) {
//        into("lib")
//        from(configurations.runtimeClasspath)
//    }
}