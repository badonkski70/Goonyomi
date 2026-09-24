package eu.kanade.tachiyomi.util.storage

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class DiskUtilTest {

    @Test
    fun `buildValidFilename replaces non ascii with lowercase utf8 hex when disallowed`() {
        assertEquals(
            "cafc3a9",
            DiskUtil.buildValidFilename("café", maxBytes = 240, disallowNonAscii = true),
        )
    }

    @Test
    fun `buildValidFilename keeps non ascii by default`() {
        assertEquals("café", DiskUtil.buildValidFilename("café"))
    }

    @Test
    fun `buildValidFilename replaces invalid fat chars`() {
        assertEquals("a_b_c", DiskUtil.buildValidFilename("a/b\\c"))
    }

    @Test
    fun `truncateToLength keeps valid utf8 encoding`() {
        // 4 bytes, truncated to 3 bytes: must not split the 2-byte é
        assertEquals("caf", DiskUtil.truncateToLength("café", 3))
    }
}