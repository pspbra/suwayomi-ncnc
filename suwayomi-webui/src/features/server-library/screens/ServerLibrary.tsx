/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

import { useCallback, useMemo, useRef, useState } from 'react';
import Alert from '@mui/material/Alert';
import Box from '@mui/material/Box';
import Button from '@mui/material/Button';
import type { ChipProps } from '@mui/material/Chip';
import Chip from '@mui/material/Chip';
import CircularProgress from '@mui/material/CircularProgress';
import IconButton from '@mui/material/IconButton';
import { styled, useTheme } from '@mui/material/styles';
import InfoOutlinedIcon from '@mui/icons-material/InfoOutlined';
import RefreshIcon from '@mui/icons-material/Refresh';
import { Link } from 'react-router-dom';
import { StringParam, useQueryParam } from 'use-query-params';
import { useLingui } from '@lingui/react/macro';
import { AppRoutes } from '@/base/AppRoute.constants.ts';
import { STABLE_EMPTY_ARRAY } from '@/base/Base.constants.ts';
import { SearchParam } from '@/base/Base.types.ts';
import { AppbarSearch } from '@/base/components/AppbarSearch.tsx';
import { CustomTooltip } from '@/base/components/CustomTooltip.tsx';
import { EmptyViewAbsoluteCentered } from '@/base/components/feedback/EmptyViewAbsoluteCentered.tsx';
import { BaseMangaGrid } from '@/features/manga/components/BaseMangaGrid.tsx';
import { useAppAction } from '@/features/navigation-bar/hooks/useAppAction.ts';
import { useAppTitle } from '@/features/navigation-bar/hooks/useAppTitle.ts';
import { ServerLibraryToolbarMenu } from '@/features/server-library/components/ServerLibraryToolbarMenu.tsx';
import { useMetadataServerSettings } from '@/features/settings/services/ServerSettingsMetadata.ts';
import { defaultPromiseErrorHandler } from '@/lib/DefaultPromiseErrorHandler.ts';
import { getErrorMessage } from '@/lib/HelperFunctions.ts';
import { MUIUtil } from '@/lib/mui/MUI.util.ts';
import { requestManager } from '@/lib/requests/RequestManager.ts';

/** Big enough to fill the screen of a desktop, small enough to keep the first request short. */
const PAGE_SIZE = 48;
/** Titles and the shared download state of other users change in the background, the list follows along. */
const POLL_INTERVAL = 15000;

const StyledGridWrapper = styled(Box)({
    minHeight: '100%',
    position: 'relative',
});

const TitleWithSizeTag = styled('span')({
    display: 'flex',
    alignItems: 'center',
});

const TitleSizeTag = ({ sx, ...props }: ChipProps) => (
    <Chip {...props} size="small" sx={MUIUtil.mergeSx(sx, { marginLeft: '5px' })} />
);

/**
 * The shared collection of the server.
 *
 * The page is laid out like the personal library: the titles are rendered as manga cards of a virtualized grid
 * which follows the grid settings, the search and the tasks are part of the app bar. The cards only show data which
 * is shared by all users - the personal library state is only used to offer adding a title to the own bookshelf.
 */
