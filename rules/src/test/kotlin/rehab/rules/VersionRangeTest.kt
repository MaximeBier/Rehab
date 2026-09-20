package rehab.rules

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class VersionRangeTest {
    private val r = VersionRange("340.0", "360.0")

    @Test fun `bornes`() {
        assertTrue(r.contains("340.0.0.12"))
        assertTrue(r.contains("359.9"))
        assertFalse(r.contains("360.0"))
        assertFalse(r.contains("339.9"))
    }

    @Test fun `segments non numeriques valent 0`() {
        assertTrue(r.contains("345.0.0.beta"))
        assertTrue(VersionRange("10.1", "11").contains("10.10.0-rc1"))
    }
}
