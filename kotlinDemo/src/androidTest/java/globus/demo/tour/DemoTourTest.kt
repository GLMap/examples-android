package globus.demo.tour

import android.graphics.Color
import android.view.View
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import globus.glmap.GLMapDrawable
import globus.glmap.GLMapTrackData
import globus.glmap.GLMapView
import globus.glmap.MapPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DemoTourTest {
    @Test fun nativeWalkerFollowsTheRouteAndResetsAtNextLoop() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync {
            val map = GLMapView(instrumentation.targetContext)
            val overlay = DemoTourOverlay(instrumentation.targetContext)
            val tour = DemoTour(map.renderer, overlay) { throw AssertionError(it) }
            // Synthetic geometry is confined to this test; the app always uses real service results.
            val points = listOf(
                MapPoint.CreateFromGeoCoordinates(40.63318, 14.60257),
                MapPoint.CreateFromGeoCoordinates(40.634, 14.605),
                MapPoint.CreateFromGeoCoordinates(40.63602, 14.60980)
            )
            fun data(color: Int) = GLMapTrackData({ index, point ->
                GLMapTrackData.setPointData(point, points[index].x.toInt(), points[index].y.toInt(), color)
            }, points.size)
            try {
                data(DemoTourArtwork.ACCENT).use { track ->
                    data(Color.WHITE).use { casing ->
                        tour.installRoute(
                            points,
                            track,
                            casing,
                            listOf(TourPlace(points.last(), "Test restaurant")),
                            "10 min · 600 m"
                        )
                    }
                }
                val marker = requireNotNull(tour.userDot)
                assertEquals(GLMapDrawable.DrawTarget.Unbound, marker.drawTarget)
                tour.render(DemoTourMotion.WALK_START)
                assertFalse(marker.isHidden)
                assertEquals(points.first().x, marker.position.x, 0.01)
                assertEquals(points.first().y, marker.position.y, 0.01)
                tour.render(DemoTourMotion.ARRIVAL)
                assertEquals(points.last().x, marker.position.x, 0.01)
                assertEquals(points.last().y, marker.position.y, 0.01)
                tour.render(DemoTourMotion.OUTRO_END)
                assertTrue(marker.isHidden)
                tour.render(0.0)
                assertTrue(marker.isHidden)
                assertEquals(points.first().x, marker.position.x, 0.01)
            } finally {
                tour.close()
                assertNull(tour.userDot)
                map.dispose()
            }
        }
    }

    @Test fun captionsFitPortraitAndLandscape() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync {
            val overlay = DemoTourOverlay(instrumentation.targetContext)
            val density = overlay.resources.displayMetrics.density
            for ((widthDp, heightDp) in listOf(390 to 844, 844 to 390)) {
                val width = (widthDp * density).toInt()
                val height = (heightDp * density).toInt()
                overlay.measure(
                    View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY)
                )
                overlay.layout(0, 0, width, height)
                overlay.showPlace("Restaurant", "12 min · 600 m")
                for (time in listOf(0.0, 6.0, 9.0, 13.8, 17.0, 22.0, 25.0)) {
                    overlay.update(time)
                    for (index in 0 until overlay.childCount) {
                        val child = overlay.getChildAt(index)
                        assertTrue(child.left >= 0 && child.right <= width)
                        assertTrue(child.top >= 0 && child.bottom <= height)
                    }
                }
            }
        }
    }
}