export const ServerLibrary = () => {
    const { t } = useLingui();
    const theme = useTheme();

    const {
        settings: { gridLayout, showTabSize },
    } = useMetadataServerSettings();

    const [query] = useQueryParam(SearchParam.QUERY, StringParam);
    const search = query ?? '';

    // The catalogue of the server is not filtered by the current user, thus the entries have to be read page by page.
    const { data, loading, error, refetch, fetchMore } = requestManager.useGetServerLibrary({
        variables: { offset: 0, limit: PAGE_SIZE, search },
        pollInterval: POLL_INTERVAL,
        skipPollAttempt: () => document.hidden,
        fetchPolicy: 'cache-and-network',
    });

    const serverLibrary = data?.serverLibrary;
    const entries = serverLibrary?.items ?? STABLE_EMPTY_ARRAY;
    const totalCount = serverLibrary?.totalCount ?? 0;
    const hasNextPage = !!serverLibrary?.hasNextPage;

    const isLoadingMoreRef = useRef(false);
    const [isLoadingMore, setIsLoadingMore] = useState(false);

    /*
     * The grid knows nothing but manga, the numbers of the shared downloads are part of the entries of the server
     * library and are handed to the cards along with them.
     */
    const mangas = useMemo(
        () =>
            entries.map(({ manga, chapterCount, downloadedChapterCount, queuedChapterCount }) => ({
                ...manga,
                chapterCount,
                downloadedChapterCount,
                queuedChapterCount,
            })),
        [entries],
    );
    const suggestions = useMemo(() => mangas.map((manga) => manga.title), [mangas]);

    const refresh = useCallback(() => refetch().catch(defaultPromiseErrorHandler('ServerLibrary::refetch')), [refetch]);

    const loadMore = useCallback(() => {
        /*
         * The grid asks for more entries whenever its viewport is not filled. A request which is still running must
         * not be started a second time - the answer of the server arrives before the entries of the list are
         * updated, which would request the same page again.
         */
        if (!hasNextPage || isLoadingMoreRef.current) {
            return;
        }

        isLoadingMoreRef.current = true;
        setIsLoadingMore(true);

        fetchMore({ variables: { offset: mangas.length } })
            .catch(defaultPromiseErrorHandler('ServerLibrary::loadMore'))
            .finally(() => {
                isLoadingMoreRef.current = false;
                setIsLoadingMore(false);
            });
    }, [hasNextPage, mangas.length, fetchMore]);

    useAppTitle(
        <TitleWithSizeTag>
            {t`Server library`}
            {showTabSize && (
                <TitleSizeTag
                    sx={theme.applyStyles('light', { backgroundColor: 'background.paper' })}
                    label={totalCount}
                />
            )}
        </TitleWithSizeTag>,
        t`Server library`,
        [t, showTabSize, totalCount, theme],
    );

    useAppAction(
        <>
            <AppbarSearch searchHistoryKey="server-library" suggestions={suggestions} />
            <CustomTooltip title={t`Refresh`}>
                <IconButton onClick={refresh} disabled={loading} color="inherit">
                    {loading ? <CircularProgress size={16} color="inherit" /> : <RefreshIcon />}
                </IconButton>
            </CustomTooltip>
            <ServerLibraryToolbarMenu />
            <CustomTooltip
                title={t`Titles of the server library are shared by all users. Add titles from the sources here and add them to your own bookshelf afterwards. Downloads are managed here as well.`}
            >
                <IconButton color="inherit">
                    <InfoOutlinedIcon />
                </IconButton>
            </CustomTooltip>
        </>,
        [t, loading, refresh, suggestions],
    );

    if (error && !serverLibrary) {
        return (
            <EmptyViewAbsoluteCentered
                message={t`Unable to load data`}
                messageExtra={getErrorMessage(error)}
                retry={refresh}
            />
        );
    }

    return (
        <StyledGridWrapper>
            {/*
             * An error which leaves the already loaded entries alone is not shown as a whole page - the shared state
             * of the server is polled, which usually recovers on its own.
             */}
            {error && serverLibrary && (
                <Alert severity="error" sx={{ m: 1 }}>
                    {getErrorMessage(error)}
                </Alert>
            )}
            <BaseMangaGrid
                mangas={mangas}
                gridLayout={gridLayout}
                mode="server-library"
                inLibraryIndicator
                hasNextPage={hasNextPage}
                loadMore={loadMore}
                isLoading={!serverLibrary ? loading : isLoadingMore}
                message={
                    search ? t`No matching titles.` : t`The server library is empty. Browse the sources to add titles.`
                }
                messageExtra={
                    search ? undefined : (
                        <Button component={Link} to={AppRoutes.browse.path()}>
                            {t`Browse sources`}
                        </Button>
                    )
                }
                retry={error ? refresh : undefined}
            />
        </StyledGridWrapper>
    );
};
