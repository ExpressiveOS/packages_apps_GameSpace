/*
 * Copyright (C) 2026 crDroid Android Project
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
package io.chaldeaprjkt.gamespace.preferences.appselector

import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import androidx.lifecycle.lifecycleScope
import androidx.preference.Preference
import com.android.settingslib.widget.SettingsBasePreferenceFragment
import dagger.hilt.android.AndroidEntryPoint
import io.chaldeaprjkt.gamespace.R
import io.chaldeaprjkt.gamespace.data.SystemSettings
import io.chaldeaprjkt.gamespace.preferences.AppListPreferences
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@AndroidEntryPoint(SettingsBasePreferenceFragment::class)
class AppSelectorFragment : Hilt_AppSelectorFragment() {

    @Inject
    lateinit var systemSettings: SystemSettings

    private var allApps: List<AppEntry> = emptyList()
    private var isLoading: Boolean = true
    private var searchQuery: String = ""

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        preferenceScreen = preferenceManager.createPreferenceScreen(requireContext())
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        val baseView = super.onCreateView(inflater, container, savedInstanceState)
        val wrapper = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            )
        }

        val searchHeader = inflater.inflate(R.layout.view_app_selector_search_header, wrapper, false)
        val searchInput = searchHeader.findViewById<EditText>(R.id.app_selector_search_input)
        val clearButton = searchHeader.findViewById<ImageButton>(R.id.app_selector_search_clear)

        searchInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) {
                val query = s?.toString().orEmpty()
                clearButton.visibility = if (query.isEmpty()) View.GONE else View.VISIBLE
                if (searchQuery != query) {
                    searchQuery = query
                    renderApps()
                }
            }
        })
        clearButton.setOnClickListener {
            searchInput.text?.clear()
        }

        wrapper.addView(searchHeader)
        wrapper.addView(
            baseView,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f,
            ),
        )
        return wrapper
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        loadApps()
    }

    private fun loadApps() {
        val ctx = requireContext()
        val pm = ctx.packageManager
        val ownPkg = ctx.packageName
        val registered = systemSettings.userGames.map { it.packageName }.toSet()

        isLoading = true
        renderApps()

        viewLifecycleOwner.lifecycleScope.launch {
            val apps = withContext(Dispatchers.IO) {
                val intent = Intent(Intent.ACTION_MAIN).apply {
                    addCategory(Intent.CATEGORY_LAUNCHER)
                }
                pm.queryIntentActivities(intent, PackageManager.ResolveInfoFlags.of(0))
                    .asSequence()
                    .mapNotNull { ri: ResolveInfo ->
                        val info = ri.activityInfo ?: return@mapNotNull null
                        if (info.packageName == ownPkg) return@mapNotNull null
                        if (info.packageName in registered) return@mapNotNull null
                        try {
                            val ai = pm.getApplicationInfo(
                                info.packageName,
                                PackageManager.ApplicationInfoFlags.of(0)
                            )
                            AppEntry(
                                packageName = info.packageName,
                                label = pm.getApplicationLabel(ai).toString(),
                                icon = ai.loadIcon(pm)
                            )
                        } catch (_: PackageManager.NameNotFoundException) {
                            null
                        }
                    }
                    .distinctBy { it.packageName }
                    .sortedBy { it.label.lowercase() }
                    .toList()
            }

            allApps = apps
            isLoading = false
            renderApps()
        }
    }

    private fun renderApps() {
        val screen = preferenceScreen ?: return
        val ctx = context ?: return
        screen.removeAll()

        if (isLoading) {
            screen.addPreference(
                AppSelectorStatusPreference(
                    context = ctx,
                    loading = true,
                    message = ctx.getString(R.string.app_selector_loading),
                )
            )
            return
        }

        val trimmed = searchQuery.trim()
        val filtered = if (trimmed.isEmpty()) {
            allApps
        } else {
            allApps.filter {
                it.label.contains(trimmed, ignoreCase = true) ||
                    it.packageName.contains(trimmed, ignoreCase = true)
            }
        }

        if (filtered.isEmpty()) {
            val emptyMessage = if (trimmed.isEmpty()) {
                ctx.getString(R.string.app_selector_empty)
            } else {
                ctx.getString(R.string.app_selector_empty_search, trimmed)
            }
            screen.addPreference(
                AppSelectorStatusPreference(
                    context = ctx,
                    loading = false,
                    message = emptyMessage,
                )
            )
            return
        }

        filtered.forEach { app ->
            val pref = Preference(ctx).apply {
                key = app.packageName
                title = app.label
                summary = app.packageName
                icon = app.icon
                isPersistent = false
                setOnPreferenceClickListener {
                    requireActivity().setResult(
                        Activity.RESULT_OK,
                        Intent().apply {
                            putExtra(AppListPreferences.EXTRA_APP, app.packageName)
                        }
                    )
                    requireActivity().finish()
                    true
                }
            }
            screen.addPreference(pref)
        }
    }

    private data class AppEntry(
        val packageName: String,
        val label: String,
        val icon: Drawable?,
    )
}
