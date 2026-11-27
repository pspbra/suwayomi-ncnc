/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

import { useCallback, useEffect, useMemo, useState } from 'react';
import Alert from '@mui/material/Alert';
import Box from '@mui/material/Box';
import Button from '@mui/material/Button';
import Card from '@mui/material/Card';
import Checkbox from '@mui/material/Checkbox';
import Chip from '@mui/material/Chip';
import CircularProgress from '@mui/material/CircularProgress';
import Dialog from '@mui/material/Dialog';
import DialogActions from '@mui/material/DialogActions';
import DialogContent from '@mui/material/DialogContent';
import DialogContentText from '@mui/material/DialogContentText';
import DialogTitle from '@mui/material/DialogTitle';
import FormControlLabel from '@mui/material/FormControlLabel';
import IconButton from '@mui/material/IconButton';
import ListItemIcon from '@mui/material/ListItemIcon';
import ListItemText from '@mui/material/ListItemText';
import Menu from '@mui/material/Menu';
import MenuItem from '@mui/material/MenuItem';
import Stack from '@mui/material/Stack';
import Typography from '@mui/material/Typography';
import DeleteIcon from '@mui/icons-material/Delete';
import DownloadIcon from '@mui/icons-material/Download';
import MenuBookIcon from '@mui/icons-material/MenuBook';
import MoreVertIcon from '@mui/icons-material/MoreVert';
import RefreshIcon from '@mui/icons-material/Refresh';
import { Link, useParams } from 'react-router-dom';
import { useLingui } from '@lingui/react/macro';
import { plural } from '@lingui/core/macro';
import { AppRoutes } from '@/base/AppRoute.constants.ts';
import { STABLE_EMPTY_ARRAY } from '@/base/Base.constants.ts';
import { SelectableCollectionSelectAll } from '@/base/collection/components/SelectableCollectionSelectAll.tsx';
import { CustomTooltip } from '@/base/components/CustomTooltip.tsx';
import { EmptyViewAbsoluteCentered } from '@/base/components/feedback/EmptyViewAbsoluteCentered.tsx';
import { LoadingPlaceholder } from '@/base/components/feedback/LoadingPlaceholder.tsx';
import { ListCardContent } from '@/base/components/lists/cards/ListCardContent.tsx';
import { SpinnerImage } from '@/base/components/SpinnerImage.tsx';
import { makeToast } from '@/base/utils/Toast.ts';
import { MANGA_COVER_ASPECT_RATIO } from '@/features/manga/Manga.constants.ts';
import { Mangas } from '@/features/manga/services/Mangas.ts';
import { useAppTitleAndAction } from '@/features/navigation-bar/hooks/useAppTitleAndAction.ts';
import { ServerLibraryPersonalStateButton } from '@/features/server-library/components/ServerLibraryPersonalStateButton.tsx';
import { useServerLibraryAction } from '@/features/server-library/ServerLibrary.hooks.ts';
import { useServerLibraryPermissions } from '@/features/server-library/ServerLibrary.permissions.tsx';
import { VirtuosoPersisted } from '@/lib/virtuoso/Component/VirtuosoPersisted.tsx';
import { defaultPromiseErrorHandler } from '@/lib/DefaultPromiseErrorHandler.ts';
import { getErrorMessage } from '@/lib/HelperFunctions.ts';
import { GET_SERVER_LIBRARY, GET_SERVER_LIBRARY_MANGA } from '@/lib/graphql/server-library/ServerLibraryQuery.ts';
import type { ServerLibraryChapterFieldsFragment } from '@/lib/graphql/server-library/ServerLibraryQuery.ts';
import {
    DELETE_SERVER_LIBRARY_DOWNLOADS,
    ENQUEUE_SERVER_LIBRARY_DOWNLOADS,
    REMOVE_SERVER_LIBRARY_MANGA,
} from '@/lib/graphql/server-library/ServerLibraryMutation.ts';
import { requestManager } from '@/lib/requests/RequestManager.ts';

const POLL_INTERVAL = 15000;

const REFETCH_AFTER_CHAPTER_CHANGE = [GET_SERVER_LIBRARY_MANGA, GET_SERVER_LIBRARY];

type ChapterActionDialog = 'remove' | 'delete-downloads';

/**
 * A chapter of a title of the server library.
 *
 * Only the shared state of the chapter is shown, the reading state belongs to the personal library.
 */
