package eu.kanade.tachiyomi.source.suwayomi

import android.content.Context
import kotlinx.coroutines.ensureActive

class SuwayomiServerPreferences(
    context: Context,
) {
    private val preferences = context.applicationContext.getSharedPreferences(PREFERENCE_FILE, Context.MODE_PRIVATE)

    init {
        accountChanges.value = accountKey
    }

    val isConfigured: Boolean
        get() = session != null

    val serverUrl: String
        get() =
            preferences
                .getString(KEY_URL, DEFAULT_URL)
                .orEmpty()
                .trim()
                .trimEnd('/')

    val username: String
        get() = preferences.getString(KEY_USERNAME, "").orEmpty()

    val password: String
        get() = preferences.getString(KEY_PASSWORD, "").orEmpty()

    val session: SuwayomiSession?
        get() =
            synchronized(SuwayomiLogin.lock) {
                val userId = preferences.getString("user_id", null) ?: return@synchronized null
                SuwayomiSession(
                    serverUrl,
                    username,
                    userId,
                    preferences.getString("access_token", "").orEmpty(),
                    preferences.getString("refresh_token", "").orEmpty(),
                )
            }

    val accountKey: String get() = session?.accountKey.orEmpty()

    suspend fun login(
        serverUrl: String,
        username: String,
        password: String,
    ) {
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            val session = SuwayomiLogin.login(normalizeUrl(serverUrl), username, password)
            kotlinx.coroutines.currentCoroutineContext().ensureActive()
            synchronized(SuwayomiLogin.lock) {
                saveSession(session)
            }
        }
    }

    internal fun saveSession(session: SuwayomiSession) {
        preferences
            .edit()
            .putString(KEY_URL, session.serverUrl)
            .putString(KEY_USERNAME, session.username)
            .remove(KEY_PASSWORD)
            .putString("user_id", session.userId)
            .putString("access_token", session.accessToken)
            .putString("refresh_token", session.refreshToken)
            .commit()
        accountChanges.value = session.accountKey
    }

    val knownSourceIds: Set<Long>
        get() =
            preferences
                .getStringSet("known_source_ids", emptySet())
                .orEmpty()
                .mapNotNull { it.toLongOrNull() }
                .toSet()

    fun rememberSources(ids: List<Long>) =
        synchronized(SuwayomiLogin.lock) {
            preferences.edit().putStringSet("known_source_ids", (knownSourceIds + ids).map { it.toString() }.toSet()).apply()
        }

    fun mangaUrl(id: Int): String = "account/$accountKey/$id"

    fun mangaId(url: String): Int {
        check(url.startsWith("account/$accountKey/") && accountKey.isNotEmpty()) {
            "这部漫画属于旧连接或其他账号，请从当前账号的浏览页重新打开"
        }
        return url.substringAfterLast('/').toInt()
    }

    fun clear() =
        synchronized(SuwayomiLogin.lock) {
            preferences
                .edit()
                .remove("user_id")
                .remove("access_token")
                .remove("refresh_token")
                .remove(KEY_PASSWORD)
                .commit()
            accountChanges.value = ""
        }

    private fun normalizeUrl(value: String): String {
        val trimmed = value.trim().trimEnd('/')
        return if (trimmed.isNotBlank() && "://" !in trimmed) "https://$trimmed" else trimmed
    }

    companion object {
        val accountChanges = kotlinx.coroutines.flow.MutableStateFlow("")
        const val PREFERENCE_FILE = "suwayomi_server"
        const val KEY_URL = "url"
        const val KEY_USERNAME = "username"
        const val KEY_PASSWORD = "password"
        const val DEFAULT_URL = "https://mangasub.filexiazaiwr.work:82"
    }
}
