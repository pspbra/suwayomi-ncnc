package tachiyomi.data.release

import android.os.Build
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.network.awaitSuccess
import eu.kanade.tachiyomi.network.parseAs
import kotlinx.serialization.json.Json
import tachiyomi.domain.release.interactor.GetApplicationRelease
import tachiyomi.domain.release.model.Release
import tachiyomi.domain.release.service.ReleaseService

@Inject
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
class ReleaseServiceImpl(
    private val networkService: NetworkHelper,
    private val json: Json,
) : ReleaseService {

    override suspend fun latest(arguments: GetApplicationRelease.Arguments): Release? {
        val release = with(json) {
            val releases =
                networkService.client
                    .newCall(GET("https://api.github.com/repos/${arguments.repository}/releases?per_page=100"))
                    .awaitSuccess()
                    .parseAs<List<GithubRelease>>()

            // The fork's repository also hosts the server and WebUI releases; only a
            // release that actually ships an APK this build can install is relevant.
            releases
                .asSequence()
                .mapNotNull { candidate ->
                    getDownloadLink(release = candidate, isFoss = arguments.isFoss)?.let { link -> candidate to link }
                }
                .firstOrNull()
                ?.let { (candidate, link) ->
                    Release(
                        version = candidate.version,
                        info = candidate.info.substringBeforeLast("<!-->").replace(gitHubUsernameMentionRegex) { mention ->
                            "[${mention.value}](https://github.com/${mention.value.substring(1)})"
                        },
                        releaseLink = candidate.releaseLink,
                        downloadLink = link,
                    )
                }
                ?: return null
        }

        return release
    }

    private fun getDownloadLink(release: GithubRelease, isFoss: Boolean): String? {
        // Only consider actual APKs; this repository also publishes the server jar and
        // the WebUI zip, which must never be offered as an app update.
        val apkAssets = release.assets.filter { it.name.endsWith(".apk", ignoreCase = true) }

        val wantedBuildType = if (isFoss) FOSS else Build.SUPPORTED_ABIS[0]
        val asset = apkAssets.find { "-$wantedBuildType" in it.name }
            ?: if (isFoss) null else apkAssets.find { BUILD_TYPES.none { type -> "-$type" in it.name } }

        return asset?.downloadLink
    }

    companion object {
        private const val FOSS = "foss"
        private val BUILD_TYPES = listOf(FOSS, "arm64-v8a", "armeabi-v7a", "x86_64", "x86")

        /**
         * Regular expression that matches a mention to a valid GitHub username, like it's
         * done in GitHub Flavored Markdown. It follows these constraints:
         *
         * - Alphanumeric with single hyphens (no consecutive hyphens)
         * - Cannot begin or end with a hyphen
         * - Max length of 39 characters
         *
         * Reference: https://stackoverflow.com/a/30281147
         */
        private val gitHubUsernameMentionRegex = """\B@([a-z0-9](?:-(?=[a-z0-9])|[a-z0-9]){0,38}(?<=[a-z0-9]))"""
            .toRegex(RegexOption.IGNORE_CASE)
    }
}
