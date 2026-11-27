/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

import type { MessageDescriptor } from '@lingui/core';
import { msg } from '@lingui/core/macro';
import {
    UserCodePurpose,
    UserPermission,
    UserRole,
} from '@/lib/graphql/generated/graphql-base.types.ts';

/**
 * The built-in admin account. It mirrors the server auth settings and can neither be
 * modified nor used as a recovery code target.
 */
export const BUILT_IN_ADMIN_USER_ID = 1;

export const USER_ROLE_TO_TRANSLATION: Record<UserRole, MessageDescriptor> = {
    [UserRole.Admin]: msg`Admin`,
    [UserRole.User]: msg`User`,
    [UserRole.Visitor]: msg`Visitor`,
};

/** Roles an admin may grant. `VISITOR` is not assignable. */
export const ASSIGNABLE_USER_ROLES = [UserRole.User, UserRole.Admin] as const;

export const USER_PERMISSION_TO_TRANSLATION: Record<UserPermission, MessageDescriptor> = {
    [UserPermission.InstallExtensions]: msg`Install extensions`,
    [UserPermission.InstallExternalExtensions]: msg`Install external extensions`,
    [UserPermission.UninstallExtensions]: msg`Uninstall extensions`,
    [UserPermission.DownloadChapters]: msg`Download chapters`,
    [UserPermission.AccessNsfw]: msg`Access NSFW sources`,
    [UserPermission.ManageSettings]: msg`Manage server settings`,
    [UserPermission.ManageUsers]: msg`Manage users`,
    [UserPermission.ManageExtensionStores]: msg`Manage extension stores`,
    [UserPermission.ManageSourcePreferences]: msg`Manage source preferences`,
    [UserPermission.ManageServerLibrary]: msg`Manage server library`,
    [UserPermission.ManageCache]: msg`Manage cache`,
};

export const USER_PERMISSIONS = Object.keys(USER_PERMISSION_TO_TRANSLATION) as UserPermission[];

/** The permission set the server grants to new accounts when none is specified. */
export const DEFAULT_USER_PERMISSIONS = [
    UserPermission.InstallExtensions,
    UserPermission.InstallExternalExtensions,
    UserPermission.UninstallExtensions,
    UserPermission.DownloadChapters,
] as const satisfies UserPermission[];

export const USER_CODE_PURPOSE_TO_TRANSLATION: Record<UserCodePurpose, MessageDescriptor> = {
    [UserCodePurpose.Recovery]: msg`Password recovery`,
    [UserCodePurpose.Registration]: msg`Registration`,
};
