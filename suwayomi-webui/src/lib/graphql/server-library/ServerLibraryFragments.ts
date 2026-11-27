/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

import gql from 'graphql-tag';

/**
 * Fields of a title in the server library.
 *
 * `inServerLibrary` is the shared collection of the whole server, `inLibrary` only the collection of the
 * current user. The shared download state is not part of the manga itself, it is queried per chapter
 * (`isDownloaded`) and per server library entry (`downloadedChapterCount`).
 */
export const SERVER_LIBRARY_MANGA_FIELDS = gql`
    fragment SERVER_LIBRARY_MANGA_FIELDS on MangaType {
        id
        title
        thumbnailUrl
        thumbnailUrlLastFetched
        sourceId
        inLibrary
        inServerLibrary
        serverLibraryAddedAt
        source {
            id
            name
            displayName
        }
    }
`;

export const SERVER_LIBRARY_CHAPTER_FIELDS = gql`
    fragment SERVER_LIBRARY_CHAPTER_FIELDS on ChapterType {
        id
        name
        sourceOrder
        isDownloaded
    }
`;
