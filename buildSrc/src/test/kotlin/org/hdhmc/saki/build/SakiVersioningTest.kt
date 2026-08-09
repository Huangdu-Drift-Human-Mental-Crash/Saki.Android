package org.hdhmc.saki.build

import org.junit.Assert.assertEquals
import org.junit.Test

class SakiVersioningTest {
    @Test
    fun `master version follows mainline distance`() {
        val version = SakiVersionRules.calculate(
            baseVersion = "v0.1.0",
            mainlineDistance = 2,
            mainlineVersionCode = 190,
            branch = "master",
            commit = "ABCDEF0123456789",
            isMainBranch = true,
            dirty = false,
        )

        assertEquals("0.1.2-master-abcdef0", version.versionName)
        assertEquals(190, version.versionCode)
    }

    @Test
    fun `branch uses the next patch at its fork point`() {
        val version = SakiVersionRules.calculate(
            baseVersion = "0.1.0",
            mainlineDistance = 0,
            mainlineVersionCode = 188,
            branch = "codex/telegram_apk delivery",
            commit = "a2f212be76a816c9",
            isMainBranch = false,
            dirty = true,
        )

        assertEquals(
            "0.1.1-codex-telegram-apk-delivery-a2f212b-dirty",
            version.versionName,
        )
        assertEquals(189, version.versionCode)
    }

    @Test
    fun `long branch slug is safe and bounded`() {
        val version = SakiVersionRules.calculate(
            baseVersion = "1.2.3",
            mainlineDistance = 4,
            mainlineVersionCode = 500,
            branch = "feature/this-is-a-very-long-branch-name-with-symbols!@#and-more-text",
            commit = "1234567890abcdef",
            isMainBranch = false,
            dirty = false,
        )
        val branchSlug = version.versionName.removePrefix("1.2.8-").removeSuffix("-1234567")

        assertEquals(48, branchSlug.length)
        assertEquals(true, branchSlug.matches(Regex("[A-Za-z0-9-]+")))
    }

    @Test
    fun `nearest version tag ignores prerelease and unrelated tags`() {
        val tag = SakiVersionRules.nearestVersionTag(
            firstParentCommits = listOf("cccc", "bbbb", "aaaa"),
            tags = listOf(
                GitTagRef("v0.3.0-rc1", "cccc", ""),
                GitTagRef("v9.0.0", "dddd", ""),
                GitTagRef("v0.2.0", "tag-object", "bbbb"),
                GitTagRef("v0.1.0", "aaaa", ""),
            ),
        )

        assertEquals("v0.2.0", tag)
    }
}
