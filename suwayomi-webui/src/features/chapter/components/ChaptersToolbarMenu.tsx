/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

import FilterList from '@mui/icons-material/FilterList';
import IconButton from '@mui/material/IconButton';
import * as React from 'react';
import DoneAllIcon from '@mui/icons-material/DoneAll';
import { useMemo } from 'react';
import { useLingui } from '@lingui/react/macro';
import { CustomTooltip } from '@/base/components/CustomTooltip.tsx';
import { ChapterOptions } from '@/features/chapter/components/ChapterOptions.tsx';
import type { updateChapterListOptions } from '@/features/chapter/utils/ChapterList.util.tsx';
import { isFilterActive } from '@/features/chapter/utils/ChapterList.util.tsx';
import type {
    ChapterBookmarkInfo,
    ChapterDownloadInfo,
    ChapterIdInfo,
    ChapterListOptions,
    ChapterReadInfo,
} from '@/features/chapter/Chapter.types.ts';
import { Chapters } from '@/features/chapter/services/Chapters.ts';

interface IProps {
    mangaId: number;
    options: ChapterListOptions;
    updateOption: ReturnType<typeof updateChapterListOptions>;
    chapters: (ChapterIdInfo & ChapterReadInfo & ChapterDownloadInfo & ChapterBookmarkInfo)[];
    scanlators: string[];
    excludeScanlators: string[];
}

export const ChaptersToolbarMenu = ({
    mangaId,
    options,
    updateOption,
    chapters,
    scanlators,
    excludeScanlators,
}: IProps) => {
    const { t } = useLingui();

    const [open, setOpen] = React.useState(false);
    const isFiltered = isFilterActive(options);

    const areAllChaptersRead = useMemo(() => chapters.every(Chapters.isRead), [chapters]);

    return (
        <>
            <CustomTooltip title={t`Mark all as read`} disabled={areAllChaptersRead}>
                <IconButton
                    disabled={areAllChaptersRead}
                    onClick={() => Chapters.markAsRead(Chapters.getNonRead(chapters), true, mangaId)}
                    color="inherit"
                >
                    <DoneAllIcon />
                </IconButton>
            </CustomTooltip>
            <CustomTooltip title={t`Filter and sort`}>
                <IconButton onClick={() => setOpen(true)} color="inherit">
                    <FilterList color={isFiltered ? 'warning' : undefined} />
                </IconButton>
            </CustomTooltip>
            <ChapterOptions
                open={open}
                onClose={() => setOpen(false)}
                options={options}
                updateOption={updateOption}
                scanlators={scanlators}
                excludedScanlators={excludeScanlators}
            />
        </>
    );
};
