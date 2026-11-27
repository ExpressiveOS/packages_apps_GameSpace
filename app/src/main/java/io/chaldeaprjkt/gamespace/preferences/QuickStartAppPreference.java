/*
 * Copyright (C) 2023-2024 the risingOS Android Project
 * SPDX-FileCopyrightText: 2026 The ExpressiveOS Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.chaldeaprjkt.gamespace.preferences;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Handler;
import android.os.Looper;
import android.util.AttributeSet;

import androidx.annotation.NonNull;
import androidx.preference.DialogPreference;
import androidx.preference.PreferenceManager;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class QuickStartAppPreference extends DialogPreference {

    private static final ExecutorService IO_EXECUTOR = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private volatile String[] appPackageNames = new String[0];
    private volatile String[] appNames = new String[0];
    private volatile boolean isLoaded = false;
    private volatile boolean isLoading = false;
    private final List<Runnable> pendingLoadListeners = new ArrayList<>();

    private static final class AppItem {
        final String packageName;
        final String label;

        AppItem(String packageName, String label) {
            this.packageName = packageName;
            this.label = label;
        }
    }

    public QuickStartAppPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    protected void onAttachedToHierarchy(PreferenceManager preferenceManager) {
        super.onAttachedToHierarchy(preferenceManager);
        loadInstalledUserAppsAsync(getContext());
    }

    public void loadInstalledUserAppsAsync(Context context) {
        if (context == null || isLoading) return;
        final Context appContext = context.getApplicationContext();
        isLoading = true;
        IO_EXECUTOR.execute(() -> {
            final List<AppItem> items = queryInstalledApps(appContext);
            final String[] pkgs = new String[items.size()];
            final String[] names = new String[items.size()];
            for (int i = 0; i < items.size(); i++) {
                pkgs[i] = items.get(i).packageName;
                names[i] = items.get(i).label;
            }
            mainHandler.post(() -> {
                appPackageNames = pkgs;
                appNames = names;
                isLoaded = true;
                isLoading = false;
                if (!pendingLoadListeners.isEmpty()) {
                    final List<Runnable> callbacks = new ArrayList<>(pendingLoadListeners);
                    pendingLoadListeners.clear();
                    for (Runnable callback : callbacks) {
                        callback.run();
                    }
                }
            });
        });
    }

    private static List<AppItem> queryInstalledApps(@NonNull Context context) {
        final PackageManager pm = context.getPackageManager();
        final Intent intent = new Intent(Intent.ACTION_MAIN, null);
        intent.addCategory(Intent.CATEGORY_LAUNCHER);

        final List<ResolveInfo> resolveInfos = pm.queryIntentActivities(intent, 0);
        final List<AppItem> items = new ArrayList<>(resolveInfos.size());
        final Set<String> seenPackages = new HashSet<>(resolveInfos.size());

        for (ResolveInfo resolveInfo : resolveInfos) {
            final ActivityInfo activityInfo = resolveInfo.activityInfo;
            if (activityInfo == null) continue;
            final String packageName = activityInfo.packageName;
            if (!seenPackages.add(packageName)) continue;
            try {
                final ApplicationInfo appInfo = pm.getApplicationInfo(packageName, 0);
                final CharSequence label = pm.getApplicationLabel(appInfo);
                items.add(new AppItem(packageName, label != null ? label.toString() : packageName));
            } catch (PackageManager.NameNotFoundException ignored) {
                // App uninstalled mid-query.
            }
        }

        Collections.sort(items, Comparator.comparing(item -> item.label, String.CASE_INSENSITIVE_ORDER));
        return items;
    }

    public boolean isAppsLoaded() {
        return isLoaded;
    }

    public void addOnLoadedListener(@NonNull Runnable listener) {
        if (isLoaded) {
            listener.run();
            return;
        }
        pendingLoadListeners.add(listener);
        loadInstalledUserAppsAsync(getContext());
    }

    public void removeOnLoadedListener(@NonNull Runnable listener) {
        pendingLoadListeners.remove(listener);
    }

    @NonNull
    public String[] getAppPackageNames() {
        return appPackageNames;
    }

    @NonNull
    public String[] getAppNames() {
        return appNames;
    }

    public void saveValue(String value) {
        persistString(value);
    }
}
