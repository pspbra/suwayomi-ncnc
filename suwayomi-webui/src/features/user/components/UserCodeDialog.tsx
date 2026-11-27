/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

import Button from '@mui/material/Button';
import Dialog from '@mui/material/Dialog';
import DialogActions from '@mui/material/DialogActions';
import DialogContent from '@mui/material/DialogContent';
import DialogContentText from '@mui/material/DialogContentText';
import DialogTitle from '@mui/material/DialogTitle';
import IconButton from '@mui/material/IconButton';
import Stack from '@mui/material/Stack';
import TextField from '@mui/material/TextField';
import Typography from '@mui/material/Typography';
import ContentCopyIcon from '@mui/icons-material/ContentCopy';
import { useLingui } from '@lingui/react/macro';
import { copyToClipboard } from '@/lib/HelperFunctions.ts';
import { ClipBoardGuard } from '@/base/components/guard/ClipBoardGuard.tsx';
import { CustomTooltip } from '@/base/components/CustomTooltip.tsx';
import { epochToDate, getDateString } from '@/base/utils/DateHelper.ts';

export const UserCodeDialog = ({
    title,
    code,
    expiresAt,
    onClose,
}: {
    title: string;
    code: string;
    expiresAt: string;
    onClose: () => void;
}) => {
    const { t } = useLingui();

    return (
        <Dialog open onClose={onClose} fullWidth maxWidth="xs">
            <DialogTitle>{title}</DialogTitle>
            <DialogContent>
                <Stack sx={{ gap: 2, pt: 1 }}>
                    <DialogContentText>{t`Share this one-time code with the user. It will not be shown again.`}</DialogContentText>
                    <Stack direction="row" sx={{ gap: 1, alignItems: 'center' }}>
                        <TextField value={code} fullWidth slotProps={{ input: { readOnly: true } }} />
                        <ClipBoardGuard>
                            <CustomTooltip title={t`Copy code`}>
                                <IconButton onClick={() => copyToClipboard(code)}>
                                    <ContentCopyIcon />
                                </IconButton>
                            </CustomTooltip>
                        </ClipBoardGuard>
                    </Stack>
                    <Typography variant="caption" sx={{ color: 'text.secondary' }}>
                        {t`Expires: ${getDateString(epochToDate(Number(expiresAt)), true, true)}`}
                    </Typography>
                </Stack>
            </DialogContent>
            <DialogActions>
                <Button onClick={onClose} color="primary">
                    {t`Ok`}
                </Button>
            </DialogActions>
        </Dialog>
    );
};
