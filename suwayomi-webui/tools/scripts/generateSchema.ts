/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

import fs from 'fs';
import * as path from 'path';
import { getIntrospectionQuery } from 'graphql';

const [, , serverUrl] = process.argv;

if (!serverUrl) {
    console.error('Usage: tsx tools/scripts/generateSchema.ts <graphql-endpoint>');
    console.error('Example: tsx tools/scripts/generateSchema.ts http://localhost:4567/api/graphql');
    process.exit(1);
}

/**
 * `inputValueDeprecation` keeps `@deprecated` input fields. The server still accepts them and the
 * WebUI relies on some of them (e.g. the settings that moved to per-user storage), but plain
 * introspection hides them, which would silently drop them from the generated types.
 */
const query = getIntrospectionQuery({ inputValueDeprecation: true, descriptions: true });

const response = await fetch(serverUrl, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ query }),
});

if (!response.ok) {
    console.error(`Failed to fetch the schema: ${response.status} ${response.statusText}`);
    process.exit(1);
}

const result = await response.json();

if (result.errors) {
    console.error(`Failed to introspect the schema: ${JSON.stringify(result.errors)}`);
    process.exit(1);
}

const targetPath = path.resolve(import.meta.dirname, '../../src/lib/graphql/schema.json');

fs.writeFileSync(targetPath, JSON.stringify(result));

console.log(`Schema written to ${targetPath}`);
