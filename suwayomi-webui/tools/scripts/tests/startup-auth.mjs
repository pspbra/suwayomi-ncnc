/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

// Run against `vite preview` with Playwright installed, or set PLAYWRIGHT_MODULE
// to the absolute path of an existing Playwright index.mjs.
import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import { pathToFileURL } from 'node:url';

const { chromium } = await import(
    process.env.PLAYWRIGHT_MODULE ? pathToFileURL(process.env.PLAYWRIGHT_MODULE).href : 'playwright'
);
const browser = await chromium.launch({ channel: process.env.BROWSER_CHANNEL || 'msedge', headless: true });
const tokenKey = 'suwayomi_webui__auth-refresh-token';
const unauthorized = {
    errors: [{ message: 'suwayomi.tachidesk.server.user.UnauthorizedException: Unauthorized' }],
    data: null,
};
// Synthetic responses only; no real account or library data is stored here.
const fixtures = JSON.parse(await readFile(new URL('./fixtures/startup.json', import.meta.url), 'utf8'));

try {
    const testSession = async (expiredToken) => {
        const context = await browser.newContext();
        const page = await context.newPage();
        const errors = [];
        const operations = [];
        page.on('pageerror', (error) => errors.push(error.message));
        if (expiredToken) {
            await page.addInitScript(({ key }) => localStorage.setItem(key, 'expired-test-token'), { key: tokenKey });
        }
        // All API responses are mocked; this test never logs in to a real server.
        await page.route('**/api/graphql', async (route) => {
            operations.push(route.request().postDataJSON().operationName);
            await route.fulfill({ json: unauthorized });
        });
        await page.goto(process.env.TEST_BASE_URL || 'http://127.0.0.1:4173/');
        await page.locator('#username').waitFor({ state: 'visible', timeout: 15000 });
        assert.equal(await page.evaluate((key) => localStorage.getItem(key), tokenKey), null);
        assert.equal(operations.filter((op) => op === 'USER_REFRESH').length, expiredToken ? 1 : 0);

        await page.locator('#username').fill('test-user');
        await page.locator('input[type="password"]').fill('incorrect-password');
        const loginButton = page.getByRole('button', { name: /log in/i });
        await loginButton.click();
        await page.getByText('Could not log in to Suwayomi', { exact: true }).waitFor();
        assert.equal(await loginButton.isEnabled(), true);
        assert.equal(operations.filter((op) => op === 'USER_LOGIN').length, 1);
        assert.equal(operations.filter((op) => op === 'USER_REFRESH').length, expiredToken ? 1 : 0);
        assert.deepEqual(errors, []);
        console.log(`PASS: ${expiredToken ? 'expired token' : 'fresh session'} shows login and handles rejected login`);
        await context.close();
    };
    await testSession(false);
    await testSession(true);

    const testVersionFailure = async ({ authRequired, savedSession = false }) => {
        const context = await browser.newContext();
        const page = await context.newPage();
        const operations = [];
        const errors = [];
        page.on('pageerror', (error) => errors.push(error.message));
        if (savedSession) {
            await page.addInitScript(({ key }) => localStorage.setItem(key, 'mock-refresh-token'), { key: tokenKey });
        }
        await page.routeWebSocket('**/api/graphql', (socket) => {
            socket.onMessage((message) => {
                if (JSON.parse(message).type === 'connection_init') {
                    socket.send(JSON.stringify({ type: 'connection_ack' }));
                }
            });
        });
        await page.route('**/api/graphql', async (route) => {
            const { operationName } = route.request().postDataJSON();
            operations.push(operationName);
            if (operationName === 'USER_REFRESH') {
                await route.fulfill({ json: { data: { refreshToken: { accessToken: 'mock-access-token' } } } });
                return;
            }
            const authenticated = route.request().headers().authorization === 'Bearer mock-access-token';
            if (authRequired && !authenticated && operationName !== 'USER_LOGIN') {
                await route.fulfill({ json: unauthorized });
                return;
            }
            if (operationName === 'GET_ABOUT') {
                await route.fulfill({
                    json: {
                        data: null,
                        errors: [{ message: 'Failed to get current version', path: ['aboutWebUI'] }],
                    },
                });
                return;
            }
            assert.ok(fixtures[operationName], `Missing fixture for ${operationName}`);
            await route.fulfill({ json: fixtures[operationName] });
        });
        await page.goto(process.env.TEST_BASE_URL || 'http://127.0.0.1:4173/');
        if (authRequired && !savedSession) {
            await page.locator('#username').fill('test-user');
            await page.locator('input[type="password"]').fill('test-password');
            await page.getByRole('button', { name: /log in/i }).click();
        }
        await page.waitForURL('**/library', { timeout: 15000 });
        await page.getByText('Your library is empty', { exact: true }).waitFor();
        for (const operation of [
            'GET_AUTH_STATUS',
            'GET_GLOBAL_METADATAS',
            'GET_SERVER_SETTINGS',
            'GET_DOWNLOAD_STATUS',
            'GET_ABOUT',
        ]) {
            assert.ok(operations.includes(operation), `Expected ${operation}`);
        }
        assert.equal(operations.filter((op) => op === 'USER_REFRESH').length, savedSession ? 1 : 0);
        assert.deepEqual(errors, []);
        console.log(
            `PASS: library opens despite failed WebUI version query (auth=${authRequired}, savedSession=${savedSession})`,
        );
        await context.close();
    };
    await testVersionFailure({ authRequired: true });
    await testVersionFailure({ authRequired: true, savedSession: true });
    await testVersionFailure({ authRequired: false });
} finally {
    await browser.close();
}
