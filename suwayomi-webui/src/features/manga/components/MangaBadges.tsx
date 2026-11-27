/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

import { styled } from '@mui/material/styles';
import Button from '@mui/material/Button';
import Typography from '@mui/material/Typography';
import BookmarkAddOutlined from '@mui/icons-material/BookmarkAddOutlined';
import type { MouseEvent } from 'react';
import { useLingui } from '@lingui/react/macro';
import type { MangaCardMode } from '@/features/manga/Manga.types.ts';
import { MediaQuery } from '@/base/utils/MediaQuery.tsx';
import { useMetadataServerSettings } from '@/features/settings/services/ServerSettingsMetadata.ts';
import { CustomTooltip } from '@/base/components/CustomTooltip.tsx';
import { MUIUtil } from '@/lib/mui/MUI.util.ts';

const BadgeContainer = styled('div')(({ theme }) => ({
    display: 'flex',
    // the badges have to stay inside the card, a source with a long name shrinks instead (see the name badge)
    minWidth: 0,
    maxWidth: '100%',
    height: 'fit-content',
    borderRadius: theme.shape.borderRadius,
    overflow: 'hidden',
}));

const Badge = styled(Typography)(({ theme }) => ({
    color: theme.palette.primary.contrastText,
    paddingInline: theme.spacing(0.3),
}));

/** Badges which only offer information are not allowed to be squeezed by the name of the source. */
const NON_SHRINKING_BADGE_SX = { flexShrink: 0, whiteSpace: 'nowrap' } as const;

/** Touch devices cannot hover, the action is thus always visible and only shows its icon to save space. */
const TOUCH_BUTTON_SX = { minWidth: 'unset', p: 0.25 } as const;

/**
 * The download state of a title of the shared collection of the server.
 *
 * The collections of the server do not know the downloads of a single user, the numbers describe the files which
 * are stored for everybody. The total number of chapters is part of the badge because it is what the downloaded
 * number is relative to, it is still growing while the initial metadata update of a newly added title is running.
 *
 * Unlike the downloads of the personal library ("showDownloadBadge") these numbers are always shown - they are the
 * shared state of the server the server library manages and the reason its cards exist.
 */
const ServerLibraryDownloadBadges = ({
    chapterCount,
    downloadedChapterCount,
    queuedChapterCount,
}: {
    chapterCount: number;
    downloadedChapterCount: number;
    queuedChapterCount: number;
}) => {
    const { t } = useLingui();

    const hasChapters = chapterCount > 0;

    return (
        <>
            {(hasChapters || downloadedChapterCount > 0) && (
                <CustomTooltip title={t`Downloaded chapters`}>
                    <Badge
                        sx={{
                            ...NON_SHRINKING_BADGE_SX,
                            backgroundColor: 'secondary.main',
                            color: 'secondary.contrastText',
                        }}
                    >
                        {hasChapters ? `${downloadedChapterCount}/${chapterCount}` : downloadedChapterCount}
                    </Badge>
                </CustomTooltip>
            )}
            {queuedChapterCount > 0 && (
                <CustomTooltip title={t`Queued chapters`}>
                    <Badge
                        sx={{
                            ...NON_SHRINKING_BADGE_SX,
                            backgroundColor: 'warning.main',
                            color: 'warning.contrastText',
                        }}
                    >
                        {`+${queuedChapterCount}`}
                    </Badge>
                </CustomTooltip>
            )}
        </>
    );
};

