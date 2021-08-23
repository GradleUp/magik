# [magik](https://plugins.gradle.org/plugin/elect86.magik)


Turn your personal Github repository into your personal maven repository.

I was tired of Github Packages requiring authentication even for public access, Sonatype and Maven Central being a pain to setup, the first supporting only one snapshot at time (and you need to deal with cache) and Nexus in general being incredibly slow in comparison, therefore I decided to go on my own and write this

Usually it was already possible doing something like that, but this implicitely meant you had to have the repo cloned locally and manually committ and push. Now these limitations are gone, allowing for even faster development cycles.

What this plugin does is the following, for each project/module:
- publishes locally to `$buildDir/repo`
- creates a `tmp` branch on the github repo
- uploads file by file
- creates a PR
- merges the same PR back to master by squashing all the commits into a single one
- deletes the `tmp` branch


Welcome [Magik](https://plugins.gradle.org/plugin/elect86.magik) (MAven repository on Github written In Kotlin)

```
plugins {
  id("elect86.magik") version "0.0.9"
}
```


![image](https://img.devrant.com/devrant/rant/r_2516404_bkZxN.jpg)

### How to use

##### Authentication

You should first be sure to be able to connect to [GitHub using ssh](https://docs.github.com/en/github/authenticating-to-github/connecting-to-github-with-ssh).

Then create a [personal access token](https://docs.github.com/en/github/authenticating-to-github/keeping-your-account-and-data-secure/creating-a-personal-access-token), be sure to check `repo`.
Then copy the token and paste it in `gradle.property` in your home (`~/.gradle`) as

`githubToken=ghp_...`

Take in account `github` is convention, you can change it with whatever you want (read the next section). Magik will look for your token in a variable names as `${githubRepositoryName}Token`

##### Publishing

```kotlin
publishing {
    publications {
        create<MavenPublication>("maven") {
            groupId = "org.gradle.sample"
            artifactId = "prova"
            version = "0.1"

            from(components["java"])
        }.github {
            // this adds another (snapshot) publication, copying from the previous one: 
            // - gav coordinates 
            // - component type (java, javaPlatform or war)
            // - name, by default appended with the `Snapshot` postfix, 
            // eg: publishMavenPublicationToGithubRepository ->
            // ->  publishMavenSnapshotPublicationToGithubRepository
            addSnapshotPublication()
        }
    }
    // don't use `repositories.github(..)`, it won't work
    // the dsl construct is necessary to distinguish it from a consume-only repo
    repositories {
        github {
            // this is superfluous since `github` is the default value, but it determines 
            // the token name to fetch and the consequent publishing task name 
            // eg: publishMavenPublicationToGithubRepository
            name = "github" 
            
            // this is mandatory instead: $owner/$repo on github domain
            domain = "kotlin-graphics/mary" // aka https://github.com/kotlin-graphics/mary
        }
    }
}
```

##### Fetching

```kotlin
repositories {
    github("kotlin-graphics/mary")
}
```

or

```kotlin
repositories {
    github("kotlin-graphics", "mary")
}
```

### Settings

Sometimes it happens you forget to commit before publishing. In order to avoid these situations, 
the default setting `commitWithChanges` will warn you whenever you are committing while there are changes to be committed or 
not staged for commit.

This requires `git` being available on path though, which is automatically set at begin with `gitOnPath`.

`defaultCommitWithChanges` will instead automatically highlight the given answer when asking if you
want to commit anyway with changes.

`dryRun` for running without uploading anything.

`verbose` is self explanatory.

```kotlin
magik {
    commitWithChanges.convention(false)
    defaultCommitWithChanges.convention(false)
    gitOnPath.convention(configuringProject.exec {
        commandLine("git", "--version")
        standardOutput = ByteArrayOutputStream() // disable output with a dummy instance
    }.exitValue == 0)
    dryRun.convention(false)
    verbose.convention(false)
}
```


### Known limitations for Github repositories

max 100GB total repo size, max 100MB size per file

