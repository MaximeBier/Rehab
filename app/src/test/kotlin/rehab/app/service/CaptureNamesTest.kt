package rehab.app.service

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

class CaptureNamesTest {
    private val zone = ZoneId.of("Europe/Paris")
    private val t = ZonedDateTime.of(2026, 9, 20, 21, 31, 5, 0, zone).toInstant().toEpochMilli()

    @Test fun names() {
        assertEquals("ig_reels_2026-09-20T21-31-05.json", CaptureNames.fileName("com.instagram.android", "instagram.reels", t, zone))
        assertEquals("ig_feed_2026-09-20T21-31-05.json", CaptureNames.fileName("com.instagram.android", "instagram.home", t, zone))
        assertEquals("x_home_2026-09-20T21-31-05.json", CaptureNames.fileName("com.twitter.android", "twitter.home", t, zone))
        assertEquals("ig_inconnu_2026-09-20T21-31-05.json", CaptureNames.fileName("com.instagram.android", null, t, zone))
    }
}
