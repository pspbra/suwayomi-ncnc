/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

import { useState } from 'react';
import Box from '@mui/material/Box';
import Card from '@mui/material/Card';
import IconButton from '@mui/material/IconButton';
import List from '@mui/material/List';
import ListItem from '@mui/material/ListItem';
import ListItemButton from '@mui/material/ListItemButton';
import ListItemText from '@mui/material/ListItemText';
import Stack from '@mui/material/Stack';
import Typography from '@mui/material/Typography';
import AddIcon from '@mui/icons-material/Add';
import DeleteIcon from '@mui/icons-material/Delete';
import EditIcon from '@mui/icons-material/Edit';
import KeyIcon from '@mui/icons-material/Key';
import { useLingui } from '@lingui/react/macro';
import { requestManager } from '@/lib/requests/RequestManager.ts';
import { getErrorMessage } from '@/lib/HelperFunctions.ts';
import { defaultPromiseErrorHandler } from '@/lib/DefaultPromiseErrorHandler.ts';
import { LoadingPlaceholder } from '@/base/components/feedback/LoadingPlaceholder.tsx';
import { EmptyViewAbsoluteCentered } from '@/base/components/feedback/EmptyViewAbsoluteCentered.tsx';
import { ListCardContent } from '@/base/components/lists/cards/ListCardContent.tsx';
import { ListSubheader } from '@/base/components/lists/ListSubheader.tsx';
import { TypographyMaxLines } from '@/base/components/texts/TypographyMaxLines.tsx';
import { CustomTooltip } from '@/base/components/CustomTooltip.tsx';
import { DEFAULT_FULL_FAB_HEIGHT, StyledFab } from '@/base/components/buttons/StyledFab.tsx';
import { Confirmation } from '@/base/AppAwaitableComponent.ts';
import { makeToast } from '@/base/utils/Toast.ts';
import { epochToDate, getDateString } from '@/base/utils/DateHelper.ts';
import { useAppTitle } from '@/features/navigation-bar/hooks/useAppTitle.ts';
import { CreateOrEditUserDialog } from '@/features/user/components/CreateOrEditUserDialog.tsx';
import { CreateRegistrationCodeDialog } from '@/features/user/components/CreateRegistrationCodeDialog.tsx';
import { UserCodeDialog } from '@/features/user/components/UserCodeDialog.tsx';
import {
    BUILT_IN_ADMIN_USER_ID,
    USER_CODE_PURPOSE_TO_TRANSLATION,
    USER_PERMISSION_TO_TRANSLATION,
    USER_ROLE_TO_TRANSLATION,
} from '@/features/user/Users.constants.ts';
import { UserRole } from '@/lib/graphql/generated/graphql-base.types.ts';
import type { UserCodeFieldsFragment, UserFieldsFragment } from '@/lib/graphql/generated/graphql.ts';
import { canManageUsers } from '@/features/user/Users.utils.ts';

