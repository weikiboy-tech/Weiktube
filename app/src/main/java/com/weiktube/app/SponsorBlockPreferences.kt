package com.weiktube.app

import androidx.datastore.preferences.core.*

data class SponsorBlockPreferences(
    val enabled: Boolean = false, val markers: Boolean = true, val notices: Boolean = true,
    val showStatistics: Boolean = true,
    val modes: Map<String, SponsorMode> = SponsorCategory.entries.associate { it.key to it.defaultMode },
) {
    companion object {
        fun read(p: androidx.datastore.preferences.core.Preferences) = SponsorBlockPreferences(
            p[booleanPreferencesKey("sponsorEnabled")] ?: false,
            p[booleanPreferencesKey("sponsorMarkers")] ?: true,
            p[booleanPreferencesKey("sponsorNotices")] ?: true,
            p[booleanPreferencesKey("sponsorStatistics")] ?: true,
            SponsorCategory.entries.associate { category -> category.key to
                (p[stringPreferencesKey("sponsorMode_${category.key}")]?.let { value -> SponsorMode.entries.firstOrNull { it.name == value } } ?: category.defaultMode) })
    }
}
