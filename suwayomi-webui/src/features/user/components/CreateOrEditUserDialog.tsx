/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

import { useState } from 'react';
import Button from '@mui/material/Button';
import TextField from '@mui/material/TextField';
import Dialog from '@mui/material/Dialog';
import DialogActions from '@mui/material/DialogActions';
import DialogContent from '@mui/material/DialogContent';
import DialogTitle from '@mui/material/DialogTitle';
import FormControl from '@mui/material/FormControl';
import InputLabel from '@mui/material/InputLabel';
import MenuItem from '@mui/material/MenuItem';
import Stack from '@mui/material/Stack';
import Typography from '@mui/material/Typography';
import { useLingui } from '@lingui/react/macro';
import { requestManager } from '@/lib/requests/RequestManager.ts';
import { getErrorMessage } from '@/lib/HelperFunctions.ts';
import { PasswordTextField } from '@/base/components/inputs/PasswordTextField.tsx';
import { Select } from '@/base/components/inputs/Select.tsx';
import { UserPermissionList } from '@/features/user/components/UserPermissionList.tsx';
import {
    ASSIGNABLE_USER_ROLES,
    DEFAULT_USER_PERMISSIONS,
    USER_ROLE_TO_TRANSLATION,
} from '@/features/user/Users.constants.ts';
import type { UserPermission } from '@/lib/graphql/generated/graphql-base.types.ts';
import { UserRole } from '@/lib/graphql/generated/graphql-base.types.ts';
import type { UserFieldsFragment } from '@/lib/graphql/generated/graphql.ts';
import { GET_USERS } from '@/lib/graphql/user/UserQuery.ts';

export const CreateOrEditUserDialog = ({ user, onClose }: { user?: UserFieldsFragment; onClose: () => void }) => {
    const { t } = useLingui();

    const isEditMode = !!user;

    const [registerUser, { loading: isCreating }] = requestManager.useRegisterUser();
    const [updateUser, { loading: isUpdating }] = requestManager.useUpdateUser();

    const [username, setUsername] = useState(user?.username ?? '');
    const [password, setPassword] = useState('');
    const [role, setRole] = useState<UserRole>(user?.roles[0] ?? UserRole.User);
    const [permissions, setPermissions] = useState<UserPermission[]>(user?.permissions ?? [...DEFAULT_USER_PERMISSIONS]);
    const [error, setError] = useState<string>();

    const isSubmitting = isCreating || isUpdating;
    const canSubmit = isEditMode || (!!username.trim() && !!password);

    const handleClose = () => {
        if (!isSubmitting) {
            onClose();
        }
    };

    const handleSubmit = async () => {
        setError(undefined);

        try {
            if (user) {
                await updateUser({
                    variables: {
                        userId: user.id,
                        permissions,
                        // only send the role when it actually changed - granting a role requires the ADMIN role
                        role: user.roles[0] === role ? undefined : role,
                    },
                    refetchQueries: [GET_USERS],
                });
            } else {
                await registerUser({
                    variables: { username: username.trim(), password, userPermissions: permissions },
                    refetchQueries: [GET_USERS],
                });
            }

            onClose();
        } catch (e) {
            setError(getErrorMessage(e));
        }
    };

    return (
        <Dialog open onClose={handleClose} fullWidth maxWidth="xs">
            <DialogTitle>{isEditMode ? t`Edit user` : t`New user`}</DialogTitle>
            <DialogContent>
                <Stack sx={{ gap: 2, pt: 1 }}>
                    {isEditMode ? (
                        <FormControl fullWidth disabled={isSubmitting}>
                            <InputLabel id="user-role-label">{t`Role`}</InputLabel>
                            <Select<UserRole>
                                labelId="user-role-label"
                                label={t`Role`}
                                value={role}
                                onChange={({ target: { value } }) => setRole(value as UserRole)}
                            >
                                {ASSIGNABLE_USER_ROLES.map((assignableRole) => (
                                    <MenuItem key={assignableRole} value={assignableRole}>
                                        {t(USER_ROLE_TO_TRANSLATION[assignableRole])}
                                    </MenuItem>
                                ))}
                            </Select>
                        </FormControl>
                    ) : (
                        <>
                            <TextField
                                autoFocus
                                fullWidth
                                label={t`Username`}
                                value={username}
                                onChange={(e) => setUsername(e.target.value)}
                                disabled={isSubmitting}
                            />
                            <PasswordTextField
                                fullWidth
                                value={password}
                                onChange={(e) => setPassword(e.target.value)}
                                disabled={isSubmitting}
                            />
                        </>
                    )}
                    <Stack>
                        <Typography variant="subtitle2">{t`Permissions`}</Typography>
                        <UserPermissionList permissions={permissions} onChange={setPermissions} disabled={isSubmitting} />
                    </Stack>
                    {error && (
                        <Typography variant="body2" sx={{ color: 'error.main' }}>
                            {error}
                        </Typography>
                    )}
                </Stack>
            </DialogContent>
            <DialogActions>
                <Button onClick={handleClose} color="primary" disabled={isSubmitting}>
                    {t`Cancel`}
                </Button>
                <Button onClick={handleSubmit} color="primary" disabled={!canSubmit || isSubmitting}>
                    {t`Submit`}
                </Button>
            </DialogActions>
        </Dialog>
    );
};