export const MangaBadges = ({
    inLibraryIndicator,
    addToServerLibrary,
    updateLibraryState,
    isInLibrary,
    isInServerLibrary,
    unread,
    downloadCount,
    chapterCount = 0,
    downloadedChapterCount = 0,
    queuedChapterCount = 0,
    sourceName,
    mode,
}: {
    inLibraryIndicator?: boolean;
    addToServerLibrary: () => void;
    updateLibraryState: () => void;
    isInLibrary: boolean;
    isInServerLibrary: boolean;
    unread?: number;
    downloadCount?: number;
    chapterCount?: number;
    downloadedChapterCount?: number;
    queuedChapterCount?: number;
    sourceName?: string;
    mode: MangaCardMode;
}) => {
    const { t } = useLingui();

    const isTouchDevice = MediaQuery.useIsTouchDevice();

    const {
        settings: { showUnreadBadge, showDownloadBadge },
    } = useMetadataServerSettings();

    const isServerLibraryEntry = mode === 'server-library';
    const showsLibraryState = inLibraryIndicator && !isServerLibraryEntry;

    const addToPersonalLibrary = (e: MouseEvent) => {
        e.preventDefault();
        e.stopPropagation();
        updateLibraryState();
    };

    return (
        <BadgeContainer>
            {isServerLibraryEntry && !!sourceName && (
                <CustomTooltip title={sourceName}>
                    <Typography
                        sx={{
                            // the name of a source has to be allowed to shrink, otherwise it pushes the badges out of the card
                            maxWidth: '100%',
                            minWidth: 0,
                            overflow: 'hidden',
                            textOverflow: 'ellipsis',
                            whiteSpace: 'nowrap',
                            backgroundColor: 'primary.dark',
                            color: 'primary.contrastText',
                            p: 0.3,
                        }}
                    >
                        {sourceName}
                    </Typography>
                </CustomTooltip>
            )}
            {/*
             * Browsing a source offers the shared collection of the server, the personal bookshelf is only
             * reachable from the server library. The indicator therefore describes `inServerLibrary`, not the
             * collection of the current user.
             */}
            {!isTouchDevice && inLibraryIndicator && mode === 'source' && !isInServerLibrary && (
                <Button
                    className="source-manga-library-state-button"
                    component="div"
                    variant="contained"
                    size="small"
                    {...MUIUtil.preventRippleProp()}
                    onClick={(e) => {
                        e.preventDefault();
                        e.stopPropagation();
                        addToServerLibrary();
                    }}
                    sx={{
                        display: 'none',
                    }}
                    color="primary"
                >
                    {t`Add to server library`}
                </Button>
            )}
            {/*
             * Every entry of the server library is part of the shared collection, an indicator for it would be
             * shown on every card of the page. The title of the entry is already known to be in it.
             */}
            {showsLibraryState && isInServerLibrary && (
                <Typography
                    className="source-manga-library-state-indicator"
                    sx={{
                        ...NON_SHRINKING_BADGE_SX,
                        backgroundColor: 'primary.dark',
                        color: 'primary.contrastText',
                        p: 0.3,
                    }}
                >
                    {t`In server library`}
                </Typography>
            )}
            {inLibraryIndicator && isInLibrary && (
                <Typography
                    className="source-manga-personal-library-state-indicator"
                    sx={{
                        ...NON_SHRINKING_BADGE_SX,
                        backgroundColor: 'secondary.dark',
                        color: 'secondary.contrastText',
                        p: 0.3,
                    }}
                >
                    {t`In my library`}
                </Typography>
            )}
            {((showUnreadBadge && mode === 'default') || mode === 'duplicate') && (unread ?? 0) > 0 && (
                <Badge sx={{ backgroundColor: 'primary.main', color: 'primary.contrastText' }}>{unread}</Badge>
            )}
            {((showDownloadBadge && mode === 'default') || mode === 'duplicate') && (downloadCount ?? 0) > 0 && (
                <Badge
                    sx={{
                        backgroundColor: 'secondary.main',
                        color: 'secondary.contrastText',
                    }}
                >
                    {downloadCount}
                </Badge>
            )}
            {isServerLibraryEntry && (
                <ServerLibraryDownloadBadges
                    chapterCount={chapterCount}
                    downloadedChapterCount={downloadedChapterCount}
                    queuedChapterCount={queuedChapterCount}
                />
            )}
            {/*
             * Adding a title to the personal library is the only action of the server library which is offered by a
             * card. Fine pointers get it while hovering the card, touch devices do not know hovering and get a
             * button of their own. Removing the title from the personal library is part of the chapter page.
             */}
            {isServerLibraryEntry && inLibraryIndicator && !isInLibrary && (
                <CustomTooltip title={t`Add to my bookshelf`} disableHoverListener={!isTouchDevice}>
                    <Button
                        className="source-manga-library-state-button"
                        component="div"
                        variant="contained"
                        size="small"
                        {...MUIUtil.preventRippleProp()}
                        onClick={addToPersonalLibrary}
                        sx={{ ...NON_SHRINKING_BADGE_SX, ...(isTouchDevice ? TOUCH_BUTTON_SX : { display: 'none' }) }}
                        color="secondary"
                    >
                        {isTouchDevice ? <BookmarkAddOutlined fontSize="small" /> : t`Add to my bookshelf`}
                    </Button>
                </CustomTooltip>
            )}
        </BadgeContainer>
    );
};
