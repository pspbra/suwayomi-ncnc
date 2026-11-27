/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

import gql from 'graphql-tag';
import type { TypedDocumentNode } from '@apollo/client/core';
import { PAGE_INFO } from '@/lib/graphql/common/Fragments.ts';
import {
    SERVER_LIBRARY_CHAPTER_FIELDS,
    SERVER_LIBRARY_MANGA_FIELDS,
} from '@/lib/graphql/server-library/ServerLibraryFragments.ts';

export type ServerLibraryMangaFieldsFragment = {
    __typename?: 'MangaType';
    id: number;
    title: string;
    thumbnailUrl: string | null;
    thumbnailUrlLastFetched: string | null;
    sourceId: string;
    inLibrary: boolean;
    inServerLibrary: boolean;
    serverLibraryAddedAt: string;
    source: { __typename?: 'SourceType'; id: string; name: string; displayName: string } | null;
};

export type ServerLibraryChapterFieldsFragment = {
    __typename?: 'ChapterType';
    id: number;
    name: string;
    sourceOrder: number;
    isDownloaded: boolean;
};

export type ServerLibraryEntry = {
    __typename?: 'ServerLibraryEntry';
    chapterCount: number;
    downloadedChapterCount: number;
    queuedChapterCount: number;
    manga: ServerLibraryMangaFieldsFragment;
};

export type GetServerLibraryQuery = {
    serverLibrary: {
        __typename?: 'ServerLibraryPage';
        totalCount: number;
        hasNextPage: boolean;
        items: ServerLibraryEntry[];
    };
};
export type GetServerLibraryQueryVariables = {
    offset: number;
    limit: number;
    search: string;
};

/**
 * The server library is shared by every user and is not filtered by the current user. `chapterCount` is the
 * number of currently cached chapters, so it can still grow while the initial metadata update is running.
 */
export const GET_SERVER_LIBRARY: TypedDocumentNode<GetServerLibraryQuery, GetServerLibraryQueryVariables> = gql`
    ${SERVER_LIBRARY_MANGA_FIELDS}

    query GET_SERVER_LIBRARY($offset: Int!, $limit: Int!, $search: String!) {
        serverLibrary(offset: $offset, limit: $limit, search: $search) {
            totalCount
            hasNextPage
            items {
                manga {
                    ...SERVER_LIBRARY_MANGA_FIELDS
                }
                chapterCount
                downloadedChapterCount
                queuedChapterCount
            }
        }
    }
`;

export type GetServerLibraryMangaQuery = {
    manga: ServerLibraryMangaFieldsFragment;
    chapters: {
        __typename?: 'ChapterNodeList';
        totalCount: number;
        pageInfo: { __typename?: 'PageInfo'; hasPreviousPage: boolean; hasNextPage: boolean };
        nodes: ServerLibraryChapterFieldsFragment[];
    };
};
export type GetServerLibraryMangaQueryVariables = {
    mangaId: number;
};

/**
 * The manga of a server library entry and all of its chapters.
 *
 * The chapters are ordered by source order descending, which - unlike the upload date - is also set for chapters
 * of sources that do not provide dates. They are fetched at once because they are rendered as a virtualized list
 * (the personal chapter page loads them the same way). The manga is queried as well because a title can be removed
 * from the server library while its chapter page is open.
 *
 * `pageInfo` is not used by this page but is part of the selection because the chapters of a manga are cached under
 * one key which is shared with the personal chapter page - the cache merge of the chapters field reads it.
 */
export const GET_SERVER_LIBRARY_MANGA: TypedDocumentNode<
    GetServerLibraryMangaQuery,
    GetServerLibraryMangaQueryVariables
> = gql`
    ${SERVER_LIBRARY_MANGA_FIELDS}
    ${SERVER_LIBRARY_CHAPTER_FIELDS}
    ${PAGE_INFO}

    query GET_SERVER_LIBRARY_MANGA($mangaId: Int!) {
        manga(id: $mangaId) {
            ...SERVER_LIBRARY_MANGA_FIELDS
        }
        chapters(condition: { mangaId: $mangaId }, order: [{ by: SOURCE_ORDER, byType: DESC }]) {
            totalCount
            pageInfo {
                ...PAGE_INFO
            }
            nodes {
                ...SERVER_LIBRARY_CHAPTER_FIELDS
            }
        }
    }
`;

export type GetServerLibraryStorageQuery = {
    serverLibraryStorage: {
        __typename?: 'ServerLibraryStorage';
        downloadBytes: string;
        availableBytes: string;
        totalBytes: string;
        measuredAt: string;
    };
};

/** Admin only. Measuring walks the whole download directory, so this must not be polled. */
export const GET_SERVER_LIBRARY_STORAGE: TypedDocumentNode<GetServerLibraryStorageQuery> = gql`
    query GET_SERVER_LIBRARY_STORAGE {
        serverLibraryStorage {
            downloadBytes
            availableBytes
            totalBytes
            measuredAt
        }
    }
`;
