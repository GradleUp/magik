package magik

import dev.forkhandles.result4k.map
import dev.forkhandles.result4k.valueOrNull
import http4k.github.GitHubRepo
import http4k.github.MergeMethod
import org.gradle.api.Action
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.dsl.RepositoryHandler
import org.gradle.api.artifacts.repositories.MavenArtifactRepository
import org.gradle.api.provider.Property
import org.gradle.api.publish.PublicationContainer
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.configurationcache.extensions.capitalized
import org.gradle.kotlin.dsl.create
import org.gradle.kotlin.dsl.getByName
import org.gradle.kotlin.dsl.maven
import org.gradle.kotlin.dsl.register
import java.io.*
import java.net.URI
import java.net.URL

abstract class MagikExtension {
    abstract val commitWithChanges: Property<Boolean>
    abstract val defaultCommitWithChanges: Property<Boolean>
    abstract val gitOnPath: Property<Boolean>
    abstract val dryRun: Property<Boolean>
    abstract val verbose: Property<Boolean>
    abstract val defaultSnapshotNamePostfix: Property<String>
    abstract val defaultSnapshotVersionPostfix: Property<(gitDistance: Int) -> String>
}

// reference in order to loop and detect automatically the publishing task to append logic to
val githubs = ArrayList<GithubArtifactRepository>()

// i-th project reference in order to automatically set as default, a `repo` directory in the build one
lateinit var configuringProject: Project


class MagikPlugin : Plugin<Project> {

    override fun apply(project: Project) {
        //        project.tasks.register("greeting") {
        //            doLast {
        //                println("Hello from plugin 'terraform.kt.greeting'")
        //            }
        //        }
        println("apply(project: $project)")

        //        project.pluginManager.apply("maven-publish")

        configuringProject = project

        // Add the 'greeting' extension object
        val setting = project.extensions.create<MagikExtension>("magik").apply {
            commitWithChanges.convention(false)
            defaultCommitWithChanges.convention(false)
            gitOnPath.convention(configuringProject.exec {
                commandLine("git", "--version")
                standardOutput = ByteArrayOutputStream() // disable output with a dummy instance
            }.exitValue == 0)
            dryRun.convention(false)
            verbose.convention(false)
            defaultSnapshotNamePostfix.convention("Snapshot")
            defaultSnapshotVersionPostfix.convention { "+$it" }
        }

        project.tasks.configureEach {

            val gh = githubs.find { it.project == project } ?: return@configureEach
            //            val repo = GitHubRepo(gh.domain)
            //            if (setting.verbose.get()) println(gh.project)
            val ghName = gh.name.capitalized()
            val postFix = "PublicationTo${ghName}Repository"

            val found = name.startsWith("publish") && name.endsWith(postFix)

            if (!found) return@configureEach

            fun verbose(text: Any) {
                if (setting.verbose.get()) println(text)
            }

            verbose("$this, $name .. appending")

            val ext = project.extensions.getByName<PublishingExtension>("publishing")
            val ignoreCase = true
            val repo = ext.repositories.first { it.name.equals(gh.name, ignoreCase) } as MavenArtifactRepository
            val publ = ext.publications.first {
                it.name.equals(name.substringAfter("publish").substringBefore(postFix), ignoreCase)
            } as MavenPublication

            // delete first any previously local publication
            verbose("deleting first any previous local publication at ${repo.url}")
            File(repo.url).deleteRecursively()

            var proceed = true
            doFirst {
                // check against git uncommitted changes
                if (!setting.gitOnPath.get() || setting.commitWithChanges.get())
                    return@doFirst
                val status = try {
                    project.exec("git status")
                } catch (e: Exception) {
                    ""
                }
                if ("Changes to be committed" in status || "Changes not staged for commit" in status) {
                    println(status)
                    tailrec fun proceed(): Boolean {
                        val options = if (setting.defaultCommitWithChanges.get()) "[Y]/N" else "Y/[N]"
                        println("\n[magik] Do you want to continue publishing anyway? $options:")
                        val reader = BufferedReader(InputStreamReader(System.`in`))
                        return when (reader.read().toChar()) {
                            'Y', 'y' -> true
                            'N', 'n' -> false
                            '\n' -> setting.defaultCommitWithChanges.get()
                            else -> proceed()
                        }
                    }
                    proceed = proceed()
                    if (proceed) println("..continuing the publication with uncommited local changes..")
                    else println("aborting, please commit or revert your local changes before proceeding publishing")
                }
                if (proceed) {
                    // download maven-metadata.xml, if exists, to avoid overwrites the remote one and keep track of previous releases/snapshots
                    // ga = org/gradle/sample/prova
                    val pathGA = publ.groupId.replace('.', '/') + '/' + publ.artifactId
                    // file does exist and it's not the first publication ever
                    gh.getContent("$pathGA/maven-metadata.xml").map { meta ->
                        File(repo.url).resolve(pathGA).run {
                            mkdirs()
                            resolve("maven-metadata.xml")
                        }.apply {
                            createNewFile()
                            writeText(meta.rawContent)
                            verbose("meta.rawContent ${meta.rawContent}")
                        }
                    }
                }
            }

            doLast {
                if (!proceed)
                    return@doLast
                //                                        println(project.displayName)

                val magikBranch = "magik"

                if (!setting.dryRun.get()) {
                    // save master branch head reference
                    val sha = gh.gitRef.valueOrNull()!!.`object`.sha
                    verbose("master head reference sha: $sha")

                    // create magik branch via a reference
                    gh.createRef(magikBranch, sha)
                }

                // create/update every file on magik branch
                val dir = File(repo.url)
                verbose("dir: $dir")
                dir.walk().forEach { file ->
                    if (file.isFile) {
                        verbose(file)
                        if (!setting.dryRun.get()) {
                            // file = /home/elect/IdeaProjects/single/build/repo/org/gradle/sample/prova/0.1/prova-0.1.pom.md5
                            // path = org/gradle/sample/prova/0.1/prova-0.1.pom.md5
                            val path = file.toRelativeString(dir).replace('\\', '/')

                            val sha = gh.getContent(path, magikBranch).valueOrNull()?.sha
                            gh.uploadContent(path, message = path, content = file, branch = magikBranch, sha = sha)
                        }
                    }
                }

                if (setting.dryRun.get())
                    return@doLast

                val gav = publ.run { "$groupId:$artifactId:$version" }

                // create the PR
                val pr = gh.createPR(title = gav, head = magikBranch, base = "master").valueOrNull()!!

                // the current head on `magik` branch
                val magikSha = gh.getCommit(magikBranch).valueOrNull()!!.sha

                // we have now everything to merge the PR
                println(gh.mergePullRequest(pr.number, commitTitle = gav, sha = magikSha, mergeMethod = MergeMethod.squash))

                // delete the magik branch
                gh.deleteBranch(magikBranch)

                println("$gav published on ${gh.domain}!")
            }
        }
    }
}

