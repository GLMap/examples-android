package globus.demo.tour

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import globus.demo.tour.DemoTourMotion as M

class DemoTourMotionTest {
    @Test fun timelineMatchesIOS() {
        assertEquals(26.0, M.DURATION, 0.0)
        assertEquals(0.0, M.walkFraction(M.WALK_START), 0.0)
        assertEquals(1.0, M.walkFraction(M.ARRIVAL), 0.0)
        assertEquals(1.0, M.walkFraction(M.DURATION - 0.001), 0.0)
        assertEquals(0.0, M.followWeight(M.FOLLOW_START), 0.0)
        assertEquals(1.0, M.followWeight(M.ARRIVAL), 0.0)
        assertEquals(0.0, M.followWeight(M.FOLLOW_END), 0.0)
        assertEquals(1.0, M.outroOpacity(M.ARRIVAL), 0.0)
        assertEquals(0.0, M.outroOpacity(M.OUTRO_END), 0.0)
    }

    @Test fun progressUsesDistanceNotVertexCount() {
        assertEquals(1 + 40.0 / 90, M.pointIndex(0.5, doubleArrayOf(0.0, 10.0, 100.0)), 1e-12)
        assertEquals(0.0, M.pointIndex(-1.0, doubleArrayOf(0.0, 10.0)), 0.0)
        assertEquals(1.0, M.pointIndex(2.0, doubleArrayOf(0.0, 10.0)), 0.0)
        assertEquals(0.0, M.pointIndex(0.5, doubleArrayOf()), 0.0)
        assertEquals(0.0, M.pointIndex(0.5, doubleArrayOf(0.0, 0.0)), 0.0)
        assertEquals(2.0, M.pointIndex(0.5, doubleArrayOf(0.0, 0.0, 10.0, 10.0, 20.0)), 0.0)
    }

    @Test fun everyFrameKeepsMarkerAndTrackAtTheSameDistance() {
        val distances = doubleArrayOf(0.0, 0.0, 2.0, 9.0, 9.0, 50.0, 100.0, 100.0)
        var previous = 0.0
        repeat(3120) { frame ->
            if (frame % 1560 == 0) previous = 0.0
            val time = (frame % 1560) / 60.0
            val fraction = M.walkFraction(time)
            val index = M.pointIndex(fraction, distances)
            assertTrue(index >= previous && index <= distances.lastIndex)
            assertTrue(M.followWeight(time) in 0.0..1.0)
            assertTrue(M.outroOpacity(time) in 0.0..1.0)
            val segment = index.toInt().coerceAtMost(distances.size - 2)
            val travelled = distances[segment] + (distances[segment + 1] - distances[segment]) * (index - segment)
            assertEquals(fraction * 100, travelled, 1e-9)
            previous = index
        }
    }

    @Test fun headingTakesShortestPathAcrossNorth() {
        assertEquals(2.0, M.angleDelta(359.0, 1.0), 0.0)
        assertEquals(-2.0, M.angleDelta(1.0, 359.0), 0.0)
        assertEquals(-180.0, M.mixAngle(-179.0, 179.0, 0.5), 0.0)
    }

    @Test fun backgroundTimeDoesNotAdvanceStory() {
        val clock = DemoTourClock()
        assertEquals(0.0, clock.frame(1_000_000_000), 0.0)
        assertEquals(2.0, clock.frame(3_000_000_000), 0.0)
        clock.pause()
        assertEquals(2.0, clock.frame(90_000_000_000), 0.0)
        assertEquals(3.0, clock.frame(91_000_000_000), 0.0)
    }

    @Test fun clockLoopsWithoutRestartingServices() {
        val clock = DemoTourClock()
        clock.frame(0)
        assertEquals(25.0, clock.frame(25_000_000_000), 0.0)
        assertEquals(0.0, clock.frame(26_000_000_000), 0.0)
        assertEquals(1.0, clock.frame(27_000_000_000), 0.0)
    }
}
