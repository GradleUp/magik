# [magik](https://plugins.gradle.org/plugin/elect86.magik)


Turn your personal Github repository into your personal maven repository.

I was tired of Github Packages requiring authentication even for public access, Sonatype and Maven Central being a pain to setup, the first supporting only one snapshot at time (and you need to deal with cache) and Nexus in general being incredibly slow in comparison, therefore I decided to go on my own and write this

Usually it was already possible doing something like that, but this implicitely meant you had to have the repo cloned locally and manually committ and push. Now these limitations are gone, allowing for even faster development cycles.

What this plugin does is the following, for each project/module:
- publishes locally to `$buildDir/repo`, but before doing this, it will download in advance `metadata-maven.xml` in order to avoid resetting/overwrite with a single entry every time and having gradle properly modify it instead
- creates a `tmp` branch on the github repo
- uploads file by file
- creates a PR
- merges the same PR back to master by squashing all the commits into a single one
- deletes the `tmp` branch


Welcome [Magik](https://plugins.gradle.org/plugin/elect86.magik) (MAven repository on Github written In Kotlin)

```
plugins {
  id("elect86.magik") version "0.3.3"
}
```


![image](https://img.devrant.com/devrant/rant/r_2516404_bkZxN.jpg)

### How to use

You have two options (!). Too many, I know, sorry. You can use:
1) a Github repository acting as a pure Maven repository
2) Github Packages without the authentication hassle, even for just consuming dependencies

### Method 1, Github repository as Maven repository

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
         // you can pass a name to overwrite the default "maven"
         // createGithubPublication("my-name") 
        createGithubPublication { this: MavenPublication
            // if your project already defines `groupId`, `artifactId` and `version`, then you can skip these here
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

##### Publishing

Run `publish(Maven)PublicationTo(Github)Repository` or `publishAllPublicationsTo(Github)Repository`

where `Maven` is the publication name (capitalized) and `Github` the repository name (capitalized)

> &gt; Task :publishMavenPublicationToGithubRepository
>
> kotlin.graphics:gli:0.8.3.0-18 published on kotlin-graphics/mary!

The printed GAV coordinates can then be easily copied and pasted where needed :)

### Settings

Sometimes it happens you forget to commit before publishing. In order to avoid these situations, 
the default setting `commitWithChanges` will warn you whenever you are committing while there are changes to be committed or 
not staged for commit.

This requires `git` being available on path though, which is automatically set at begin in `gitOnPath`.

`defaultCommitWithChanges` will instead automatically highlight the given answer when asking if you
want to commit anyway with changes.

> [magik] Do you want to continue publishing anyway? Y/[N]:

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

#### Setting the repository in `settings.gradle.kts` for all the modules in a multi-module project

Since this is a `Project` plugin (which applies to `build.gradle.kts`), there is no support for `settings.gradle.kts`.
So, in case you prefer to apply the repositories once in the settings, just fallback to:

`"https://raw.githubusercontent.com/$organization/$repo/$branch"`

For example, `settings.gradle.kts`:
```
dependencyResolutionManagement {
    repositories {
        mavenCentral()
        maven("https://raw.githubusercontent.com/kotlin-graphics/mary/master")
    }
}
```

### Method 2, clients consuming Github Packages without having to set up authentication

Create a token with the `read:packages` scope and add a `credentials` file in the root of your repository (branch `master`/`main`) hosting 
the packages with your nickname on the first line, and the token you just created on the second line (without the 
prefix `ghp_`, otherwise Github will detect the token code in the next commit and delete the corresponding token)

Example, mary's [`credentials`](https://github.com/kotlin-graphics/mary/blob/master/credentials)

```
elect86
7V1YljEcKShzwPzPJuAPP0X55urEhF0RBWG2
```

##### Fetching

```kotlin
repositories {
    githubPackages("kotlin-graphics/mary")
}
```

### Advantages and limitations

Big advantage for both: they rely on the Github infrastructure (aka bandwidth and reliability)

##### Method 1, advantages:
- pure Maven repository
- works with every client, Maven or Gradle or whatever
- no need to expose a (read) token on the internet
- no need to have a `credentials` file in your root repository

##### Disadvantages:
- git is suboptimal for storing (large) binaries, it's fine for regular ones, but fat jars may represent a problem in the long run
- every repository has a hard limit of 100 GB for its total size
- every file has a hard limit of 100 MB for its size

##### Method 2, advantages:
- is optimal for every kind of binaries, also fat jars

##### [Disadvantages](https://medium.com/swlh/devops-with-github-part-1-github-packages-with-gradle-c4253cdf7ca6):
- Snapshots do not work
  - Even though the docs state that snapshots are supported, we couldn’t get them to work. The first few builds work just fine; however, old snapshots are not being removed. Instead, new artifacts are constantly being added to that same release, which eventually stops working. From this point on, you will always fetch old snapshots.
- Multiple artifacts per release do not work
  - As soon as you try to upload JavaDoc and source artifacts in addition to the library itself, the repository enters an unstable state. Even loading existing artifacts or publishing new library versions will fail with a 400 error. In our case, only deleting the artifact or repository could fix this. 
- Public versions cannot be deleted
  - While it’s possible to delete artifact versions in private repositories, GitHub doesn’t allow you to do this in public repositories. Therefore, you should carefully consider what to publish and what to keep private ;)
- The token needs the correct scopes
  - The personal access token used to access the artifacts must have precisely the scopes listed above for everything to work. If any of these scopes is missing, you will receive error messages that often do not indicate the lack of permission!
- Dynamic versions will prevent publishing
  - If you are using Spring Boot, and have dynamic versions enabled, the publishing will fail because the versions are not locked. While this is not directly related to GitHub packages, it is still good to be aware of. To fix this, you need to adjust the build.gradle file, so that there is a version mapping in the publishing block:
