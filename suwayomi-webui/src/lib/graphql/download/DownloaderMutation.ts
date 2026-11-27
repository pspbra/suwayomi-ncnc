/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

import gql from 'graphql-tag';
import { DOWNLOAD_STATUS_FIELDS } from '@/lib/graphql/download/DownloadFragments.ts';

export const CLEAR_DOWNLOADER = gql`
    ${DOWNLOAD_STATUS_FIELDS}

    mutation CLEAR_DOWNLOADER($input: ClearDownloaderInput = {}) {
        clearDownloader(input: $input) {
            downloadStatus {
                ...DOWNLOAD_STATUS_FIELDS
            }
        }
    }
`;

export const DEQUEUE_CHAPTER_DOWNLOAD = gql`
    ${DOWNLOAD_STATUS_FIELDS}

    mutation DEQUEUE_CHAPTER_DOWNLOAD($input: DequeueChapterDownloadInput!) {
        dequeueChapterDownload(input: $input) {
            downloadStatus {
                ...DOWNLOAD_STATUS_FIELDS
            }
        }
    }
`;

export const DEQUEUE_CHAPTER_DOWNLOADS = gql`
    ${DOWNLOAD_STATUS_FIELDS}

    mutation DEQUEUE_CHAPTER_DOWNLOADS($input: DequeueChapterDownloadsInput!) {
        dequeueChapterDownloads(input: $input) {
            downloadStatus {
                ...DOWNLOAD_STATUS_FIELDS
            }
        }
    }
`;

export const REORDER_CHAPTER_DOWNLOAD = gql`
    ${DOWNLOAD_STATUS_FIELDS}

    mutation REORDER_CHAPTER_DOWNLOAD($input: ReorderChapterDownloadInput!) {
        reorderChapterDownload(input: $input) {
            downloadStatus {
                ...DOWNLOAD_STATUS_FIELDS
            }
        }
    }
`;

export const REORDER_CHAPTER_DOWNLOADS = gql`
    ${DOWNLOAD_STATUS_FIELDS}

    mutation REORDER_CHAPTER_DOWNLOADS($input: ReorderChapterDownloadsInput!) {
        reorderChapterDownloads(input: $input) {
            downloadStatus {
                ...DOWNLOAD_STATUS_FIELDS
            }
        }
    }
`;

export const START_DOWNLOADER = gql`
    mutation START_DOWNLOADER($input: StartDownloaderInput = {}) {
        startDownloader(input: $input) {
            downloadStatus {
                state
            }
        }
    }
`;

export const STOP_DOWNLOADER = gql`
    mutation STOP_DOWNLOADER($input: StopDownloaderInput = {}) {
        stopDownloader(input: $input) {
            downloadStatus {
                state
            }
        }
    }
`;
