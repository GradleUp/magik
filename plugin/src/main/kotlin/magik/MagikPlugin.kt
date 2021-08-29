package magik

import org.gradle.api.Action
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.dsl.RepositoryHandler
import org.gradle.api.artifacts.repositories.ArtifactRepository
import org.gradle.api.artifacts.repositories.MavenArtifactRepository
import org.gradle.api.artifacts.repositories.RepositoryContentDescriptor
import org.gradle.api.provider.Property
import org.gradle.api.publish.PublicationContainer
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.tasks.Input
import org.gradle.kotlin.dsl.create
import org.gradle.kotlin.dsl.findByType
import org.gradle.kotlin.dsl.getByName
import org.gradle.kotlin.dsl.maven
import org.http4k.client.JavaHttpClient
import org.http4k.core.*
import org.http4k.core.Method.*
import java.io.BufferedReader
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStreamReader
import java.util.*


abstract class MagikExtension {

    @get:Input
    abstract val commitWithChanges: Property<Boolean>

    @get:Input
    abstract val defaultCommitWithChanges: Property<Boolean>

    @get:Input
    abstract val gitOnPath: Property<Boolean>

    @get:Input
    abstract val dryRun: Property<Boolean>

    @get:Input
    abstract val verbose: Property<Boolean>

    @get:Input
    abstract val defaultSnapshotNamePostfix: Property<String>

    @get:Input
    abstract val defaultSnapshotVersionPostfix: Property<(gitDistance: Int) -> String>

    init {
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
}

//abstract class GithubContainer : BuildService<GithubContainer.Params>, AutoCloseable {
//
//    internal interface Params : BuildServiceParameters {
//        val repositories: Property<ArrayList<GithubArtifactRepository>>
//    }
//
//    init {
//        parameters.repositories.set(ObjectFactory.listProperty())
//    }
//}

//lateinit var githubContainer: Provider<GithubContainer>

// reference in order to loop and detect automatically the publishing task to append logic to
val githubs = ArrayList<GithubArtifactRepository>()

// i-th project reference in order to automatically set as default, a `repo` directory in the build one
lateinit var configuringProject: Project

/**
 * A simple 'hello world' plugin.
 */
class MagikPlugin : Plugin<Project> {

