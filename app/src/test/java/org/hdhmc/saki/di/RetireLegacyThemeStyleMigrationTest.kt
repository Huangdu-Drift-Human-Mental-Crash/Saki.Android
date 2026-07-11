package org.hdhmc.saki.di

import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RetireLegacyThemeStyleMigrationTest {
    private val migration = RetireLegacyThemeStyleMigration()

    @Test
    fun migrateRemovesLegacyThemeStyleAndKeepsOtherPreferences() = runTest {
        val otherKey = stringPreferencesKey("other_setting")
        val current = emptyPreferences().toMutablePreferences().apply {
            this[LEGACY_THEME_STYLE_KEY] = "saki"
            this[otherKey] = "preserved"
        }.toPreferences()

        assertTrue(migration.shouldMigrate(current))

        val migrated = migration.migrate(current)
        assertNull(migrated[LEGACY_THEME_STYLE_KEY])
        assertEquals("preserved", migrated[otherKey])
    }

    @Test
    fun shouldMigrateAcceptsMaterialExpressiveLegacyValue() = runTest {
        val current = emptyPreferences().toMutablePreferences().apply {
            this[LEGACY_THEME_STYLE_KEY] = "material_expressive"
        }.toPreferences()

        assertTrue(migration.shouldMigrate(current))
        assertNull(migration.migrate(current)[LEGACY_THEME_STYLE_KEY])
    }

    @Test
    fun shouldMigrateSkipsPreferencesWithoutLegacyThemeStyle() = runTest {
        val current = emptyPreferences()

        assertFalse(migration.shouldMigrate(current))
    }
}
