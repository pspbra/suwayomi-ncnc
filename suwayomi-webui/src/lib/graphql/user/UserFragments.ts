/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

import gql from 'graphql-tag';

export const USER_FIELDS = gql`
    fragment USER_FIELDS on UserType {
        id
        username
        roles
        permissions
    }
`;

export const USER_CODE_FIELDS = gql`
    fragment USER_CODE_FIELDS on UserCodeType {
        id
        purpose
        permissions
        createdAt
        expiresAt
        user {
            ...USER_FIELDS
        }
        createdBy {
            ...USER_FIELDS
        }
    }
    ${USER_FIELDS}
`;
