package com.elara.clock

import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

class WorldTest {
    @Test fun sunOverheadAtJuneSolsticeNoon() {
        // 2026-06-21 12:00 UTC: the sun is over the Tropic of Cancer, near the Greenwich meridian.
        val (lat, lon) = subsolarPoint(Instant.parse("2026-06-21T12:00:00Z"))
        assertEquals(23.44, lat, 0.3)
        assertEquals(0.0, lon, 1.0)
    }

    @Test fun sunSouthInDecemberAndMovesWestOverTheDay() {
        val (lat, lon) = subsolarPoint(Instant.parse("2026-12-21T06:00:00Z"))
        assertEquals(-23.44, lat, 0.3)
        assertEquals(90.0, lon, 1.5) // Six hours before noon at Greenwich, noon is 90 degrees east.
    }

    @Test fun dayAndNight() {
        val sun = subsolarPoint(Instant.parse("2026-11-10T04:00:00Z")) // 09:45 in Kathmandu, 23:00 in New York.
        assertTrue(isDay(27.7, 85.3, sun))
        assertFalse(isDay(40.7, -74.0, sun))
    }

    @Test fun cityLineRoundTrip() {
        val city = City("New York", "US", "NYC", ZoneId.of("America/New_York"), 40.7128, -74.006)
        assertEquals(city, City.fromLine(city.toLine()))
        assertEquals(null, City.fromLine("Nowhere\tXX\tMars/Olympus\t0\t0\tNOW"))
    }

    @Test fun closeCitiesAllGetBubbles() {
        // Kathmandu, Delhi and Dubai as placed on the 380 dp map: a few dp apart.
        val spots = listOf(DpOffset(280.dp, 78.dp), DpOffset(271.dp, 76.dp), DpOffset(243.dp, 83.dp))
        val bubbles = placeBubbles(spots, 340.dp)
        bubbles.forEach { assertNotNull(it) }
        for (i in bubbles.indices) for (j in i + 1 until bubbles.size) {
            val a = bubbles[i]!!
            val b = bubbles[j]!!
            assertTrue("bubbles $i and $j overlap", kotlin.math.abs((a.x - b.x).value) >= 46 || kotlin.math.abs((a.y - b.y).value) >= 20)
        }
    }
}
