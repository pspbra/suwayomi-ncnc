/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

import type { ReactNode } from 'react';
import Alert from '@mui/material/Alert';
import { useLingui } from '@lingui/react/macro';
import { requestManager } from '@/lib/requests/RequestManager.ts';
import { UserPermission, UserRole } from '@/lib/graphql/generated/graphql-base.types.ts';
import type { UserFieldsFragment } from '@/lib/graphql/generated/graphql.ts';

/** The `ADMIN` role bypasses every permission check of the server. */
export const isAdminUser = (user: UserFieldsFragment): boolean => user.roles.includes(UserRole.Admin);

export const canManageServerLibrary = (user: UserFieldsFragment): boolean =>
    isAdminUser(user) || user.permissions.includes(UserPermission.ManageServerLibrary);

export const canDownloadChapters = (user: UserFieldsFragment): boolean =>
    isAdminUser(user) || user.permissions.includes(UserPermission.DownloadChapters);

/**
 * Permissions which decide which server library actions are offered.
 *
 * `isUserLoaded` distinguishes "the account has no permission" from "the account is not known yet". Nothing may
 * be hidden permanently just because the current user query has not answered yet.
 */
export const useServerLibraryPermissions = () => {
    const { data, loading } = requestManager.useGetCurrentUser();
    const user = data?.user;
    const isAdmin = !!user && isAdminUser(user);

    return {
        isUserLoaded: !!user,
        isLoading: loading,
        isAdmin,
        canDownload: !!user && canDownloadChapters(user),
        canManage: !!user && canManageServerLibrary(user),
    };
};

/**
 * Admin-only areas such as the global download queue, the global update status and the server storage
 * statistics. The server rejects these requests for every other account.
 */
export const useIsServerAdmin = (): boolean => {
    const { data } = requestManager.useGetCurrentUser();
    return !!data?.user && isAdminUser(data.user);
};

export const ServerAdminGuard = ({ children }: { children: ReactNode }) => {
    const { t } = useLingui();
    const { isUserLoaded, isAdmin } = useServerLibraryPermissions();

    if (!isUserLoaded) {
        return null;
    }

    if (!isAdmin) {
        return <Alert severity="info">{t`Only administrators can access global server tasks.`}</Alert>;
    }

    return children;
};
