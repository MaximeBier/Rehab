package rehab.app.ui.components

import org.junit.Assert.assertEquals
import org.junit.Test
import rehab.app.ui.theme.ChivoMono

class MonoNumbersTest {
    @Test fun spansCoverNumberRuns() {
        val s = withMonoNumbers("ancien record 23 · jokers 2/2")
        assertEquals("ancien record 23 · jokers 2/2", s.text)
        val ranges = s.spanStyles.map { s.text.substring(it.start, it.end) }
        assertEquals(listOf("23", "2/2"), ranges)
        assertEquals(ChivoMono, s.spanStyles.first().item.fontFamily)
    }

    @Test fun timesAndArrowsStayTogether() {
        val s = withMonoNumbers("23:00 → 07:30")
        assertEquals(listOf("23:00", "07:30"), s.spanStyles.map { s.text.substring(it.start, it.end) })
    }

    @Test fun noNumberNoSpan() {
        assertEquals(0, withMonoNumbers("Libre").spanStyles.size)
    }
}