const ServerLibraryChapterCard = ({
    chapter,
    isSelected,
    isDisabled,
    handleSelection,
}: {
    chapter: ServerLibraryChapterFieldsFragment;
    isSelected: boolean;
    isDisabled: boolean;
    handleSelection: (chapterId: number, selected: boolean) => void;
}) => {
    const { t } = useLingui();

    return (
        <Card sx={{ m: 0.5 }}>
            <ListCardContent>
                <Checkbox
                    checked={isSelected}
                    disabled={isDisabled}
                    slotProps={{ input: { 'aria-label': chapter.name } }}
                    onChange={(_event, checked) => handleSelection(chapter.id, checked)}
                />
                <Typography sx={{ flexGrow: 1, minWidth: 0, wordBreak: 'break-word' }}>{chapter.name}</Typography>
                <Chip
                    size="small"
                    variant={chapter.isDownloaded ? 'filled' : 'outlined'}
                    color={chapter.isDownloaded ? 'success' : 'default'}
                    label={chapter.isDownloaded ? t`Downloaded` : t`Not downloaded`}
                />
            </ListCardContent>
        </Card>
    );
};

/**
 * Chapter and download management of a single server library title.
 *
 * Everything offered here affects every user of the server, which is why the actions are gated by the
 * DOWNLOAD_CHAPTERS and MANAGE_SERVER_LIBRARY permissions. Titles which were removed from the server library stay
 * reachable to clean up retained downloads, they only lose the download and removal actions.
 */
