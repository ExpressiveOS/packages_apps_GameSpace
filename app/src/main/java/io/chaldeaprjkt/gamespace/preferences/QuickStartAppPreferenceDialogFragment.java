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
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.preference.PreferenceDialogFragmentCompat;

import java.util.HashSet;
import java.util.Set;

import io.chaldeaprjkt.gamespace.R;

public class QuickStartAppPreferenceDialogFragment extends PreferenceDialogFragmentCompat {

    public interface QuickStartAppListener {
        String getSavedQuickStartApps();
        void saveQuickStartApps(String apps);
    }

    private final Set<String> selectedPackages = new HashSet<>();
    private QuickStartAppListener listener;

    public void setListener(QuickStartAppListener listener) {
        this.listener = listener;
    }

    public static QuickStartAppPreferenceDialogFragment newInstance(String key) {
        final QuickStartAppPreferenceDialogFragment fragment = new QuickStartAppPreferenceDialogFragment();
        final Bundle b = new Bundle(1);
        b.putString(ARG_KEY, key);
        fragment.setArguments(b);
        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (listener == null && getTargetFragment() instanceof QuickStartAppListener) {
            listener = (QuickStartAppListener) getTargetFragment();
        }
    }

    @Override
    protected void onPrepareDialogBuilder(@NonNull AlertDialog.Builder builder) {
        final QuickStartAppPreference preference = (QuickStartAppPreference) getPreference();
        final Context context = getContext();
        if (preference == null || context == null || listener == null) {
            return;
        }

        selectedPackages.clear();
        final String savedApps = listener.getSavedQuickStartApps();
        if (savedApps != null && !savedApps.isEmpty()) {
            for (String app : savedApps.split(",")) {
                if (!app.isEmpty()) {
                    selectedPackages.add(app);
                }
            }
        }

        final String[] appNames = preference.getAppNames();
        final String[] appPackageNames = preference.getAppPackageNames();

        builder.setTitle(R.string.quick_start_apps_title)
                .setNegativeButton(android.R.string.cancel, null);

        if (!preference.isAppsLoaded() && appNames.length == 0) {
            builder.setMessage(R.string.quick_start_apps_loading);
            return;
        }

        final boolean[] checkedItems = new boolean[appNames.length];
        for (int i = 0; i < appPackageNames.length; i++) {
            checkedItems[i] = selectedPackages.contains(appPackageNames[i]);
        }

        builder.setMultiChoiceItems(appNames, checkedItems, (dialog, which, isChecked) -> {
                    final String selectedApp = appPackageNames[which];
                    if (isChecked) {
                        selectedPackages.add(selectedApp);
                    } else {
                        selectedPackages.remove(selectedApp);
                    }
                })
                .setPositiveButton(android.R.string.ok, (dialog, which) -> saveSelectedApps());
    }

    @Override
    public void onDialogClosed(boolean positiveResult) {}

    private void saveSelectedApps() {
        final String[] selectedArray = selectedPackages.toArray(new String[0]);
        final StringBuilder sb = new StringBuilder();
        for (int i = 0; i < selectedArray.length; i++) {
            sb.append(selectedArray[i]);
            if (i < selectedArray.length - 1) {
                sb.append(",");
            }
        }
        final String result = sb.toString();
        listener.saveQuickStartApps(result);
        final QuickStartAppPreference preference = (QuickStartAppPreference) getPreference();
        if (preference != null) {
            preference.saveValue(result);
        }
    }
}
