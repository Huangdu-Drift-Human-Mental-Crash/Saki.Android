package org.hdhmc.saki.build

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class SakiTranslationCoverageTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun `locale qualifier accepts locales and rejects other qualifiers`() {
        assertEquals("zh", SakiTranslationCoverageRules.localeQualifier("values-zh"))
        assertEquals("zh-rTW", SakiTranslationCoverageRules.localeQualifier("values-zh-rTW"))
        assertEquals("pt", SakiTranslationCoverageRules.localeQualifier("values-pt"))
        assertNull(SakiTranslationCoverageRules.localeQualifier("values"))
        assertNull(SakiTranslationCoverageRules.localeQualifier("values-night"))
        assertNull(SakiTranslationCoverageRules.localeQualifier("values-v24"))
        assertNull(SakiTranslationCoverageRules.localeQualifier("values-zh-rTW-night"))
    }

    @Test
    fun `resource name is a valid identifier`() {
        assertEquals("translation_coverage_zh", SakiTranslationCoverageRules.resourceName("zh"))
        assertEquals("translation_coverage_zh_rtw", SakiTranslationCoverageRules.resourceName("zh-rTW"))
        assertEquals(true, SakiTranslationCoverageRules.resourceName("zh-rTW").matches(Regex("[A-Za-z0-9_]+")))
    }

    @Test
    fun `untranslatable defaults are excluded and identical values still count`() {
        val defaultFile = write("values/strings.xml", DEFAULT_STRINGS)
        val localeFile = write("values-zh/strings.xml", CHINESE_STRINGS)

        val defaultNames = SakiTranslationCoverageRules.translatableNames(listOf(defaultFile))
        val localizedNames = SakiTranslationCoverageRules.localizedNames(listOf(localeFile))
        val translated = defaultNames.count { it in localizedNames }

        assertEquals(setOf("settings_title", "settings_wifi"), defaultNames)
        assertEquals(2, translated)
        assertEquals(100, SakiTranslationCoverageRules.percent(translated, defaultNames.size))
    }

    @Test
    fun `missing keys lower coverage while orphan locale keys are ignored`() {
        val defaultFile = write("values/strings.xml", DEFAULT_STRINGS)
        val localeFile = write(
            "values-zh/strings.xml",
            """
            <?xml version="1.0" encoding="utf-8"?>
            <resources>
                <string name="settings_title">设置</string>
                <string name="not_in_defaults">多余</string>
            </resources>
            """.trimIndent(),
        )

        val defaultNames = SakiTranslationCoverageRules.translatableNames(listOf(defaultFile))
        val localizedNames = SakiTranslationCoverageRules.localizedNames(listOf(localeFile))
        val translated = defaultNames.count { it in localizedNames }

        assertEquals(1, translated)
        assertEquals(2, defaultNames.size)
        assertEquals(50, SakiTranslationCoverageRules.percent(translated, defaultNames.size))
    }

    @Test
    fun `percent truncates and stays zero without defaults`() {
        assertEquals(66, SakiTranslationCoverageRules.percent(2, 3))
        assertEquals(0, SakiTranslationCoverageRules.percent(0, 0))
    }

    private fun write(relativePath: String, content: String): File {
        val file = File(temporaryFolder.root, relativePath)
        file.parentFile?.mkdirs()
        file.writeText(content)
        return file
    }

    private companion object {
        val DEFAULT_STRINGS = """
            <?xml version="1.0" encoding="utf-8"?>
            <resources>
                <string name="settings_title">Settings</string>
                <string name="settings_wifi">WiFi</string>
                <string name="app_name" translatable="false">Saki</string>
                <string name="stream_quality_96_kbps" translatable="false">96 kbps</string>
                <plurals name="song_count" translatable="false">
                    <item quantity="other">Songs</item>
                </plurals>
            </resources>
        """.trimIndent()

        val CHINESE_STRINGS = """
            <?xml version="1.0" encoding="utf-8"?>
            <resources>
                <string name="settings_title">设置</string>
                <string name="settings_wifi">WiFi</string>
                <string name="not_in_defaults">多余</string>
            </resources>
        """.trimIndent()
    }
}
