/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

import gql from 'graphql-tag';
import { USER_FIELDS } from '@/lib/graphql/user/UserFragments.ts';

export const USER_LOGIN = gql`
    mutation USER_LOGIN($password: String!, $username: String!) {
        login(input: { password: $password, username: $username }) {
            accessToken
            refreshToken
        }
    }
`;

export const USER_REFRESH = gql`
    mutation USER_REFRESH($refreshToken: String!) {
        refreshToken(input: { refreshToken: $refreshToken }) {
            accessToken
        }
    }
`;

export const USER_REGISTER = gql`
    mutation USER_REGISTER($username: String!, $password: String!, $userPermissions: [UserPermission!]) {
        register(input: { username: $username, password: $password, userPermissions: $userPermissions }) {
            clientMutationId
        }
    }
`;

export const USER_UPDATE = gql`
    ${USER_FIELDS}
    mutation USER_UPDATE($userId: Int!, $permissions: [UserPermission!], $role: UserRole) {
        updateUser(input: { userId: $userId, permissions: $permissions, role: $role }) {
            user {
                ...USER_FIELDS
            }
        }
    }
`;

export const USER_CREATE_RECOVERY_CODE = gql`
    mutation USER_CREATE_RECOVERY_CODE($userId: Int!) {
        createRecoveryCode(input: { userId: $userId }) {
            code
            expiresAt
        }
    }
`;

export const USER_CREATE_REGISTRATION_CODE = gql`
    mutation USER_CREATE_REGISTRATION_CODE($permissions: [UserPermission!]) {
        createRegistrationCode(input: { permissions: $permissions }) {
            code
            expiresAt
        }
    }
`;

export const USER_REVOKE_CODE = gql`
    mutation USER_REVOKE_CODE($id: Int!) {
        revokeUserCode(input: { id: $id }) {
            clientMutationId
        }
    }
`;

export const USER_SET_PASSWORD = gql`
    mutation USER_SET_PASSWORD($oldPassword: String!, $newPassword: String!) {
        setPassword(input: { oldPassword: $oldPassword, newPassword: $newPassword }) {
            clientMutationId
        }
    }
`;
