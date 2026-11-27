import java.io.BufferedReader

/*
 * Copyright (C) Contributors to the Suwayomi project
 * 
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

const val MainClass = "suwayomi.tachidesk.MainKt"

// should be bumped with each stable release
val getTachideskVersion = { "v2.6" }

// Revision of the WebUI built from suwayomi-webui/ and bundled into this jar.
// It must match the "revision" file inside WebUI.zip and the release tag this
// fork publishes the WebUI zip under (see suwayomi-webui/versionToServerVersionMapping.json).
val webUIRevisionTag = "r1"

val webviewJbrRelease = "jbr-release-25.0.4b508.27"

private val getCommitCount = {
    runCatching {
        ProcessBuilder()
            .command("git", "rev-list", "HEAD", "--count")
            .start()
            .let { process ->
                process.waitFor()
                val output = process.inputStream.use {
                    it.bufferedReader().use(BufferedReader::readText)
                }
                process.destroy()
                output.trim()
            }
    }.getOrDefault("0")
}

// counts commits on the current checked out branch
val getTachideskRevision = { "r${getCommitCount()}" }
