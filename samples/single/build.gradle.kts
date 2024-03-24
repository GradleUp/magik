import magik.createGithubPublication
import magik.github

plugins {
    embeddedKotlin("jvm")
    id("elect86.magik")
    `maven-publish`
}

group = "magik"
version = "1.0"

repositories { mavenCentral() }

dependencies {
    testImplementation(kotlin("test"))
}

tasks.test { useJUnitPlatform() }

kotlin.jvmToolchain(17)

magik {
    verbose = true
}

publishing {
    publications {
        // you can pass a name to overwrite the default "maven"
        // createGithubPublication("my-name")
        createGithubPublication {
            // if your project already defines `groupId`, `artifactId` and `version`, then you can skip these here
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
//            name = "github"

            // this is mandatory instead: $owner/$repo on github domain
            domain = "elect86/fuzzy-octo-disco" // aka https://github.com/elect86/fuzzy-octo-disco
        }
    }
}