fun Project.exec(cmd: String): String =
    ByteArrayOutputStream().also { exec { commandLine(cmd.split(' ')); standardOutput = it; } }.toString().trim()

/** root repositories scope */
fun RepositoryHandler.github(domain: String) = maven("https://raw.githubusercontent.com/$domain/master")

/** root repositories scope */
fun RepositoryHandler.github(owner: String, repo: String) =
    maven("https://raw.githubusercontent.com/$owner/$repo/master")

/** publishing/repositories scope */
fun RepositoryHandler.github(action: Action<GithubArtifactRepositoryBuilder>) {
    val builder = GithubArtifactRepositoryBuilder().also(action::execute)
    val gh = GithubArtifactRepository(configuringProject, builder.name, builder.domain)
    githubs += gh
    maven {
        name = gh.name
        url = configuringProject.run { uri(layout.buildDirectory.dir("repo")) }
    }
}

fun RepositoryHandler.githubPackages(owner: String, repo: String) =
    githubPackages("https://raw.githubusercontent.com/$owner/$repo/master")

fun RepositoryHandler.githubPackages(domain: String) {
    maven {
        // The url of the repository that contains the published artifacts
        url = URI("https://maven.pkg.github.com/$domain")
        credentials {
            fun file(branch: String = "master") = "https://raw.githubusercontent.com/$domain/$branch/credentials"
            val (name, pwd) = try {
                URL(file()).readText()
            } catch (ex: FileNotFoundException) {
                URL(file("main")).readText()
            }.lines()
            username = name
            password = "ghp_$pwd"
        }
    }
}

class GithubArtifactRepositoryBuilder {
    var name = "github"
    lateinit var domain: String
}

class GithubArtifactRepository(val project: Project,
                               val name: String,
                               domain: String,
                               branch: String = "master") : GitHubRepo(domain, branch) {
    internal val repo: String by lazy { domain.substringAfter('/') }
}

val gitDescribe: String
    get() = configuringProject.exec("git describe --tags")

val gitDistance: Int
    get() = try {
        gitDescribe.substringBeforeLast("-g").substringAfterLast('-').toInt()
    } catch (ex: Exception) {
        -1
    }

fun PublicationContainer.createGithubPublication(name: String = "maven",
                                                 action: Action<MavenPublication>) {
    currentSnapshot = null
    action.execute(register<MavenPublication>(name).get())
    currentSnapshot?.let {
        register<MavenPublication>(it.name).get().version = it.version
        currentSnapshot = null
    }
}

class GithubSnapshotPublication(var name: String, var version: String)

var currentSnapshot: GithubSnapshotPublication? = null

fun MavenPublication.addSnapshotPublication(block: GithubSnapshotPublication.() -> Unit = {}) {
    if (currentSnapshot != null)
    // we don't want to recursively create another snapshot when already creating a snapshot
        return
    val setting = configuringProject.extensions.getByName<MagikExtension>("magik")
    val name = "$name${setting.defaultSnapshotNamePostfix.get()}"
    val version = "$version+${setting.defaultSnapshotVersionPostfix.get()(gitDistance)}"
    currentSnapshot = GithubSnapshotPublication(name, version).apply(block)
}