const UserCard = ({
    user,
    isCurrentUser,
    onEdit,
    onIssueRecoveryCode,
}: {
    user: UserFieldsFragment;
    isCurrentUser: boolean;
    onEdit: (user: UserFieldsFragment) => void;
    onIssueRecoveryCode: (user: UserFieldsFragment) => void;
}) => {
    const { t } = useLingui();

    const isBuiltInAdmin = user.id === BUILT_IN_ADMIN_USER_ID;
    const isAdmin = user.roles.includes(UserRole.Admin);
    const role = user.roles[0] ?? UserRole.User;

    const getPermissionsText = () => {
        if (isAdmin) {
            return t`All permissions`;
        }

        if (!user.permissions.length) {
            return t`No permissions`;
        }

        return user.permissions.map((permission) => t(USER_PERMISSION_TO_TRANSLATION[permission])).join(', ');
    };

    return (
        <ListItem sx={{ p: 1, pb: 0 }}>
            <Card sx={{ width: '100%' }}>
                <ListCardContent sx={{ flexWrap: 'wrap', justifyContent: 'space-between' }}>
                    <Stack>
                        <TypographyMaxLines variant="h6" component="h3">
                            {user.username}
                            {isCurrentUser ? ` ${t`(you)`}` : ''}
                        </TypographyMaxLines>
                        <Typography variant="caption" sx={{ color: 'text.secondary' }}>
                            {t(USER_ROLE_TO_TRANSLATION[role])}
                            {isBuiltInAdmin ? ` — ${t`Built-in admin`}` : ''}
                            {' — '}
                            {getPermissionsText()}
                        </Typography>
                    </Stack>
                    {!isBuiltInAdmin && (
                        <Stack sx={{ flexDirection: 'row', justifyContent: 'end', flexGrow: 1 }}>
                            <CustomTooltip title={t`Issue recovery code`}>
                                <IconButton onClick={() => onIssueRecoveryCode(user)}>
                                    <KeyIcon />
                                </IconButton>
                            </CustomTooltip>
                            <CustomTooltip title={t`Edit`}>
                                <IconButton onClick={() => onEdit(user)}>
                                    <EditIcon />
                                </IconButton>
                            </CustomTooltip>
                        </Stack>
                    )}
                </ListCardContent>
            </Card>
        </ListItem>
    );
};

const UserCodeCard = ({
    userCode,
    onRevoke,
}: {
    userCode: UserCodeFieldsFragment;
    onRevoke: (userCode: UserCodeFieldsFragment) => void;
}) => {
    const { t } = useLingui();

    return (
        <ListItem sx={{ p: 1, pb: 0 }}>
            <Card sx={{ width: '100%' }}>
                <ListCardContent sx={{ flexWrap: 'wrap', justifyContent: 'space-between' }}>
                    <Stack>
                        <TypographyMaxLines variant="body1">
                            {t(USER_CODE_PURPOSE_TO_TRANSLATION[userCode.purpose])}
                        </TypographyMaxLines>
                        <Typography variant="caption" sx={{ color: 'text.secondary' }}>
                            {userCode.user ? t`For ${userCode.user.username}` : t`Not redeemed yet`}
                            {' — '}
                            {t`Expires: ${getDateString(epochToDate(Number(userCode.expiresAt)), true, true)}`}
                        </Typography>
                    </Stack>
                    <Stack sx={{ flexDirection: 'row', justifyContent: 'end', flexGrow: 1 }}>
                        <CustomTooltip title={t`Revoke`}>
                            <IconButton onClick={() => onRevoke(userCode)}>
                                <DeleteIcon />
                            </IconButton>
                        </CustomTooltip>
                    </Stack>
                </ListCardContent>
            </Card>
        </ListItem>
    );
};

