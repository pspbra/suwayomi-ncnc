/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

import { UserPermission, UserRole } from '@/lib/graphql/generated/graphql-base.types.ts';
import type { UserFieldsFragment } from '@/lib/graphql/generated/graphql.ts';

/** Whether an account may manage other accounts. The `ADMIN` role bypasses all permission checks. */
export const canManageUsers = (user: UserFieldsFragment): boolean =>
    user.roles.includes(UserRole.Admin) || user.permissions.includes(UserPermission.ManageUsers);
