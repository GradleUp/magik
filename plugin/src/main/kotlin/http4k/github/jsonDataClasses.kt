package http4k.github

// Generic
data class _Link(val git: String,
                 val html: String,
                 val self: String)

data class GetGitRefData(val ref: String,
                         val node_id: String,
                         val url: String,
                         val `object`: Object) {
    data class Object(val type: String,
                      val sha: String,
                      val url: String)
}

data class CreateRefData(val ref: String)

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
    // TODO finish
}

data class PullRequests(val pullRequests: Array<PushRequest>)

data class CommitData(val url: String,
                      val sha: String,
                      val node_id: String,
                      val html_url: String,
                      val comments_url: String,
                      val commit: Commit) {
    data class Commit(
        val url: String,
        // TODO finish
    )
    // TODO finish
}

data class PullRequestMergeResult(val sha: String,
                                  val merged: Boolean,
                                  val message: String)

enum class MergeMethod { merge, squash, rebase }