    override fun apply(project: Project) {
        //        println("apply($project)")
        // Register the service
        //        githubContainer = project.gradle.sharedServices.registerIfAbsent("githubContainer", GithubContainer::class.java) {}

        configuringProject = project

        // Add the 'greeting' extension object
        val setting = project.extensions.create<MagikExtension>("magik")

        // Register a task
        project.tasks.register("greeting") {
            doLast {
                println("Hello from plugin 'magik.greeting'")
            }
        }

        project.tasks.configureEach {

            val gh = githubs.find { it.project == project } ?: return@configureEach
            //            if (setting.verbose.get()) println(gh.project)
            val ghName = gh.name.capitalize()
            val postFix = "PublicationTo${ghName}Repository"

            val found = name.startsWith("publish") && name.endsWith(postFix)

            if (!found)
                return@configureEach

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
            File(repo.url).deleteRecursively()
            verbose("delete first any previously local publication at ${repo.url}")

            operator fun Method.invoke(relativeUri: String, debugRequest: Boolean = false,
                                       debugResponse: Boolean = false, is404fine: Boolean = false,
                                       block: (Request.() -> Request)? = null): Response {
                var request = Request(this, "https://api.github.com/repos/${gh.domain}/$relativeUri")
                    .header("Accept", "application/vnd.github.v3+json")
                    .header("Authorization", "token ${project.property("${gh.name}Token")!!}")
                if (debugRequest)
                    println(request)
                if (block != null)
                    request = request.block()
                return JavaHttpClient()(request).apply {
                    close()
                    if (debugResponse)
                        println(this)
                    if (!status.successful)
                        if (status != Status.NOT_FOUND || !is404fine)
                            error("$status\n$request\n(${request.toCurl()}\n$this")
                }
            }

            var proceed = true
            doFirst {
                // check against git uncommitted changes
                if (!setting.gitOnPath.get() || setting.commitWithChanges.get())
                    return@doFirst
                val status = project.exec("git status")
                val changesToBeCommitted = "Changes to be committed"
                val changesNotStagedForCommit = "Changes not staged for commit"
                if (changesToBeCommitted in status || changesNotStagedForCommit in status) {
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
                    println(when {
                                proceed -> "..continuing the publication with uncommited local changes.."
                                else -> "aborting, please commit or revert your local changes before proceeding publishing"
                            })
                }
                if (proceed) {
                    // download maven-metadata.xml to avoid overwrites and keep track of previous releases/snapshots
                    val gas = publ.groupId.split('.') + publ.artifactId
                    val ga = gas.joinToString(File.separator)
                    val metadata = File(repo.url).resolve(ga).run {
                        mkdirs()
                        resolve("maven-metadata.xml")
                    }.apply { createNewFile() }
                    val request = Request(GET, "https://raw.githubusercontent.com/${gh.domain}/master/$ga/maven-metadata.xml")
                        .header("Accept", "application/vnd.github.v3+json")
                        .header("Authorization", "token ${project.property("${gh.name}Token")!!}")
                    metadata.writeText(JavaHttpClient()(request).bodyString())
                }
            }

            doLast {
                if (!proceed)
                    return@doLast
                //                                        println(project.displayName)

                if (!setting.dryRun.get()) {
                    // save commit revision
                    val rev = GET("git/refs/heads").bodyString().sha
                    verbose("rev: $rev")

                    // create tmp branch via a reference
                    POST("git/refs") {
                        body("""{"ref": "refs/heads/tmp", "sha": "$rev"}""")
                    }
                }

                // create/update every file on tmp
                val dir = File(repo.url)
                verbose("dir: $dir")
                dir.walk().forEach { file ->
                    if (file.isFile) {
                        verbose(file)
                        if (!setting.dryRun.get()) {
                            val path = file.toRelativeString(dir).replace('\\', '/')
                            val response = GET("contents/$path", is404fine = true)
                            val maybeSha = when (response.status) {
                                Status.NOT_FOUND -> ""
                                else -> """, "sha": "${response.bodyString().sha}""""
                            }
                            val content = Base64.getEncoder().encodeToString(file.readBytes())
                            PUT("contents/$path") {
                                body("""{"path": "$path", "message": "$path", "content": "$content", "branch": "tmp"$maybeSha}""")
                            }
                        }
                    }
                }

                if (setting.dryRun.get())
                    return@doLast

                val gav = "${publ.groupId}:${publ.artifactId}:${publ.version}"

                // create the PR
                POST("pulls") {
                    body("""{"repo":"${gh.repo}","title":"$gav","head":"tmp","base":"master","body":"$gav"}""")
                }

                // retrieve the PR number
                val pr = run {
                    // retrieve all the PRs (it should be just one) and read its number
                    val body = GET("pulls").bodyString()
                    val ofs = body.indexOf(""","number":""") + 10
                    // let's give it a couple of digits, before parsing
                    val number = body.substring(ofs, ofs + 5)
                    number.takeWhile { it.isDigit() }.toInt()
                }

                // the current head on `tmp` branch
                val lastCommit = run {
                    val body = GET("commits/tmp").bodyString()
                    val ofs = body.indexOf("\"sha\":\"") + 7
                    body.substring(ofs, ofs + 40)
                }

                // we have now everything to merge the PR
                PUT("pulls/$pr/merge") {
                    body("""{"repo":"${gh.repo}","pull_number":"$pr","commit_title":"$gav","sha":"$lastCommit","merge_method":"squash"}""")
                }

                // delete the tmp branch
                DELETE("git/refs/heads/tmp")

                println("$gav published on ${gh.domain}!")
            }
        }
    }
}

fun Project.exec(cmd: String): String = ByteArrayOutputStream().also { exec { commandLine(cmd.split(' ')); standardOutput = it; } }.toString().trim()

val String.sha: String
    get() {
        val ofs = indexOf("\"sha\":\"") + 7
        return substring(ofs, ofs + 40)
    }

/** root repositories scope */
fun RepositoryHandler.github(domain: String) = maven("https://raw.githubusercontent.com/$domain/master")

/** root repositories scope */
fun RepositoryHandler.github(owner: String, repo: String) = maven("https://raw.githubusercontent.com/$owner/$repo/master")

/** publishing/repositories scope */
fun RepositoryHandler.github(block: GithubArtifactRepository.() -> Unit) {
    val gh = GithubArtifactRepository(configuringProject)
    gh.block()
    githubs += gh
    maven {
        name = gh.name
        url = configuringProject.run { uri(layout.buildDirectory.dir("repo")) }
    }
}

class GithubArtifactRepository(val project: Project) : ArtifactRepository {

    private var n = "github"

    lateinit var domain: String

    internal val repo: String
        get() = domain.substringAfter('/')

    override fun getName(): String = n

    override fun setName(name: String) {
        n = name
    }

    override fun content(configureAction: Action<in RepositoryContentDescriptor>) = TODO("Not yet implemented")
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
                                                 block: MavenPublication.() -> Unit) {
    currentSnapshot = null
    create(name, block)
    currentSnapshot?.let {
        create(it.name, block).version = it.version
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