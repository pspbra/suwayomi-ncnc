package suwayomi.tachidesk.global.impl

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.NetworkHelper
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import suwayomi.tachidesk.manga.impl.util.network.await
import uy.kohesive.injekt.injectLazy

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

data class UpdateDataClass(
    /** [channel] mirrors [suwayomi.tachidesk.server.BuildConfig.BUILD_TYPE] */
    val channel: String,
    val tag: String,
    val url: String,
)

object AppUpdate {
    // This fork publishes the server, the WebUI and the Android app from a single
    // repository, so the latest *server* release is selected by the presence of a
    // server jar instead of taking "releases/latest" (which may be any of the three).
    private const val RELEASES_URL = "https://api.github.com/repos/pspbra/suwayomi-ncnc/releases?per_page=100"
    private const val SERVER_RELEASE_ASSET_SUFFIX = ".jar"

    private val json: Json by injectLazy()
    private val network: NetworkHelper by injectLazy()

    suspend fun checkUpdate(): List<UpdateDataClass> {
        val releases =
            json
                .parseToJsonElement(
                    network.client
                        .newCall(GET(RELEASES_URL))
                        .await()
                        .body
                        .string(),
                ).jsonArray
                .map { it.jsonObject }
                // Only releases that ship a server jar are server updates.
                .filter { release ->
                    release["assets"]?.jsonArray?.any { asset ->
                        asset.jsonObject["name"]?.jsonPrimitive?.content?.endsWith(SERVER_RELEASE_ASSET_SUFFIX) == true
                    } == true
                }

        // Releases are returned newest first.
        val latestServerRelease = releases.firstOrNull() ?: return emptyList()

        val tag = latestServerRelease["tag_name"]!!.jsonPrimitive.content
        val url = latestServerRelease["html_url"]!!.jsonPrimitive.content

        // The WebUI matches an entry by its own build type ("Stable"/"Preview"), so both
        // channels are reported with the same tag; this fork has a single release stream.
        return listOf(
            UpdateDataClass("Stable", tag, url),
            UpdateDataClass("Preview", tag, url),
        )
    }
}
