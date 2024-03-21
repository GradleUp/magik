package magik

import dev.forkhandles.result4k.*
import org.http4k.base64Decoded
import org.http4k.base64Encode
import org.http4k.client.JavaHttpClient
import org.http4k.connect.RemoteFailure
import org.http4k.connect.github.GitHubToken
import org.http4k.connect.github.api.GitHub
import org.http4k.connect.github.api.GitHubAction
import org.http4k.core.*
import org.http4k.format.Moshi.auto
import java.io.File
import org.http4k.core.Body
import org.http4k.core.Method
import org.http4k.core.Request
import org.http4k.core.Uri
import org.http4k.core.with
import org.http4k.format.Jackson.json
import org.http4k.format.Jackson.asJsonObject

private val gitHub = GitHub.Http2()

open class GitHubRepo(val domain: String, var branch: String = "master") {
    //    infix fun getFile(path: String) = GitHub(GetFile())


    // https://docs.github.com/en/rest/repos/contents?apiVersion=2022-11-28#get-repository-content

    fun getContent(path: String, branch: String = this@GitHubRepo.branch) = gitHub(GetContentTree(path, branch))

    inner class GetContentTree(path: String, branch: String) : GitHubAction<ContentTree> {
        val responseBody = Body.auto<ContentTree>().toLens()
        val uri = Uri.of("$api/$domain/contents/$path?ref=$branch")

        override fun toRequest() = Request(Method.GET, uri)

        override fun toResult(response: Response) = when {
            response.status.successful -> Success(responseBody(response))
            else -> Failure(RemoteFailure(Method.GET, uri, response.status, response.bodyString()))
        }
    }

    data class ContentTree(val type: String,
                           val encoding: String,
                           val size: Int,
                           val name: String,
                           val path: String,
                           val sha: String,
                           val url: String, // URI?
                           val content: String,
                           val git_url: String,
                           val html_url: String,
                           val download_url: String,
                           val entries: Array<Entry>?,
                           val _links: _Link) {
        data class Entry(val type: String,
                         val size: Int,
                         val name: String,
                         val path: String,
                         val content: String,
                         val sha: String,
                         val url: String,
                         val git_url: String,
                         val html_url: String,
                         val download_url: String,
                         val _links: _Link)
        val rawContent by lazy { content.replace("\n", "").base64Decoded() }
    }

    // Generic
    data class _Link(val git: String,
                     val html: String,
                     val self: String)

    // https://docs.github.com/en/rest/git/refs?apiVersion=2022-11-28#get-a-reference

    val gitRef: Result<GetGitRefData, RemoteFailure>
        get() = getGitRef()

    fun getGitRef(branch: String = this@GitHubRepo.branch) = gitHub(GetGitRef(branch))

    inner class GetGitRef(branch: String) : GitHubAction<GetGitRefData> {
        val responseBody = Body.auto<GetGitRefData>().toLens()
        val uri = Uri.of("$api/$domain/git/refs/heads/$branch")

        override fun toRequest() = Request(Method.GET, uri)

        override fun toResult(response: Response) = when {
            response.status.successful -> Success(responseBody(response))
            else -> Failure(RemoteFailure(Method.GET, uri, response.status, response.bodyString()))
        }
    }

    data class GetGitRefData(val ref: String,
                             val node_id: String,
                             val url: String,
                             val `object`: Object) {
        data class Object(val type: String,
                          val sha: String,
                          val url: String)
    }


    // https://docs.github.com/en/rest/git/refs?apiVersion=2022-11-28#create-a-reference

    fun createRef(ref: String, sha: String) = gitHub(CreateRef(ref, sha))

    inner class CreateRef(val ref: String, val sha: String) : GitHubAction<CreateRefData> {
        val responseBody = Body.auto<CreateRefData>().toLens()
        val uri = Uri.of("$api/$domain/git/refs")

        override fun toRequest() = Request(Method.POST, uri)
            .body("""{"ref": "refs/heads/$ref", "sha": "$sha"}""")

        override fun toResult(response: Response) = when {
            response.status.successful -> Success(responseBody(response))
            else -> Failure(RemoteFailure(Method.POST, uri, response.status, response.bodyString()))
        }
    }