export const ServerLibraryMangaDetails = () => {
    const { t } = useLingui();
    const { mangaId } = useParams();
    const id = Number(mangaId);
    const isValidId = Number.isSafeInteger(id) && id > 0;

    const [selectedChapterIds, setSelectedChapterIds] = useState<number[]>([]);
    const [dialog, setDialog] = useState<ChapterActionDialog | null>(null);
    const [deleteDownloads, setDeleteDownloads] = useState(false);
    const [menuAnchorEl, setMenuAnchorEl] = useState<HTMLElement | null>(null);

    const { isAdmin, canDownload, canManage } = useServerLibraryPermissions();
    const action = useServerLibraryAction();

    const { data, loading, error, refetch } = requestManager.useGetServerLibraryManga({
        variables: { mangaId: id },
        skip: !isValidId,
        pollInterval: POLL_INTERVAL,
        skipPollAttempt: () => document.hidden,
        fetchPolicy: 'cache-and-network',
    });

    const manga = data?.manga;
    const chapters = data?.chapters.nodes ?? STABLE_EMPTY_ARRAY;
    const totalCount = data?.chapters.totalCount ?? 0;

    useEffect(() => {
        setSelectedChapterIds([]);
    }, [id]);

    useEffect(() => {
        if (action.error) {
            makeToast(t`Could not apply the action`, 'error', action.error);
        }
    }, [t, action.error]);

    useEffect(() => {
        if (action.refreshError) {
            makeToast(
                t`The action was applied, but the displayed data could not be refreshed.`,
                'warning',
                action.refreshError,
            );
        }
    }, [t, action.refreshError]);

    const refresh = useCallback(
        () => refetch().catch(defaultPromiseErrorHandler('ServerLibraryManga::refetch')),
        [refetch],
    );

    const selectedChapters = useMemo(
        () => chapters.filter((chapter) => selectedChapterIds.includes(chapter.id)),
        [chapters, selectedChapterIds],
    );
    const selectedNotDownloadedIds = useMemo(
        () => selectedChapters.filter((chapter) => !chapter.isDownloaded).map((chapter) => chapter.id),
        [selectedChapters],
    );
    const selectedDownloadedIds = useMemo(
        () => selectedChapters.filter((chapter) => chapter.isDownloaded).map((chapter) => chapter.id),
        [selectedChapters],
    );
    const downloadedChapterCount = useMemo(() => chapters.filter((chapter) => chapter.isDownloaded).length, [chapters]);

    const isRemovalAllowed = canManage && !!manga?.inServerLibrary;

    const handleChapterSelection = useCallback(
        (chapterId: number, selected: boolean) =>
            setSelectedChapterIds((previous) =>
                selected ? [...previous, chapterId] : previous.filter((idToFilter) => idToFilter !== chapterId),
            ),
        [],
    );

    useAppTitleAndAction(
        manga?.title ?? t`Server library`,
        <>
            <CustomTooltip title={t`Refresh`}>
                <IconButton onClick={refresh} disabled={loading} color="inherit">
                    {loading && !manga ? <CircularProgress size={16} color="inherit" /> : <RefreshIcon />}
                </IconButton>
            </CustomTooltip>
            <CustomTooltip title={t`Server library tasks`}>
                <IconButton onClick={(e) => setMenuAnchorEl(e.currentTarget)} color="inherit">
                    <MoreVertIcon />
                </IconButton>
            </CustomTooltip>
            <Menu anchorEl={menuAnchorEl} open={!!menuAnchorEl} onClose={() => setMenuAnchorEl(null)}>
                <MenuItem component={Link} to={AppRoutes.manga.path(id)}>
                    <ListItemIcon>
                        <MenuBookIcon fontSize="small" />
                    </ListItemIcon>
                    <ListItemText>{t`Read and view details`}</ListItemText>
                </MenuItem>
                {isAdmin && (
                    <MenuItem component={Link} to={AppRoutes.downloads.path}>
                        <ListItemIcon>
                            <DownloadIcon fontSize="small" />
                        </ListItemIcon>
                        <ListItemText>{t`Global download queue`}</ListItemText>
                    </MenuItem>
                )}
                {isRemovalAllowed && (
                    <MenuItem
                        disabled={action.busy}
                        onClick={() => {
                            setMenuAnchorEl(null);
                            setDeleteDownloads(false);
                            setDialog('remove');
                        }}
                    >
                        <ListItemIcon>
                            <DeleteIcon fontSize="small" color="error" />
                        </ListItemIcon>
                        <ListItemText>{t`Remove server title`}</ListItemText>
                    </MenuItem>
                )}
            </Menu>
        </>,
        [t, id, loading, manga, refresh, isAdmin, isRemovalAllowed, action.busy],
    );

    if (!isValidId) {
        return <EmptyViewAbsoluteCentered message={t`Invalid manga ID`} />;
    }

    if (error && !manga) {
        return (
            <EmptyViewAbsoluteCentered
                message={t`Unable to load data`}
                messageExtra={getErrorMessage(error)}
                retry={refresh}
            />
        );
    }

    if (!manga) {
        return <LoadingPlaceholder />;
    }

    return (
        <Box sx={{ p: 2, maxWidth: 1200, mx: 'auto' }}>
            <Stack spacing={2}>
                <Stack direction="row" spacing={2}>
                    <Box sx={{ width: 100, flexShrink: 0 }}>
                        <SpinnerImage
                            alt={manga.title}
                            src={Mangas.getThumbnailUrl(manga)}
                            imgStyle={{
                                width: '100%',
                                aspectRatio: MANGA_COVER_ASPECT_RATIO,
                                objectFit: 'cover',
                                borderRadius: '4px',
                            }}
                        />
                    </Box>
                    <Stack spacing={1} sx={{ minWidth: 0, flexGrow: 1 }}>
                        <Typography variant="h5" component="h1">
                            {manga.title}
                        </Typography>
                        <Typography variant="body2" color="textSecondary">
                            {manga.source?.displayName ?? manga.sourceId}
                        </Typography>
                        <Typography variant="body2">
                            {t`Chapters`}: {totalCount} · {t`Downloaded`}: {downloadedChapterCount}
                        </Typography>
                        {!totalCount && (
                            <Typography variant="caption" color="textSecondary">
                                {t`Metadata and chapters may still be updating.`}
                            </Typography>
                        )}
                        <Stack direction="row" sx={{ flexWrap: 'wrap', gap: 1 }}>
                            <ServerLibraryPersonalStateButton manga={manga} size="small" variant="outlined" />
                            <Button component={Link} to={AppRoutes.manga.path(id)} size="small">
                                {t`Read and view details`}
                            </Button>
                        </Stack>
                    </Stack>
                </Stack>

                {!manga.inServerLibrary && (
                    <Alert severity="warning">
                        {t`This title was removed from the server library. Personal bookmarks and the reading history are kept, retained downloads can still be deleted here.`}
                    </Alert>
                )}

                {/*
                 * The shared state of the server is polled, an error which leaves the already loaded chapters alone
                 * is therefore not shown as a whole page.
                 */}
                {error && <Alert severity="error">{getErrorMessage(error)}</Alert>}

                {!loading && !chapters.length && (
                    <Alert severity="info">
                        {t`No cached chapters yet. The metadata update may still be running.`}
                    </Alert>
                )}

                <Stack
                    direction="row"
                    sx={{ alignItems: 'center', justifyContent: 'space-between', flexWrap: 'wrap', gap: 1 }}
                >
                    <Typography variant="h5" component="h2">
                        {plural(totalCount, { one: '# chapter', other: '# chapters' })}
                    </Typography>
                    <Stack direction="row" sx={{ alignItems: 'center', flexWrap: 'wrap', gap: 0.5 }}>
                        {!!chapters.length && (
                            <SelectableCollectionSelectAll
                                areAllItemsSelected={selectedChapterIds.length === chapters.length}
                                areNoItemsSelected={!selectedChapterIds.length}
                                onChange={(checked) =>
                                    setSelectedChapterIds(checked ? chapters.map((chapter) => chapter.id) : [])
                                }
                            />
                        )}
                        {canDownload && manga.inServerLibrary && (
                            <Button
                                disabled={action.busy || !selectedNotDownloadedIds.length}
                                onClick={() => {
                                    void action.run(
                                        ENQUEUE_SERVER_LIBRARY_DOWNLOADS,
                                        { chapterIds: selectedNotDownloadedIds },
                                        {
                                            refetch: REFETCH_AFTER_CHAPTER_CHANGE,
                                            onSuccess: () => setSelectedChapterIds([]),
                                        },
                                    );
                                }}
                            >
                                {t`Download selected chapters`}
                            </Button>
                        )}
                        {canManage && (
                            <Button
                                color="error"
                                disabled={action.busy || !selectedDownloadedIds.length}
                                onClick={() => setDialog('delete-downloads')}
                            >
                                {t`Delete selected downloads`}
                            </Button>
                        )}
                    </Stack>
                </Stack>

                {action.busy && <CircularProgress size={24} />}

                <VirtuosoPersisted
                    persistKey={`server-library-${id}-chapters`}
                    useWindowScroll
                    increaseViewportBy={window.innerHeight * 0.5}
                    totalCount={chapters.length}
                    computeItemKey={(index) => chapters[index].id}
                    itemContent={(index) => (
                        <ServerLibraryChapterCard
                            chapter={chapters[index]}
                            isSelected={selectedChapterIds.includes(chapters[index].id)}
                            isDisabled={action.busy}
                            handleSelection={handleChapterSelection}
                        />
                    )}
                />
            </Stack>

            <Dialog
                open={dialog !== null}
                onClose={() => {
                    if (!action.busy) {
                        setDialog(null);
                    }
                }}
            >
                <DialogTitle>{dialog === 'remove' ? t`Remove server title` : t`Delete shared downloads`}</DialogTitle>
                <DialogContent>
                    <DialogContentText>
                        {dialog === 'remove'
                            ? t`Pending downloads of this title get cancelled. Personal bookshelves, categories and the reading history are kept.`
                            : plural(selectedDownloadedIds.length, {
                                  one: '# downloaded file will be deleted for every user.',
                                  other: '# downloaded files will be deleted for every user.',
                              })}
                    </DialogContentText>
                    {dialog === 'remove' && (
                        <FormControlLabel
                            label={t`Also delete the downloaded files (shared by all users)`}
                            control={
                                <Checkbox
                                    checked={deleteDownloads}
                                    onChange={(_event, checked) => setDeleteDownloads(checked)}
                                />
                            }
                        />
                    )}
                </DialogContent>
                <DialogActions>
                    <Button disabled={action.busy} onClick={() => setDialog(null)}>
                        {t`Cancel`}
                    </Button>
                    <Button
                        color="error"
                        disabled={action.busy}
                        onClick={() => {
                            const onSuccess = () => {
                                setDialog(null);
                                setSelectedChapterIds([]);
                            };

                            void action.run(
                                dialog === 'remove' ? REMOVE_SERVER_LIBRARY_MANGA : DELETE_SERVER_LIBRARY_DOWNLOADS,
                                dialog === 'remove'
                                    ? { mangaId: id, deleteDownloads }
                                    : { chapterIds: selectedDownloadedIds },
                                { refetch: REFETCH_AFTER_CHAPTER_CHANGE, onSuccess },
                            );
                        }}
                    >
                        {t`Confirm`}
                    </Button>
                </DialogActions>
            </Dialog>
        </Box>
    );
};
