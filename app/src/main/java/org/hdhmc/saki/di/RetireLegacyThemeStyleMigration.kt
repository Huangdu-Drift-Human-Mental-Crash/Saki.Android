package org.hdhmc.saki.di

import androidx.datastore.core.DataMigration
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.stringPreferencesKey

internal val LEGACY_THEME_STYLE_KEY = stringPreferencesKey("theme_style")

/** Removes the obsolete dual-theme preference after Material 3 Expressive becomes the sole theme. */
class RetireLegacyThemeStyleMigration : DataMigration<Preferences> {
    override suspend fun shouldMigrate(currentData: Preferences): Boolean =
        currentData[LEGACY_THEME_STYLE_KEY] != null

    override suspend fun migrate(currentData: Preferences): Preferences =
        currentData.toMutablePreferences().apply {
            remove(LEGACY_THEME_STYLE_KEY)
        }.toPreferences()

    override suspend fun cleanUp() = Unit
}