    data class CreateRefData(val ref: String)


    // https://docs.github.com/en/rest/repos/contents?apiVersion=2022-11-28#create-or-update-file-contents

    fun uploadContent(path: String, message: String,
                      content: File, sha: String? = null,
                      branch: String = this@GitHubRepo.branch,
                      committer: UploadContentData.Person? = null,
                      author: UploadContentData.Person? = committer) = gitHub(UploadContent(path, message, content, sha, branch, committer, author))

    inner class UploadContent(path: String, val message: String,
                              val content: File, val sha: String?,
                              val branch: String,
                              val committer: UploadContentData.Person?,
                              val author: UploadContentData.Person?) : GitHubAction<UploadContentData> {
        val responseBody = Body.auto<UploadContentData>().toLens()
        val uri = Uri.of("$api/$domain/contents/$path")
        val body = buildString {
            append("""{"message":"$message","content":"${content.readBytes().base64Encode()}",""")
            sha?.let { append(""""sha":"$it",""") }
            append(""""branch":"$branch"""")
            committer?.let { append(""","committer":${it.body}""") }
            author?.let { append(""","author":${it.body}""") }
            append("""}""")
        }

        override fun toRequest() = Request(Method.PUT, uri).body(body)

        override fun toResult(response: Response) = when {
            response.status.successful -> Success(responseBody(response))
            else -> Failure(RemoteFailure(Method.PUT, uri, response.status, response.bodyString()))
        }
    }

    data class UploadContentData(val content: Content,
                                 val commit: Commit) {
        data class Content(val name: String,
                           val path: String,
                           val sha: String,
                           val size: Int,
                           val url: String,
                           val html_url: String,
                           val git_url: String,
                           val download_url: String,
                           val type: String,
                           val _links: _Link)
        data class Commit(val sha: String,
                          val node_id: String,
                          val url: String,
                          val html_url: String,
                          val author: Person,
                          val committer: Person,
                          val message: String,
                          val tree: Tree,
                          val parents: Array<Parent>,
                          val verification: Verification)
        data class Person(val name: String, val email: String, val date: String? = null) {
            val body by lazy { """{"name":"$name","email":"$email"${date?.let { ""","date":"$it"""" } ?: ""}}""" }
        }
        data class Tree(val url: String, val sha: String)
        data class Parent(val url: String, val html_url: String, val sha: String)
        data class Verification(val verified: Boolean,
                                val reason: String,
                                val signature: String?,
                                val payload: String?)
    }


    // https://docs.github.com/en/rest/pulls/pulls?apiVersion=2022-11-28#create-a-pull-request

    fun createPR(title: String? = null, head: String, headRepo: String? = null,
                 base: String, body: String? = null, maintainerCanModify: Boolean? = null,
                 draft: Boolean? = null, issue: Int? = null) = gitHub(CreatePR(title, head, headRepo, base, body, maintainerCanModify, draft, issue))

    inner class CreatePR(title: String?, head: String, headRepo: String?,
                         base: String, body: String?, maintainerCanModify: Boolean?,
                         draft: Boolean?, issue: Int?) : GitHubAction<PushRequest> {
        val responseBody = Body.auto<PushRequest>().toLens()
        val uri = Uri.of("$api/$domain/pulls")
        val body = buildString {
            append("{")
            title?.let { append(""""title":"$it",""") }
            append(""""head":"$head",""")
            headRepo?.let { append(""""head_repo":"$it",""") }
            append(""""base":"$base"""")
            body?.let { append(""","head_repo":"$it"""") }
            maintainerCanModify?.let { append(""","maintainer_can_modify":"$it"""") }
            draft?.let { append(""","draft":"$it",""") }
            issue?.let { append(""","issue":"$it"""") }
            append("""}""")
        }

        override fun toRequest() = Request(Method.POST, uri).body(body)

        override fun toResult(response: Response) = when {
            response.status.successful -> Success(responseBody(response))
            else -> Failure(RemoteFailure(Method.POST, uri, response.status, response.bodyString()))
        }
    }

    data class PushRequest(val url: String,
                           val id: Int,
                           val node_id: String,
                           val html_url: String,
                           val diff_url: String,
                           val patch_url: String,
                           val issue_url: String,
                           val commits_url: String,
                           val review_comments_url: String,
                           val review_comment_url: String,
                           val comments_url: String,
                           val statuses_url: String,
                           val number: Int,
                           val state: State,
                           val locked: Boolean,
                           val title: String,
                           val user: User,
                           val body: String?,
                           val labels: Array<Label>) {
        enum class State { open, closed }
        data class User(val name: String?,
                        val email: String?,
                        val login: String,
                        val id: Int,
                        val node_id: String,
                        val avatar_url: String,
                        val gravatar_id: String,
                        val url: String,
                        val html_url: String,
                        val followers_url: String,
                        val following_url: String,
                        val gists_url: String,
                        val starred_url: String,
                        val subscriptions_url: String,
                        val organizations_url: String,
                        val repos_url: String,
                        val events_url: String,
                        val received_events_url: String,
                        val type: String,
                        val site_admin: Boolean,
                        val starred_at: String?)
        data class Label(val id: Long,
                         val node_id: String,
                         val url: String,
                         val name: String,
                         val description: String,
                         val color: String,
                         val default: Boolean)
    }

    // https://docs.github.com/en/rest/pulls/pulls?apiVersion=2022-11-28#create-a-pull-request

    val pullRequests: Result<PullRequests, RemoteFailure>
        get() = gitHub(GetPRs())

    inner class GetPRs : GitHubAction<PullRequests> {
        val responseBody = Body.auto<PullRequests>().toLens()
        val uri = Uri.of("$api/$domain/pulls")

        override fun toRequest() = Request(Method.GET, uri)

        override fun toResult(response: Response) = when {
            response.status.successful -> Success(responseBody(response))
            else -> Failure(RemoteFailure(Method.GET, uri, response.status, response.bodyString()))
        }
    }

    data class PullRequests(val pullRequests: Array<PushRequest>)

    companion object {
        val api = "https://api.github.com/repos"
    }
}


//class GitHubMavenRepo(domain: String, token: String) {
//
//}

fun GitHub.Companion.Http2(token: GitHubToken = GitHubToken.of(System.getenv("TOKEN")),
                           http: HttpHandler = JavaHttpClient(),
                           authScheme: String = "token") =
    object : GitHub {
        //        private val routedHttp = ClientFilters.SetBaseUriFrom(Uri.of("https://api.github.com"))
        //            .then(http)

        override fun <R> invoke(action: GitHubAction<R>) =
            action.toResult(
                http(action.toRequest()
                         .header("Accept", "application/vnd.github+json")
                         .header("Authorization", "$authScheme $token")))
    }

fun main() {
    //    val repo = GitHubRepo("elect86/fuzzy-octo-disco")
    //    var response = repo.getFile("org/gradle/sample/prova/maven-metadata.xml")
    //    println(response.valueOrNull()!!.rawContent)
    //    repo.branch = "branch0"
    //    response = repo.getFile("branchFile")
    //    println(response.get()::class)

    //    val request = Request(Method.GET, "https://api.github.com/repos/elect86/fuzzy-octo-disco/contents/branchFile?ref=branch0")
    //        val request = Request(Method.GET, "https://api.github.com/repos/elect86/fuzzy-octo-disco/contents/org/gradle/sample/prova/maven-metadata.xml")
    //            .header("Accept", "application/vnd.github.object+json")
    //            .header("Authorization", "token ${System.getenv("TOKEN")}")
    //    println(JavaHttpClient()(request))

    //    val body = Body.json().toLens()
    //    println(Request(Method.POST, Uri.of("")).with(body of mapOf("ref" to "refs/heads/featureA",
    //                                                                "sha" to "aa218f56b14c9653891f9e74264a383fa43fefbd").asJsonObject()))

    val gh: GitHubRepo
    //    val response: Failure = gh.getContent("").

    val body = Body.json().toLens()

    println(Request(Method.POST, Uri.of("")).with(body of mapOf("ref" to "refs/heads/featureA", "sha" to "aa218f56b14c9653891f9e74264a383fa43fefbd").asJsonObject()))
}