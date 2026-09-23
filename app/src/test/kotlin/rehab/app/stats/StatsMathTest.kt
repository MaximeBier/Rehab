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

    // ---- prepare : dédoublonnage + rognage/prorata aux bornes (fix round 1 puis 2, chevauchement UsageStatsManager) ----

    @Test fun prepareDedupesBucketsWithSameStart() {
        // Ex. Android renvoie deux fois le même bucket (même borne de début) : le second est un doublon.
        val a = UsageBucket(at(1, 10, 0), at(1, 12, 0), Duration.ofHours(2))
        val duplicate = UsageBucket(at(1, 10, 0), at(1, 12, 0), Duration.ofHours(2))
        val b = UsageBucket(at(1, 14, 0), at(1, 15, 0), Duration.ofHours(1))
        val prepared = StatsMath.prepare(listOf(a, duplicate, b), at(1), at(2))
        assertEquals(2, prepared.size)
        assertEquals(Duration.ofHours(3), prepared.fold(Duration.ZERO) { acc, x -> acc + x.duration })
    }

    @Test fun prepareClipsBucketPartiallyOutsideWindow() {
        // Bucket qui déborde des deux côtés de la fenêtre interrogée (48 h de portée, dont 24 h dans
        // la fenêtre, soit une portion conservée de 1/2) : les bornes sont rognées ET la durée est
        // répartie au prorata de la portion conservée (fix round 2 — garder la durée entière du
        // bucket alors que sa portée est réduite de moitié gonflerait la moyenne de x2).
        val bucket = UsageBucket(at(1, 12, 0), at(3, 12, 0), Duration.ofHours(4))
        val prepared = StatsMath.prepare(listOf(bucket), at(2, 0, 0), at(3, 0, 0))
        assertEquals(listOf(UsageBucket(at(2, 0, 0), at(3, 0, 0), Duration.ofHours(2))), prepared)
    }

    @Test fun prepareProratesBucketStraddlingInstallBoundary() {
        // Simule un bucket WEEKLY qui chevauche `installedAt`, la borne de fin de la fenêtre
        // « avant » (28 jours précédant l'installation) : bucket de 7 jours, l'installation tombe le
        // 4ᵉ jour — seuls 4/7 de la durée mesurée doivent compter dans « avant ».
        val bucketStart = at(1)
        val bucketEnd = at(8) // 7 jours, granularité hebdomadaire
        val installedAt = at(5) // 4 jours après le début du bucket
        val from = bucketStart.minus(Duration.ofDays(21)) // largement avant : ne rogne pas ce côté
        val prepared = StatsMath.prepare(listOf(UsageBucket(bucketStart, bucketEnd, Duration.ofHours(70))), from, installedAt)
        assertEquals(listOf(UsageBucket(bucketStart, installedAt, Duration.ofHours(40))), prepared)
    }

    @Test fun prepareDropsBucketFullyOutsideWindow() {
        val bucket = UsageBucket(at(1), at(2), Duration.ofHours(1))
        assertEquals(emptyList<UsageBucket>(), StatsMath.prepare(listOf(bucket), at(5), at(6)))
    }

    @Test fun prepareKeepsDurationWhenBucketFullyInsideWindow() {
        val bucket = UsageBucket(at(1, 10, 0), at(1, 12, 0), Duration.ofHours(2))
        assertEquals(listOf(bucket), StatsMath.prepare(listOf(bucket), at(1), at(2)))
    }
}
