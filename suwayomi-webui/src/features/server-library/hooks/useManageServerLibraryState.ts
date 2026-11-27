/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

import { useCallback, useState } from 'react';
import { useLingui } from '@lingui/react/macro';
import { requestManager } from '@/lib/requests/RequestManager.ts';
import { makeToast } from '@/base/utils/Toast.ts';
import { getErrorMessage } from '@/lib/HelperFunctions.ts';
import { Mangas } from '@/features/manga/services/Mangas.ts';
import { ADD_SERVER_LIBRARY_MANGA } from '@/lib/graphql/server-library/ServerLibraryMutation.ts';
import { GET_SERVER_LIBRARY } from '@/lib/graphql/server-library/ServerLibraryQuery.ts';
import type { MangaIdInfo, MangaInServerLibraryInfo, MangaTitleInfo } from '@/features/manga/Manga.types.ts';

/**
 * The live state of a shared collection entry.
 *
 * Reading it from the cache keeps every surface which queries the manga fragment in sync, the local state is only
 * the fallback for data which was fetched before the entry changed.
 */
export const useManageServerLibraryState = (
    manga: MangaIdInfo & MangaTitleInfo & Partial<MangaInServerLibraryInfo>,
) => {
    const { t } = useLingui();

    const [isInServerLibrary, setIsInServerLibrary] = useState(!!manga.inServerLibrary);

    /**
     * Adds a title of a source to the shared collection of the server.
     *
     * It is idempotent and never adds the title to the personal library - that stays a separate action of the
     * server library page.
     */
    const addToServerLibrary = useCallback(() => {
        const { client } = requestManager.graphQLClient;

        client
            .mutate({ mutation: ADD_SERVER_LIBRARY_MANGA, variables: { mangaId: manga.id } })
            .then(() => {
                setIsInServerLibrary(true);
                makeToast(t`Added manga to the server library!`, 'success');
                return client.refetchQueries({ include: [GET_SERVER_LIBRARY] });
            })
            .catch((e) => {
                makeToast(t`Could not add manga to the server library!`, 'error', getErrorMessage(e));
            });
    }, [manga.id]);

    return {
        addToServerLibrary,
        isInServerLibrary:
            Mangas.getFromCache<MangaInServerLibraryInfo>(manga.id)?.inServerLibrary ?? isInServerLibrary,
    };
};
