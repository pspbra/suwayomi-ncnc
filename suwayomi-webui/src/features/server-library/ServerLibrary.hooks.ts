/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

import { useCallback, useState } from 'react';
import type { DocumentNode } from 'graphql';
import type { OperationVariables } from '@apollo/client/core';
import { requestManager } from '@/lib/requests/RequestManager.ts';
import { getErrorMessage } from '@/lib/HelperFunctions.ts';

type ServerLibraryActionOptions = {
    /**
     * The active queries which have to be re-read after the mutation.
     *
     * Only the queries a mutation can actually invalidate may be listed - the library and chapter pages of the
     * title, not every active query of the application.
     */
    refetch?: DocumentNode[];
    /** Runs after the mutation succeeded, before the queries get re-read (e.g. to close a dialog). */
    onSuccess?: () => void;
};

/**
 * Runs a server library mutation and keeps track of its progress.
 *
 * The mutation and the following refresh are handled separately: a failing refresh does not turn a successful
 * mutation into a failure, it only means the displayed data can be outdated.
 */
export const useServerLibraryAction = () => {
    const [busy, setBusy] = useState(false);
    const [error, setError] = useState('');
    const [refreshError, setRefreshError] = useState('');

    const run = useCallback(
        async <Variables extends OperationVariables>(
            mutation: DocumentNode,
            variables: Variables,
            { refetch, onSuccess }: ServerLibraryActionOptions = {},
        ): Promise<boolean> => {
            const { client } = requestManager.graphQLClient;

            setBusy(true);
            setError('');
            setRefreshError('');

            let succeeded = false;
            try {
                await client.mutate({ mutation, variables });
                succeeded = true;
                onSuccess?.();
            } catch (e) {
                setError(getErrorMessage(e));
            }

            if (succeeded && refetch?.length) {
                try {
                    await client.refetchQueries({ include: refetch });
                } catch (e) {
                    setRefreshError(getErrorMessage(e));
                }
            }

            setBusy(false);

            return succeeded;
        },
        [],
    );

    const clearError = useCallback(() => {
        setError('');
    }, []);

    const clearRefreshError = useCallback(() => {
        setRefreshError('');
    }, []);

    return { busy, error, refreshError, run, clearError, clearRefreshError };
};
