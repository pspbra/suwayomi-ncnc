/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

import { useEffect } from 'react';
import { AppInitializer } from '@/base/AppInitializer.ts';
import { useServerLibraryPermissions } from '@/features/server-library/ServerLibrary.permissions.tsx';

/**
 * Loads and releases the admin-only global server state.
 *
 * The global download queue, the global update status and the server storage statistics are rejected for every
 * account without the `ADMIN` role. They are therefore requested only once the current account is known and may
 * not stay subscribed or cached after the account changed. This is mounted inside the routes which require
 * authentication, so it also unmounts when a session ends.
 */
export const AdminSessionGate = () => {
    const { isUserLoaded, isAdmin } = useServerLibraryPermissions();

    useEffect(() => {
        if (!isUserLoaded) {
            return undefined;
        }

        if (isAdmin) {
            AppInitializer.startAdminData();
            return () => AppInitializer.stopAdminData();
        }

        // Drop data of a previous admin session before the new account uses the application.
        AppInitializer.stopAdminData();

        return undefined;
    }, [isUserLoaded, isAdmin]);

    return null;
};
