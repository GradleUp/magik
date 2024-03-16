import magik.registerGithubPublication
import magik.github

plugins {
    embeddedKotlin("jvm")
    id("elect86.magik")
}

group = "org.example"
version = "1.0-SNAPSHOT"

repositories { mavenCentral() }

dependencies {
    testImplementation(kotlin("test"))
}

tasks.test { useJUnitPlatform() }

kotlin.jvmToolchain(17)

//registerGithubPublication {
//
//}

publishing {
    publications {
        // you can pass a name to overwrite the default "maven"
        // createGithubPublication("my-name")
        registerGithubPublication {
            // if your project already defines `groupId`, `artifactId` and `version`, then you can skip these here
            groupId = "org.gradle.sample"
            artifactId = "prova"
            version = "0.1"

            from(components["java"])
        }
    }
    // don't use `repositories.github(..)`, it won't work
    // the dsl construct is necessary to distinguish it from a consume-only repo
    repositories {
        // don't use github(domain: String), that's for fetching, it won't work for publishing
        github {
            // this is optional since `github` is the default value, but it determines
            // the token name to fetch and the consequent publishing task name
            // eg: publishMavenPublicationToGithubRepository
            name = "github"

            // this is mandatory instead: $owner/$repo on github domain
            domain = "kotlin-graphics/mary" // aka https://github.com/kotlin-graphics/mary
        }
    }
}