package eu.kanade.presentation.more.settings.screen.debug

import android.content.Context
import eu.kanade.domain.ui.UiPreferences
import eu.kanade.tachiyomi.BuildConfig
import eu.kanade.tachiyomi.util.lang.toDateTimestampString
import eu.kanade.tachiyomi.util.system.isFossBuildType
import eu.kanade.tachiyomi.util.system.isNightlyBuildType
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import mihon.app.di.appGraph
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import kotlin.time.Instant

internal object AppVersionInfo {
    fun getVersionName(): String =
        when {
            BuildConfig.DEBUG -> {
                "Debug ${BuildConfig.COMMIT_SHA}"
            }

            isNightlyBuildType -> {
                "Nightly r${BuildConfig.COMMIT_COUNT} (${BuildConfig.COMMIT_SHA})"
            }

            else -> {
                val channel = if (isFossBuildType) "FOSS" else "Stable"
                "$channel v${BuildConfig.VERSION_NAME}"
            }
        }

    fun getFormattedBuildTime(): String =
        try {
            Instant
                .parse(BuildConfig.BUILD_TIME)
                .toLocalDateTime(TimeZone.currentSystemDefault())
                .toDateTimestampString(
                    UiPreferences.dateFormat(
                        Injekt
                            .get<Context>()
                            .appGraph.uiPreferences.dateFormat
                            .get(),
                    ),
                )
        } catch (_: Exception) {
            BuildConfig.BUILD_TIME
        }
}
