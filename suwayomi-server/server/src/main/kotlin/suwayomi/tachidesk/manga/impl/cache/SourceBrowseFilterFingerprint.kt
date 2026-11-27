package suwayomi.tachidesk.manga.impl.cache

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import io.github.oshai.kotlinlogging.KotlinLogging
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.security.MessageDigest

object SourceBrowseFilterFingerprint {
    private val logger = KotlinLogging.logger {}

    /** Returns null for an unknown filter shape, allowing callers to safely bypass search caching. */
    fun create(filters: FilterList): String? =
        try {
            val bytes =
                ByteArrayOutputStream().use { output ->
                    DataOutputStream(output).use { data ->
                        data.writeInt(filters.size)
                        filters.forEach { writeFilter(data, it) }
                    }
                    output.toByteArray()
                }
            MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it.toInt() and 0xff) }
        } catch (exception: Exception) {
            logger.debug(exception) { "Source browse cache bypassed because the filter state cannot be fingerprinted" }
            null
        }

    private fun writeFilter(
        output: DataOutputStream,
        filter: Filter<*>,
    ) {
        output.writeUTF(filter::class.qualifiedName ?: filter::class.java.name)
        output.writeUTF(filter.name)
        when (filter) {
            is Filter.Header, is Filter.Separator -> {
                Unit
            }

            is Filter.Select<*> -> {
                output.writeInt(filter.state)
            }

            is Filter.Text -> {
                output.writeUTF(filter.state)
            }

            is Filter.CheckBox -> {
                output.writeBoolean(filter.state)
            }

            is Filter.TriState -> {
                output.writeInt(filter.state)
            }

            is Filter.Sort -> {
                output.writeBoolean(filter.state != null)
                filter.state?.let {
                    output.writeInt(it.index)
                    output.writeBoolean(it.ascending)
                }
            }

            is Filter.Group<*> -> {
                output.writeInt(filter.state.size)
                filter.state.forEach { child ->
                    require(child is Filter<*>) { "Unsupported filter group item: ${child?.let { it::class.qualifiedName }}" }
                    writeFilter(output, child)
                }
            }

            else -> {
                error("Unsupported filter type: ${filter::class.qualifiedName}")
            }
        }
    }
}
