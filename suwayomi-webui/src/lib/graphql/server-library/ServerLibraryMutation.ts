/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

import gql from 'graphql-tag';
import type { TypedDocumentNode } from '@apollo/client/core';
import { SERVER_LIBRARY_MANGA_FIELDS } from '@/lib/graphql/server-library/ServerLibraryFragments.ts';
import type { ServerLibraryMangaFieldsFragment } from '@/lib/graphql/server-library/ServerLibraryQuery.ts';

export type AddServerLibraryMangaMutation = {
    addServerLibraryManga: {
        __typename?: 'ServerLibraryMangaPayload';
        manga: ServerLibraryMangaFieldsFragment;
    };
};
export type AddServerLibraryMangaMutationVariables = { mangaId: number };

/**
 * Adds a title of a source to the server library. Idempotent and does not touch the personal library.
 *
 * This is the only way a source title becomes available for downloads, so it replaces the old "add to library"
 * action of the source browse pages.
 */
export const ADD_SERVER_LIBRARY_MANGA: TypedDocumentNode<
    AddServerLibraryMangaMutation,
    AddServerLibraryMangaMutationVariables
> = gql`
    ${SERVER_LIBRARY_MANGA_FIELDS}

    mutation ADD_SERVER_LIBRARY_MANGA($mangaId: Int!) {
        addServerLibraryManga(input: { mangaId: $mangaId }) {
            manga {
                ...SERVER_LIBRARY_MANGA_FIELDS
            }
        }
    }
`;

export type RemoveServerLibraryMangaMutation = {
    removeServerLibraryManga: {
        __typename?: 'ServerLibraryMangaPayload';
        manga: ServerLibraryMangaFieldsFragment;
    };
};
export type RemoveServerLibraryMangaMutationVariables = { mangaId: number; deleteDownloads: boolean };

/**
 * Removes a title from the server library. Pending downloads are always cancelled, the downloaded files are kept
 * unless `deleteDownloads` is set. Personal libraries, categories and reading history are never touched.
 */
export const REMOVE_SERVER_LIBRARY_MANGA: TypedDocumentNode<
    RemoveServerLibraryMangaMutation,
    RemoveServerLibraryMangaMutationVariables
> = gql`
    ${SERVER_LIBRARY_MANGA_FIELDS}

    mutation REMOVE_SERVER_LIBRARY_MANGA($mangaId: Int!, $deleteDownloads: Boolean!) {
        removeServerLibraryManga(input: { mangaId: $mangaId, deleteDownloads: $deleteDownloads }) {
            manga {
                ...SERVER_LIBRARY_MANGA_FIELDS
            }
        }
    }
`;

export type ServerLibraryDownloadsPayload = {
    __typename?: 'ServerLibraryDownloadsPayload';
    /** The chapters accepted by the server. Already downloaded chapters are filtered out. */
    chapterIds: number[];
};

export type EnqueueServerLibraryDownloadsMutation = {
    enqueueServerLibraryDownloads: ServerLibraryDownloadsPayload;
};
export type EnqueueServerLibraryDownloadsMutationVariables = { chapterIds: number[] };

/**
 * Enqueues chapters of the server library for the shared download queue. Deduplicates, skips chapters that are
 * already downloaded and requires the DOWNLOAD_CHAPTERS permission.
 *
 * The returned ids are the chapters that got enqueued, they are not a statement about who downloads them or
 * whether the download already finished.
 */
export const ENQUEUE_SERVER_LIBRARY_DOWNLOADS: TypedDocumentNode<
    EnqueueServerLibraryDownloadsMutation,
    EnqueueServerLibraryDownloadsMutationVariables
> = gql`
    mutation ENQUEUE_SERVER_LIBRARY_DOWNLOADS($chapterIds: [Int!]!) {
        enqueueServerLibraryDownloads(input: { chapterIds: $chapterIds }) {
            chapterIds
        }
    }
`;

export type DeleteServerLibraryDownloadsMutation = {
    deleteServerLibraryDownloads: ServerLibraryDownloadsPayload;
};
export type DeleteServerLibraryDownloadsMutationVariables = { chapterIds: number[] };

/**
 * Deletes downloaded files for every user and cancels their pending downloads. Requires the
 * MANAGE_SERVER_LIBRARY permission and also works for retained files of titles that are no longer part of the
 * server library.
 */
export const DELETE_SERVER_LIBRARY_DOWNLOADS: TypedDocumentNode<
    DeleteServerLibraryDownloadsMutation,
    DeleteServerLibraryDownloadsMutationVariables
> = gql`
    mutation DELETE_SERVER_LIBRARY_DOWNLOADS($chapterIds: [Int!]!) {
        deleteServerLibraryDownloads(input: { chapterIds: $chapterIds }) {
            chapterIds
        }
    }
`;

export type UpdateServerLibraryMutation = { updateServerLibrary: boolean };

/** Admin only. Metadata requests of the queued titles still honour the source metadata cache TTL. */
export const UPDATE_SERVER_LIBRARY: TypedDocumentNode<UpdateServerLibraryMutation> = gql`
    mutation UPDATE_SERVER_LIBRARY {
        updateServerLibrary
    }
`;
