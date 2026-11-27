package suwayomi.tachidesk.graphql.types

import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import suwayomi.tachidesk.manga.model.table.ChapterTable
import suwayomi.tachidesk.graphql.server.primitives.Node
import suwayomi.tachidesk.manga.model.table.ChapterUserTable

class ChapterUserType(
    val isRead: Boolean,
    val isBookmarked: Boolean,
    val lastPageRead: Int,
    val lastReadAt: Long,
    val isDownloaded: Boolean,
    val chapterId: Int,
) : Node {
    constructor(row: ResultRow) : this(
        row[ChapterUserTable.isRead],
        row[ChapterUserTable.isBookmarked],
        row[ChapterUserTable.lastPageRead],
        row[ChapterUserTable.lastReadAt],
        row.getOrNull(ChapterTable.isDownloaded) ?: transaction {
            ChapterTable.selectAll().where { ChapterTable.id eq row[ChapterUserTable.chapter] }.first()[ChapterTable.isDownloaded]
        },
        row[ChapterUserTable.chapter].value,
    )
}
