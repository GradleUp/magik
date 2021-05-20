# magik


Turn your personal Github repository into your personal maven repository.

Usually it was already possible doing something like that, but this implicitely means you must have the repo cloned locally and manually committing and pushing. Now these limitations are gone, allowing for faster development cycles.

What this plugin does is the following:
- publishing to `$buildDir/repo`
- creating a `tmp` branch
- uploading file by file
- creating a PR
- merging the same PR back to master by squashing all the commits into a single one
- deleting the `tmp` branch


### How to use

##### Authentication

You should first be sure to be able to connect to [GitHub using ssh](https://docs.github.com/en/github/authenticating-to-github/connecting-to-github-with-ssh).

Then create a [personal access token](https://docs.github.com/en/github/authenticating-to-github/keeping-your-account-and-data-secure/creating-a-personal-access-token), you should be sure to check `repo`.
Then copy the token and paste it in `gradle.property` in your home (`~/.gradle`) as

`githubToken=ghp_...`

Take in account `github` is convention, you can change it with whatever you want (read the next section). Magik will look for your token in `${githubRepositoryName}Token`

##### Publishing

```
publishing {
    publications {
        create<MavenPublication>("maven") {
            groupId = "org.gradle.sample"
            artifactId = "prova"
            version = "0.1"

            from(components["java"])
        }.alsoSnapshot() // this clone the publication for snapshots 
        // eg: publishMavenPublicationToGithubRepository -> publishMavenSnapshotPublicationToGithubRepository
    }
    repositories {
        github {
            // this is superfluous since `github` is the default value
            // but this determines the token name to fetch and the publishing task name (eg: publishMavenPublicationToGithubRepository)
            name = "github" 
            domain = "kotlin-graphics/mary"
        }
    }
}
```

##### Fetching

```
repositories {
    github("kotlin-graphics/mary")
}
```

or

```
repositories {
    github("kotlin-graphics", "mary")
}
```

Known limitations: max 100GB total repo size, max 100MB size per file.

