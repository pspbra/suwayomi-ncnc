/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

import { useEffect, useState } from 'react';
import Button from '@mui/material/Button';
import Dialog from '@mui/material/Dialog';
import DialogActions from '@mui/material/DialogActions';
import DialogContent from '@mui/material/DialogContent';
import DialogTitle from '@mui/material/DialogTitle';
import IconButton from '@mui/material/IconButton';
import ListItemIcon from '@mui/material/ListItemIcon';
import ListItemText from '@mui/material/ListItemText';
import Menu from '@mui/material/Menu';
import MenuItem from '@mui/material/MenuItem';
import CloudSyncIcon from '@mui/icons-material/CloudSync';
import DownloadIcon from '@mui/icons-material/Download';
import MoreVertIcon from '@mui/icons-material/MoreVert';
import StorageIcon from '@mui/icons-material/Storage';
import { Link } from 'react-router-dom';
import { useLingui } from '@lingui/react/macro';
import { AppRoutes } from '@/base/AppRoute.constants.ts';
import { CustomTooltip } from '@/base/components/CustomTooltip.tsx';
import { makeToast } from '@/base/utils/Toast.ts';
import { ServerLibraryStorage } from '@/features/server-library/components/ServerLibraryStorage.tsx';
import { useServerLibraryAction } from '@/features/server-library/ServerLibrary.hooks.ts';
import { useServerLibraryPermissions } from '@/features/server-library/ServerLibrary.permissions.tsx';
import { GET_SERVER_LIBRARY } from '@/lib/graphql/server-library/ServerLibraryQuery.ts';
import { UPDATE_SERVER_LIBRARY } from '@/lib/graphql/server-library/ServerLibraryMutation.ts';

/**
 * The tasks which affect the whole server.
 *
 * They are admin only, the server rejects them for every other account. The storage statistics are only read when
 * their dialog is opened: measuring them walks the whole download directory, which must not happen in the
 * background.
 */
export const ServerLibraryToolbarMenu = () => {
    const { t } = useLingui();

    const { isAdmin } = useServerLibraryPermissions();
    const { busy, error, refreshError, run } = useServerLibraryAction();

    const [anchorEl, setAnchorEl] = useState<HTMLElement | null>(null);
    const [isStorageDialogOpen, setIsStorageDialogOpen] = useState(false);

    useEffect(() => {
        if (error) {
            makeToast(t`Could not update the server library`, 'error', error);
        }
    }, [t, error]);

    useEffect(() => {
        if (refreshError) {
            makeToast(
                t`The action was applied, but the displayed data could not be refreshed.`,
                'warning',
                refreshError,
            );
        }
    }, [t, refreshError]);

    if (!isAdmin) {
        return null;
    }

    const closeMenu = () => setAnchorEl(null);

    return (
        <>
            <CustomTooltip title={t`Server library tasks`}>
                <IconButton onClick={(e) => setAnchorEl(e.currentTarget)} color="inherit">
                    <MoreVertIcon />
                </IconButton>
            </CustomTooltip>
            <Menu anchorEl={anchorEl} open={!!anchorEl} onClose={closeMenu}>
                <MenuItem
                    disabled={busy}
                    onClick={() => {
                        closeMenu();
                        void run(UPDATE_SERVER_LIBRARY, {}, { refetch: [GET_SERVER_LIBRARY] });
                    }}
                >
                    <ListItemIcon>
                        <CloudSyncIcon fontSize="small" />
                    </ListItemIcon>
                    <ListItemText>{t`Update server library`}</ListItemText>
                </MenuItem>
                <MenuItem component={Link} to={AppRoutes.downloads.path} onClick={closeMenu}>
                    <ListItemIcon>
                        <DownloadIcon fontSize="small" />
                    </ListItemIcon>
                    <ListItemText>{t`Global download queue`}</ListItemText>
                </MenuItem>
                <MenuItem
                    onClick={() => {
                        closeMenu();
                        setIsStorageDialogOpen(true);
                    }}
                >
                    <ListItemIcon>
                        <StorageIcon fontSize="small" />
                    </ListItemIcon>
                    <ListItemText>{t`Server storage`}</ListItemText>
                </MenuItem>
            </Menu>
            {/* the content of the dialog is only mounted - and thus requested - while it is open */}
            <Dialog open={isStorageDialogOpen} onClose={() => setIsStorageDialogOpen(false)} fullWidth maxWidth="sm">
                <DialogTitle>{t`Server storage`}</DialogTitle>
                <DialogContent>{isStorageDialogOpen && <ServerLibraryStorage />}</DialogContent>
                <DialogActions>
                    <Button onClick={() => setIsStorageDialogOpen(false)}>{t`Close`}</Button>
                </DialogActions>
            </Dialog>
        </>
    );
};
