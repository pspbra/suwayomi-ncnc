/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

import { requestManager } from '@/lib/requests/RequestManager.ts';
import { d } from 'koration';
import { ControlledPromise } from '@/lib/ControlledPromise.ts';
import { AuthManager } from '@/features/authentication/AuthManager.ts';
import { assertIsDefined } from '@/base/Asserts.ts';
import { MigrationManager } from '@/features/migration/MigrationManager.ts';
import { defaultPromiseErrorHandler } from '@/lib/DefaultPromiseErrorHandler.ts';

type ActionConfig = [Key: string, Setup: () => Promise<unknown> | unknown][];
type InFlightAction = [Key: string, InFlightSetup: Promise<unknown> | unknown];

export class AppInitializer {
    private static actions: ControlledPromise[] = [];

    private static subscriptions: any[] = [];

    private static adminSubscriptions: any[] = [];

    private static adminDataStarted: boolean = false;

    static async start(): Promise<void> {
        AppInitializer.stop();
        await AppInitializer.authenticate();

        void AppInitializer.fetchBackgroundData();

        await AppInitializer.fetchRequiredData();
        await AppInitializer.startPostDataFetchLogic();
    }

    static stop(): void {
        AppInitializer.actions.forEach((promise) => promise.reject('stopped'));
        AppInitializer.actions = [];
        AppInitializer.subscriptions.forEach((subscription) => subscription.unsubscribe());
        AppInitializer.subscriptions = [];
        AppInitializer.stopAdminData();
    }

    private static async authenticate(): Promise<void> {
        await AppInitializer.executeActions([
            [
                'initializeAuthentication',
                async () => {
                    const response = await requestManager.getAuthStatus().response;

                    if (AuthManager.isAuthInitialized()) {
                        return;
                    }

                    assertIsDefined(response?.data);

                    AuthManager.setAuthRequired(false);
                    AuthManager.setAuthInitialized(true);
                    requestManager.processQueues();
                },
            ],
        ]);
    }

    private static async fetchRequiredData(): Promise<void> {
        await AppInitializer.executeActions([
            ['globalMeta', () => requestManager.getGlobalMeta().response],
            ['serverSettings', () => requestManager.getServerSettings().response],
            // The download status is admin-only state and must not be requested here: a rejected request would be
            // retried forever and the application would never finish starting. It gets loaded by startAdminData.
        ]);
    }

    private static async startPostDataFetchLogic(): Promise<void> {
        await AppInitializer.executeActions([
            [
                'resumeMigration',
                () => {
                    if (!MigrationManager.isActive()) {
                        navigator.locks
                            ?.request('migration-executor', async () => {
                                const resumed = await MigrationManager.resume();
                                if (resumed) {
                                    await MigrationManager.awaitCompletion();
                                }
                            })
                            .catch(defaultPromiseErrorHandler('ResumeMigration'));
                    }
                },
            ],
        ]);
    }

    private static async fetchBackgroundData(): Promise<void> {
        await AppInitializer.executeActions([
            // Fetch extension list on startup to show up-to-date number of available extension updates in the navigation bar
            // without having to open the extensions page.
            ['fetchExtensionList', () => requestManager.getExtensionListFetch().response],
            /*
             * Creates permanent subscriptions to always have the latest data.
             *
             * The download and updater subscriptions are missing on purpose, they observe admin-only global state
             * and are started by startAdminData once the account is known to be an admin.
             */
            ['webUISubscription', () => this.subscriptions.push(requestManager.webUIUpdateSubscription().subscribe())],
            ['syncSubscription', () => this.subscriptions.push(requestManager.syncSubscription().subscribe())],
        ]);
    }

    /**
     * Loads the global download and update state of the server and keeps it up to date.
     *
     * Every request this starts is rejected for a non-admin account, so it may only be called once the current
     * account is known and has the `ADMIN` role. Calling it repeatedly is safe.
     */
    static startAdminData(): void {
        if (AppInitializer.adminDataStarted) {
            return;
        }

        AppInitializer.adminDataStarted = true;

        AppInitializer.adminSubscriptions.push(requestManager.downloadSubscription().subscribe());
        AppInitializer.adminSubscriptions.push(requestManager.updaterSubscription().subscribe());

        // Fill the caches the subscriptions keep up to date.
        requestManager
            .getDownloadStatus()
            .response.catch(defaultPromiseErrorHandler('AppInitializer::startAdminData: downloadStatus'));
    }

    /**
     * Stops the admin-only subscriptions and drops the global state they filled.
     *
     * The account behind a session can change (token expiry, mode switch), in which case the global download and
     * update data of the previous admin must not stay in the cache for the next account.
     */
    static stopAdminData(): void {
        AppInitializer.adminSubscriptions.forEach((subscription) => subscription.unsubscribe());
        AppInitializer.adminSubscriptions = [];

        if (!AppInitializer.adminDataStarted) {
            return;
        }

        AppInitializer.adminDataStarted = false;

        const { cache } = requestManager.graphQLClient.client;
        ['downloadStatus', 'libraryUpdateStatus', 'lastUpdateTimestamp', 'serverLibraryStorage'].forEach((fieldName) =>
            cache.evict({ fieldName }),
        );
        cache.gc();
    }

    private static async executeActions(
        actions: ActionConfig,
        {
            timeout = d(5).seconds.inWholeMilliseconds,
            timeoutMultiplier = 1.5,
            maxTimeout = d(2).minutes.inWholeMilliseconds,
            promise,
        }: {
            timeout?: number;
            timeoutMultiplier?: number;
            maxTimeout?: number;
            promise?: ControlledPromise;
        } = {},
    ): Promise<void> {
        const finalPromise = promise ?? new ControlledPromise();
        if (!promise) {
            AppInitializer.actions.push(finalPromise);
        }

        const isAborted = async () => {
            try {
                const result = (await Promise.race([finalPromise.promise, Promise.resolve(false)])) ?? true;

                return result;
            } catch (error) {
                return true;
            }
        };

        if (await isAborted()) {
            return;
        }

        const inFlightActions = actions.map(([key, fn]) => [key, fn()] satisfies InFlightAction);

        const actionWithSuccessState = await Promise.all(
            inFlightActions.map(async ([key, inFlightAction]) => {
                try {
                    await inFlightAction;

                    return [key, true] as [string, boolean];
                } catch (e) {
                    return [key, false] as [string, boolean];
                }
            }),
        );

        const failedActions = actionWithSuccessState.filter(([_, succeeded]) => !succeeded);

        if (!failedActions.length) {
            finalPromise.resolve();

            return;
        }

        await new Promise((resolve) => {
            setTimeout(resolve, timeout);
        });

        await AppInitializer.executeActions(
            actions.filter(([key]) => failedActions.some(([k]) => k === key)),
            {
                timeout: (timeout * timeoutMultiplier) % maxTimeout,
                timeoutMultiplier,
                maxTimeout,
                promise,
            },
        );
    }
}
