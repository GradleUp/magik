package magik

import dev.forkhandles.result4k.Failure
import dev.forkhandles.result4k.Result
import dev.forkhandles.result4k.Success
import org.http4k.connect.RemoteFailure
import org.http4k.connect.github.api.GitHub
import org.http4k.connect.github.api.GitHubAction
import org.http4k.core.*
import org.http4k.format.Jackson.auto

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