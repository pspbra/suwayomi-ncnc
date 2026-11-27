package eu.kanade.tachiyomi.source.suwayomi

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException

/** Shared JWT authentication for GraphQL, downloads, reader images and Coil. */
class SuwayomiAuthInterceptor(
    private val preferences: SuwayomiServerPreferences,
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val initial = preferences.session ?: return chain.proceed(request)
        val server = initial.serverUrl.toHttpUrlOrNull() ?: return chain.proceed(request)
        val url = request.url
        if (url.scheme != server.scheme || url.host != server.host || url.port != server.port ||
            !url.encodedPath.startsWith(server.encodedPath.trimEnd('/') + "/")
        ) {
            return chain.proceed(request)
        }
        val expected = request.tag(SuwayomiAccount::class.java)?.key
        if (expected != null && expected != initial.accountKey) throw IOException("账号已切换，请重新打开页面")

        fun session(
            force: Boolean,
            previous: String? = null,
        ): SuwayomiSession =
            synchronized(SuwayomiLogin.refreshLock) {
                val current = preferences.session ?: throw IOException("请先登录服务器")
                if (current.accountKey != initial.accountKey) throw IOException("账号已切换，请重新打开页面")
                if (current.expiresSoon() || (force && current.accessToken == previous)) {
                    val refreshed = SuwayomiLogin.refresh(current)
                    // Never hold the preference lock during network I/O: Compose reads it too.
                    synchronized(SuwayomiLogin.lock) {
                        val latest = preferences.session
                        if (latest == null || latest.accountKey != current.accountKey || latest.refreshToken != current.refreshToken) {
                            throw IOException("账号会话已改变，请重试")
                        }
                        preferences.saveSession(refreshed)
                    }
                    refreshed
                } else {
                    current
                }
            }
        val active = session(false)

        fun send(value: SuwayomiSession) =
            chain.proceed(
                request.newBuilder().header("Authorization", "Bearer ${value.accessToken}").build(),
            )
        val response = send(active)
        // GraphQL represents authentication failures with HTTP 200 as well as HTTP 401.
        val unauthorized =
            response.code == 401 ||
                (
                    url.encodedPath.endsWith("/api/graphql") &&
                        response
                            .peekBody(65536)
                            .string()
                            .contains(Regex("\"message\"\\s*:\\s*\"[^\"]*Unauthorized"))
                )
        if (!unauthorized) return response
        response.close()
        return send(session(true, active.accessToken))
    }
}

/** Binds an in-flight operation to the user who initiated it. */
internal data class SuwayomiAccount(
    val key: String,
)
