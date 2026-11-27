/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

import Button from '@mui/material/Button';
import type { ButtonProps } from '@mui/material/Button';
import { useLingui } from '@lingui/react/macro';
import { useManageMangaLibraryState } from '@/features/manga/hooks/useManageMangaLibraryState.tsx';
import type { ServerLibraryMangaFieldsFragment } from '@/lib/graphql/server-library/ServerLibraryQuery.ts';

/**
 * Adds or removes the current user's entry of a server library title.
 *
 * The server only accepts a title into the personal library while it is part of the server library, so the
 * button is disabled for removed titles which are still favorited.
 */
export const ServerLibraryPersonalStateButton = ({
    manga,
    ...buttonProps
}: { manga: ServerLibraryMangaFieldsFragment } & Pick<ButtonProps, 'size' | 'fullWidth' | 'variant' | 'color'>) => {
    const { t } = useLingui();
    const { updateLibraryState, isInLibrary } = useManageMangaLibraryState(manga, true);

    return (
        <Button {...buttonProps} onClick={updateLibraryState} disabled={!manga.inServerLibrary && !isInLibrary}>
            {isInLibrary ? t`Remove from my bookshelf` : t`Add to my bookshelf`}
        </Button>
    );
};
