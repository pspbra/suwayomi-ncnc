/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

import { useState } from 'react';
import Button from '@mui/material/Button';
import Dialog from '@mui/material/Dialog';
import DialogActions from '@mui/material/DialogActions';
import DialogContent from '@mui/material/DialogContent';
import DialogContentText from '@mui/material/DialogContentText';
import DialogTitle from '@mui/material/DialogTitle';
import Stack from '@mui/material/Stack';
import Typography from '@mui/material/Typography';
import { useLingui } from '@lingui/react/macro';
import { requestManager } from '@/lib/requests/RequestManager.ts';
import { getErrorMessage } from '@/lib/HelperFunctions.ts';
import { CheckboxInput } from '@/base/components/inputs/CheckboxInput.tsx';
import { UserPermissionList } from '@/features/user/components/UserPermissionList.tsx';
import { DEFAULT_USER_PERMISSIONS } from '@/features/user/Users.constants.ts';
import type { UserPermission } from '@/lib/graphql/generated/graphql-base.types.ts';

export const CreateRegistrationCodeDialog = ({
    onClose,
    onCreated,
}: {
    onClose: () => void;
    onCreated: (code: string, expiresAt: string) => void;
}) => {
    const { t } = useLingui();

    const [createRegistrationCode, { loading: isSubmitting }] = requestManager.useCreateRegistrationCode();

    const [useDefaultPermissions, setUseDefaultPermissions] = useState(true);
    const [permissions, setPermissions] = useState<UserPermission[]>([...DEFAULT_USER_PERMISSIONS]);
    const [error, setError] = useState<string>();

    const handleClose = () => {
        if (!isSubmitting) {
            onClose();
        }
    };

    const handleSubmit = async () => {
        setError(undefined);

        try {
            const result = await createRegistrationCode({
                // omitting the permissions makes the server grant its default set
                variables: { permissions: useDefaultPermissions ? undefined : permissions },
            });
            const payload = result.data?.createRegistrationCode;

            if (payload) {
                onCreated(payload.code, payload.expiresAt);
            }

            onClose();
        } catch (e) {
            setError(getErrorMessage(e));
        }
    };

    return (
        <Dialog open onClose={handleClose} fullWidth maxWidth="xs">
            <DialogTitle>{t`Registration code`}</DialogTitle>
            <DialogContent>
                <Stack sx={{ gap: 2, pt: 1 }}>
                    <DialogContentText>
                        {t`Anyone with this code can create an account. It is valid for 7 days.`}
                    </DialogContentText>
                    <Stack>
                        <CheckboxInput
                            disabled={isSubmitting}
                            checked={useDefaultPermissions}
                            onChange={(_, checked) => setUseDefaultPermissions(checked)}
                            label={t`Grant the default permissions`}
                        />
                        {!useDefaultPermissions && (
                            <UserPermissionList
                                permissions={permissions}
                                onChange={setPermissions}
                                disabled={isSubmitting}
                            />
                        )}
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
                <Button onClick={handleSubmit} color="primary" disabled={isSubmitting}>
                    {t`Submit`}
                </Button>
            </DialogActions>
        </Dialog>
    );
};
