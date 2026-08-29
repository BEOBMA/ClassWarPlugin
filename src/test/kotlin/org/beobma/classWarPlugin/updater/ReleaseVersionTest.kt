package org.beobma.classWarPlugin.updater

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ReleaseVersionTest {
    @Test
    fun `accepts a leading v and compares numeric components`() {
        val installed = assertNotNull(ReleaseVersion.parse("1.9.9"))
        val release = assertNotNull(ReleaseVersion.parse("v1.10.0"))

        assertTrue(installed < release)
    }

    @Test
    fun `stable release is newer than a prerelease`() {
        val prerelease = assertNotNull(ReleaseVersion.parse("1.0.1-rc.2"))
        val release = assertNotNull(ReleaseVersion.parse("1.0.1"))

        assertTrue(prerelease < release)
    }

    @Test
    fun `numeric prerelease identifiers use numeric ordering`() {
        val second = assertNotNull(ReleaseVersion.parse("1.0.1-rc.2"))
        val tenth = assertNotNull(ReleaseVersion.parse("1.0.1-rc.10"))

        assertTrue(second < tenth)
    }

    @Test
    fun `trailing zeros and build metadata do not change precedence`() {
        val short = assertNotNull(ReleaseVersion.parse("v1.0+build.1"))
        val long = assertNotNull(ReleaseVersion.parse("1.0.0+build.99"))

        assertEquals(0, short.compareTo(long))
    }

    @Test
    fun `rejects non version tags`() {
        assertNull(ReleaseVersion.parse("latest"))
    }
}
