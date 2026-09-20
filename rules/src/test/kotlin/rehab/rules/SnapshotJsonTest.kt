package rehab.rules

import kotlin.test.Test
import kotlin.test.assertEquals

class SnapshotJsonTest {
    @Test fun `aller retour JSON`() {
        val s = Snapshot("com.instagram.android", "352.0", 1000L, listOf(
            Node(id = "com.instagram.android:id/tab_bar", className = "android.widget.LinearLayout", bounds = Bounds(0, 2200, 1080, 2340)),
            Node(text = "Suggestions pour vous", bounds = Bounds(0, 100, 1080, 160), depth = 3),
        ))
        assertEquals(s, SnapshotJson.decode(SnapshotJson.encode(s)))
    }
}