const UsersManagement = ({ currentUserId }: { currentUserId: number }) => {
    const { t } = useLingui();

    const { data, loading, error, refetch, dataState } = requestManager.useGetUsers();
    const { data: userCodesData, refetch: refetchUserCodes } = requestManager.useGetUserCodes();
    const [createRecoveryCode] = requestManager.useCreateUserRecoveryCode();
    const [revokeUserCode] = requestManager.useRevokeUserCode();

    const [userToEdit, setUserToEdit] = useState<UserFieldsFragment>();
    const [isCreatingUser, setIsCreatingUser] = useState(false);
    const [isCreatingRegistrationCode, setIsCreatingRegistrationCode] = useState(false);
    const [issuedCode, setIssuedCode] = useState<{ title: string; code: string; expiresAt: string }>();

    const refreshUserCodes = () =>
        refetchUserCodes().catch(defaultPromiseErrorHandler('UsersSettings::refetchUserCodes'));

    const issueRecoveryCode = async (user: UserFieldsFragment) => {
        try {
            const result = await createRecoveryCode({ variables: { userId: user.id } });
            const payload = result.data?.createRecoveryCode;

            if (payload) {
                setIssuedCode({
                    title: t`Recovery code for ${user.username}`,
                    code: payload.code,
                    expiresAt: payload.expiresAt,
                });
            }

            refreshUserCodes();
        } catch (e) {
            makeToast(t`Could not create recovery code`, 'error', getErrorMessage(e));
        }
    };

    const revokeCode = async (userCode: UserCodeFieldsFragment) => {
        try {
            await Confirmation.show({
                title: t`Are you sure?`,
                message: t`You are about to revoke this code`,
            });
        } catch (e) {
            return;
        }

        try {
            await revokeUserCode({ variables: { id: userCode.id } });
            refreshUserCodes();
        } catch (e) {
            makeToast(t`Could not revoke code`, 'error', getErrorMessage(e));
        }
    };

    if (loading && dataState !== 'complete') {
        return <LoadingPlaceholder />;
    }

    if (error) {
        return (
            <EmptyViewAbsoluteCentered
                message={t`Unable to load users`}
                messageExtra={getErrorMessage(error)}
                retry={() => refetch().catch(defaultPromiseErrorHandler('UsersSettings::refetch'))}
            />
        );
    }

    const users = data?.users.nodes ?? [];
    const userCodes = userCodesData?.userCodes ?? [];

    return (
        <Box sx={{ pb: DEFAULT_FULL_FAB_HEIGHT }}>
            <List sx={{ pt: 0 }}>
                {users.map((user) => (
                    <UserCard
                        key={user.id}
                        user={user}
                        isCurrentUser={user.id === currentUserId}
                        onEdit={setUserToEdit}
                        onIssueRecoveryCode={issueRecoveryCode}
                    />
                ))}
            </List>
            <List sx={{ pt: 0 }}>
                <ListSubheader>{t`Registration codes`}</ListSubheader>
                {userCodes.map((userCode) => (
                    <UserCodeCard key={userCode.id} userCode={userCode} onRevoke={revokeCode} />
                ))}
                <ListItemButton onClick={() => setIsCreatingRegistrationCode(true)}>
                    <ListItemText primary={t`Create registration code`} />
                </ListItemButton>
            </List>
            <StyledFab
                variant="extended"
                color="primary"
                sx={{ gap: 1 }}
                onClick={() => setIsCreatingUser(true)}
            >
                <AddIcon />
                {t`Add user`}
            </StyledFab>
            {(isCreatingUser || userToEdit) && (
                <CreateOrEditUserDialog
                    user={userToEdit}
                    onClose={() => {
                        setIsCreatingUser(false);
                        setUserToEdit(undefined);
                    }}
                />
            )}
            {isCreatingRegistrationCode && (
                <CreateRegistrationCodeDialog
                    onClose={() => setIsCreatingRegistrationCode(false)}
                    onCreated={(code, expiresAt) =>
                        setIssuedCode({ title: t`Registration code`, code, expiresAt })
                    }
                />
            )}
            {issuedCode && <UserCodeDialog {...issuedCode} onClose={() => setIssuedCode(undefined)} />}
        </Box>
    );
};

export const UsersSettings = () => {
    const { t } = useLingui();

    useAppTitle(t`Users`);

    const { data, loading, error, refetch, dataState } = requestManager.useGetCurrentUser();

    if (loading && dataState !== 'complete') {
        return <LoadingPlaceholder />;
    }

    if (error || !data) {
        return (
            <EmptyViewAbsoluteCentered
                message={t`Unable to load data`}
                messageExtra={error ? getErrorMessage(error) : undefined}
                retry={() => refetch().catch(defaultPromiseErrorHandler('UsersSettings::refetchCurrentUser'))}
            />
        );
    }

    if (!canManageUsers(data.user)) {
        return <EmptyViewAbsoluteCentered message={t`You do not have permission to manage users`} />;
    }

    return <UsersManagement currentUserId={data.user.id} />;
};
