package magik

import dev.forkhandles.result4k.*
import org.http4k.base64Decoded
import org.http4k.client.JavaHttpClient
import org.http4k.connect.RemoteFailure
import org.http4k.connect.github.GitHubToken
import org.http4k.connect.github.api.GitHub
import org.http4k.connect.github.api.GitHubAction
import org.http4k.core.*
import org.http4k.filter.ClientFilters
import org.http4k.format.Moshi.auto

class GetAuthenticatedUserEmails : GitHubAction<List<GetAuthenticatedUserEmails.ResponseData>> {
    val responseBody = Body.auto<Array<ResponseData>>().toLens()
    val uri = Uri.of("/user/emails")

    override fun toRequest() = Request(Method.GET, uri)

    override fun toResult(response: Response) = when {
        response.status.successful -> Success(responseBody(response).toList())
        else -> Failure(RemoteFailure(Method.GET, uri, response.status, response.bodyString()))
    }

    data class ResponseData(val email: String, val primary: Boolean, val verified: Boolean)
}

val GitHub.authenticatedUserEmails: Result<List<GetAuthenticatedUserEmails.ResponseData>, RemoteFailure>
    get() = invoke(GetAuthenticatedUserEmails())

private val gitHub = GitHub.Http2()

open class GitHubRepo(val domain: String, var branch: String = "master") {
    //    infix fun getFile(path: String) = GitHub(GetFile())

    fun getFile(path: String) = gitHub(GetFile(path))

    inner class GetFile(val path: String) : GitHubAction<GetFileData> {
        val responseBody = Body.auto<GetFileData>().toLens()
        val uri
            get() = Uri.of("$api/$domain/contents/$path?ref=$branch")

        override fun toRequest() = Request(Method.GET, uri)
            .header("Accept", "application/vnd.github+json")

        override fun toResult(response: Response) = when {
            response.status.successful -> Success(responseBody(response))
            else -> Failure(RemoteFailure(Method.GET, uri, response.status, response.bodyString()))
        }
    }

    data class _Link(val git: String, val self: String, val html: String)
    data class GetFileData(val type: String,
                           val encoding: String,
                           val size: Int,
                           val path: String,
                           val content: String,
                           val sha: String,
                           val url: String,
                           val git_url: String,
                           val html_url: String,
                           val download_url: String,
                           val _links: _Link) {
        val rawContent by lazy { content.replace("\n", "").base64Decoded() }
    }

    companion object {
        val api = "https://api.github.com/repos"
    }
}


//class GitHubMavenRepo(domain: String, token: String) {
//
//}

fun main() {
    val repo = GitHubRepo("elect86/fuzzy-octo-disco")
    var response = repo.getFile("org/gradle/sample/prova/maven-metadata.xml")
    println(response.valueOrNull()!!.rawContent)
    repo.branch = "branch0"
    response = repo.getFile("branchFile")
    println(response.get()::class)
    //    val request = Request(Method.GET, "https://api.github.com/repos/elect86/fuzzy-octo-disco/contents/branchFile?ref=branch0")
    //        val request = Request(Method.GET, "https://api.github.com/repos/elect86/fuzzy-octo-disco/contents/org/gradle/sample/prova/maven-metadata.xml")
    //            .header("Accept", "application/vnd.github.object+json")
    //            .header("Authorization", "token ${System.getenv("TOKEN")}")
    //    println(JavaHttpClient()(request))
}

fun GitHub.Companion.Http2(
    token: GitHubToken = GitHubToken.of(System.getenv("TOKEN")),
    http: HttpHandler = JavaHttpClient(),
    authScheme: String = "token"
) =
    object : GitHub {
        //        private val routedHttp = ClientFilters.SetBaseUriFrom(Uri.of("https://api.github.com"))
        //            .then(http)

        override fun <R> invoke(action: GitHubAction<R>) =
            action.toResult(
                http(action.toRequest()
                         .header("Authorization", "$authScheme $token"))
            )
    }
