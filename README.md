# magik


Turn your personal Github repository into your personal maven repository.

Usually it was already possible doing something like that, but this implicitely meant to have the repo cloned locally and manually committing and pushing. Now this limitations are gone.

What this plugin does is the following:
- publishing to `$buildDir/repo`
- creating a `tmp` branch
- uploading file by file
- creating a PR
- merging the same PR back to master by squashing all the commits into a single one
- deleting the `tmp` branch


### How to use

##### Authentication

You should first be sure to be able to connect to [GitHub using ssh](https://docs.github.com/en/github/authenticating-to-github/connecting-to-github-with-ssh)

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
    }
    repositories {
        github {
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

