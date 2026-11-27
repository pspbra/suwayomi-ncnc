/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

import FormGroup from '@mui/material/FormGroup';
import { useLingui } from '@lingui/react/macro';
import { CheckboxInput } from '@/base/components/inputs/CheckboxInput.tsx';
import { USER_PERMISSIONS, USER_PERMISSION_TO_TRANSLATION } from '@/features/user/Users.constants.ts';
import type { UserPermission } from '@/lib/graphql/generated/graphql-base.types.ts';

export const UserPermissionList = ({
    permissions,
    onChange,
    disabled,
}: {
    permissions: UserPermission[];
    onChange: (permissions: UserPermission[]) => void;
    disabled?: boolean;
}) => {
    const { t } = useLingui();

    return (
        <FormGroup>
            {USER_PERMISSIONS.map((permission) => (
                <CheckboxInput
                    key={permission}
                    disabled={disabled}
                    checked={permissions.includes(permission)}
                    onChange={(_, checked) =>
                        onChange(
                            checked
                                ? [...permissions, permission]
                                : permissions.filter((it) => it !== permission),
                        )
                    }
                    label={t(USER_PERMISSION_TO_TRANSLATION[permission])}
                />
            ))}
        </FormGroup>
    );
};
