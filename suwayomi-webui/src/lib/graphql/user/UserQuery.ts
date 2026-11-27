/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

import gql from 'graphql-tag';
import type { TypedDocumentNode } from '@apollo/client/core';
import { USER_CODE_FIELDS, USER_FIELDS } from '@/lib/graphql/user/UserFragments.ts';

export type GetAuthStatusQuery = { metas: { __typename: 'GlobalMetaNodeList' } };

// Probe an authenticated field without depending on optional WebUI version files.
export const GET_AUTH_STATUS: TypedDocumentNode<GetAuthStatusQuery> = gql`
    query GET_AUTH_STATUS {
        metas(first: 0) {
            __typename
        }
    }
`;

export const GET_USERS = gql`
    ${USER_FIELDS}
    query GET_USERS {
        users {
            totalCount
            nodes {
                ...USER_FIELDS
            }
        }
    }
`;

export const GET_CURRENT_USER = gql`
    ${USER_FIELDS}
    query GET_CURRENT_USER {
        user {
            ...USER_FIELDS
        }
    }
`;

export const GET_USER_CODES = gql`
    ${USER_CODE_FIELDS}
    query GET_USER_CODES {
        userCodes {
            ...USER_CODE_FIELDS
        }
    }
`;
