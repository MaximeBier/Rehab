package rehab.app.stats

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime

class StatsMathTest {
    private val zone = ZoneId.of("Europe/Paris")
    private fun at(d: Int, h: Int = 0, m: Int = 0) = ZonedDateTime.of(2026, 9, d, h, m, 0, 0, zone).toInstant()

    // ---- averagePerDay : jours couverts ----

    @Test fun averagePerDayNormalSpan() {
        // 2 buckets couvrant du 1er au 5 (4 jours pleins), total 8 h -> 2 h/j.
        val buckets = listOf(
            UsageBucket(at(1), at(3), Duration.ofHours(5)),
            UsageBucket(at(3), at(5), Duration.ofHours(3)),
        )
        assertEquals(Duration.ofHours(2), StatsMath.averagePerDay(buckets, maxDays = 28))
    }

    @Test fun averagePerDayPartialBucket() {
        // Un seul bucket de 3 h, span < 1 jour -> dénominateur plancher à 1 jour.
        val buckets = listOf(UsageBucket(at(1, 10, 0), at(1, 13, 0), Duration.ofHours(3)))
        assertEquals(Duration.ofHours(3), StatsMath.averagePerDay(buckets, maxDays = 28))
    }

    @Test fun averagePerDayZeroBuckets() {
        assertNull(StatsMath.averagePerDay(emptyList(), maxDays = 28))
    }

    @Test fun averagePerDayCapsAtMaxDays() {
        // Span de 40 jours mais maxDays = 28 : le dénominateur est plafonné à 28.
        val buckets = listOf(UsageBucket(at(1), at(1).plus(Duration.ofDays(40)), Duration.ofHours(280)))
        assertEquals(Duration.ofHours(10), StatsMath.averagePerDay(buckets, maxDays = 28))
    }

    // ---- changePercent : hausse / baisse / inconnu ----

    @Test fun changePercentBaisse() {
        assertEquals(-67, StatsMath.changePercent(Duration.ofMinutes(60), Duration.ofMinutes(20)))
    }

    @Test fun changePercentHausse() {
        assertEquals(100, StatsMath.changePercent(Duration.ofMinutes(20), Duration.ofMinutes(40)))
    }

    @Test fun changePercentInconnuAvantNull() {
        assertNull(StatsMath.changePercent(null, Duration.ofMinutes(20)))
    }

    @Test fun changePercentInconnuAvantZero() {
        assertNull(StatsMath.changePercent(Duration.ZERO, Duration.ofMinutes(20)))
    }

    // ---- effectiveBefore : sélection manuel > Android ----

    @Test fun effectiveBeforePrefersManual() {
        assertEquals(Duration.ofMinutes(45), StatsMath.effectiveBefore(Duration.ofMinutes(45), Duration.ofMinutes(10)))
    }

    @Test fun effectiveBeforeFallsBackToAndroid() {
        assertEquals(Duration.ofMinutes(10), StatsMath.effectiveBefore(null, Duration.ofMinutes(10)))
    }

    @Test fun effectiveBeforeUnknownWhenBothNull() {
        assertNull(StatsMath.effectiveBefore(null, null))
    }
}
