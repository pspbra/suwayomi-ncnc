package eu.kanade.tachiyomi.source.suwayomi

import android.util.Base64
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

data class SuwayomiSession(
    val serverUrl: String,
    val username: String,
    val userId: String,
    val accessToken: String,
    val refreshToken: String,
) {
    val accountKey: String
        get() =
            MessageDigest
                .getInstance("SHA-256")
                .digest("$serverUrl\n$userId".toByteArray())
                .joinToString("") { "%02x".format(it) }

    fun expiresSoon(): Boolean =
        runCatching {
            val payload = String(Base64.decode(accessToken.split('.')[1], Base64.URL_SAFE or Base64.NO_WRAP))
            val expiry =
                Json
                    .parseToJsonElement(payload)
                    .jsonObject["exp"]
                    ?.jsonPrimitive
                    ?.longOrNull ?: 0L
            expiry <= System.currentTimeMillis() / 1000 + 30
        }.getOrDefault(true)
}

/** No cookies, logging interceptors or redirects on the credential transport. */
internal object SuwayomiLogin {
    val lock = Any()
    val refreshLock = Any()
    private val client =
        OkHttpClient
            .Builder()
            .followRedirects(false)
            .followSslRedirects(false)
            .callTimeout(30, TimeUnit.SECONDS)
            .build()

    fun login(
        url: String,
        username: String,
        password: String,
    ): SuwayomiSession {
        val tokens =
            call(
                url,
                "mutation Login(\$username: String!, \$password: String!) { login(input: { username: \$username, password: \$password }) { accessToken refreshToken } }",
                buildJsonObject {
                    put("username", username.trim())
                    put("password", password)
                },
            ).getValue("login").jsonObject
        val access = tokens.getValue("accessToken").jsonPrimitive.content
        val user = call(url, "query { user { id username } }", token = access).getValue("user").jsonObject
        return SuwayomiSession(
            url,
            user.getValue("username").jsonPrimitive.content,
            user.getValue("id").jsonPrimitive.content,
            access,
            tokens.getValue("refreshToken").jsonPrimitive.content,
        )
    }

    fun refresh(session: SuwayomiSession): SuwayomiSession {
        val result =
            call(
                session.serverUrl,
                "mutation Refresh(\$token: String!) { refreshToken(input: { refreshToken: \$token }) { accessToken } }",
                buildJsonObject { put("token", session.refreshToken) },
            )
        return session.copy(
            accessToken =
                result
                    .getValue("refreshToken")
                    .jsonObject
                    .getValue("accessToken")
                    .jsonPrimitive.content,
        )
    }

    private fun call(
        url: String,
        query: String,
        variables: JsonObject = buildJsonObject {},
        token: String? = null,
    ): JsonObject {
        val payload =
            buildJsonObject {
                put("query", query)
                put("variables", variables)
            }
        val request =
            Request
                .Builder()
                .url("$url/api/graphql")
                .post(payload.toString().toRequestBody("application/json".toMediaType()))
                .apply { if (token != null) header("Authorization", "Bearer $token") }
                .build()
        return client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("登录请求失败（HTTP ${response.code}），请检查服务器地址和账号")
            val root = Json.parseToJsonElement(response.body.string()).jsonObject
            if (!root["errors"]?.jsonArray.isNullOrEmpty()) {
                throw IOException("登录或令牌刷新失败，请在服务器设置中重新登录")
            }
            root.getValue("data").jsonObject
        }
    }
}
