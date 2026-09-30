package globus.demo.tour

/** Shared with the iOS tour: an accelerated route preview, never a device GPS location. */
internal object DemoTourMotion {
    const val DURATION = 26.0
    const val SEARCH_START = 4.0
    const val PINS_START = 5.1
    const val SELECTION_START = 8.0
    const val OVERVIEW_START = 10.7
    const val ROUTE_START = 11.0
    const val REVEAL_START = 12.0
    const val REVEAL_END = 13.4
    const val FOLLOW_START = 14.2
    const val WALK_START = 15.5
    const val ARRIVAL = 21.3
    const val RETURN_START = 21.7
    const val FOLLOW_END = 23.0
    const val OUTRO_START = 23.1
    const val OUTRO_END = 24.3

    fun ease(value: Double): Double {
        val t = value.coerceIn(0.0, 1.0)
        return t * t * (3 - 2 * t)
    }

    fun walkFraction(time: Double) = ease((time - WALK_START) / (ARRIVAL - WALK_START))
    fun followWeight(time: Double) = ease((time - FOLLOW_START) / (WALK_START - FOLLOW_START)) *
        (1 - ease((time - RETURN_START) / (FOLLOW_END - RETURN_START)))
    fun outroOpacity(time: Double) = 1 - ease((time - OUTRO_START) / (OUTRO_END - OUTRO_START))

    /** Distances are cumulative and start at zero. Repeated vertices are allowed. */
    fun pointIndex(fraction: Double, distances: DoubleArray): Double {
        if (distances.size < 2 || distances.last() <= 0) return 0.0
        if (fraction <= 0) return 0.0
        if (fraction >= 1) return distances.lastIndex.toDouble()
        val target = fraction * distances.last()
        var lower = 1
        var upper = distances.lastIndex
        while (lower < upper) {
            val middle = (lower + upper) / 2
            if (distances[middle] < target) lower = middle + 1 else upper = middle
        }
        val length = distances[lower] - distances[lower - 1]
        val part = if (length > 0) (target - distances[lower - 1]) / length else 0.0
        return lower - 1 + part
    }

    fun angleDelta(start: Double, end: Double): Double {
        val delta = (end - start) % 360
        return when {
            delta > 180 -> delta - 360
            delta < -180 -> delta + 360
            else -> delta
        }
    }

    fun mixAngle(start: Double, end: Double, fraction: Double) = start + angleDelta(start, end) * fraction
}

/** Frame timestamps are monotonic; inactive time never advances the story. */
internal class DemoTourClock {
    private var lastFrame: Long? = null
    private var elapsed = 0.0

    fun frame(nanos: Long): Double {
        lastFrame?.let { elapsed += (nanos - it).coerceAtLeast(0) / 1_000_000_000.0 }
        lastFrame = nanos
        return elapsed % DemoTourMotion.DURATION
    }

    fun pause() {
        lastFrame = null
    }
}
