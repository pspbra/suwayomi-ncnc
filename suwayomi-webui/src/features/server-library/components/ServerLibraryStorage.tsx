/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

import Alert from '@mui/material/Alert';
import Button from '@mui/material/Button';
import LinearProgress from '@mui/material/LinearProgress';
import Stack from '@mui/material/Stack';
import Typography from '@mui/material/Typography';
import { useLingui } from '@lingui/react/macro';
import { requestManager } from '@/lib/requests/RequestManager.ts';

/** The server reports bytes as strings, they are displayed as binary sizes. */
const formatBytes = (bytes: number): string => {
    const units = ['B', 'KiB', 'MiB', 'GiB', 'TiB'];
    let value = bytes;
    let unitIndex = 0;

    while (value >= 1024 && unitIndex < units.length - 1) {
        value /= 1024;
        unitIndex += 1;
    }

    return `${unitIndex ? value.toFixed(2) : value} ${units[unitIndex]}`;
};

/**
 * Admin only.
 *
 * The download storage covers every downloaded file of the server, including files which were retained after a
 * title got removed from the server library. The disk values describe the volume the download directory is on.
 * Collecting these numbers walks the whole download directory, so they are only read on request and never polled.
 */
export const ServerLibraryStorage = () => {
    const { t } = useLingui();
    const { data, loading, error, refetch } = requestManager.useGetServerLibraryStorage();
    const storage = data?.serverLibraryStorage;

    return (
        <Stack spacing={1}>
            <Typography variant="body2" color="textSecondary">
                {t`Download storage includes retained files of removed titles. Disk space refers to the download volume.`}
            </Typography>
            {loading && <LinearProgress />}
            {error && <Alert severity="error">{error.message}</Alert>}
            {storage && (
                <Typography>
                    {t`Downloads`}: {formatBytes(Number(storage.downloadBytes))} · {t`Available`}:{' '}
                    {formatBytes(Number(storage.availableBytes))} / {formatBytes(Number(storage.totalBytes))} ·{' '}
                    {new Date(Number(storage.measuredAt) * 1000).toLocaleString()}
                </Typography>
            )}
            <Button
                disabled={loading}
                onClick={() => {
                    refetch().catch(() => {});
                }}
            >
                {t`Refresh storage statistics`}
            </Button>
        </Stack>
    );
};
