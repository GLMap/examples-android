package globus.demo.draw

import android.Manifest
import android.location.Location
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.GrantPermissionRule
import globus.glmap.GLMapView
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LocationLifecycleTest {
    @get:Rule
    val permission: GrantPermissionRule = GrantPermissionRule.grant(
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION
    )

    @Test fun locationSubscriptionStopsAndRestartsWithActivity() {
        ActivityScenario.launch(UserLocationActivity::class.java).use { scenario ->
            scenario.onActivity { assertTrue(it.locationUpdatesRequested) }
            scenario.moveToState(Lifecycle.State.CREATED)
            scenario.onActivity { assertFalse(it.locationUpdatesRequested) }
            scenario.moveToState(Lifecycle.State.RESUMED)
            scenario.onActivity { assertTrue(it.locationUpdatesRequested) }
        }
    }

    @Test fun gpsCameraUsesClockwiseDegrees() {
        ActivityScenario.launch(GPSTrackActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                // Invoke the protected hook directly, without depending on a real GPS fix.
                val update = GPSTrackActivity::class.java.getDeclaredMethod(
                    "updateCameraForLocation",
                    Location::class.java
                )
                update.isAccessible = true
                update.invoke(activity, Location("test").apply { bearing = 90f })
                val map = findMap(activity.findViewById(android.R.id.content))!!
                assertEquals(90f, map.renderer.mapAngle, 0.001f)
            }
        }
    }

    private fun findMap(view: View): GLMapView? {
        if (view is GLMapView) return view
        if (view is ViewGroup) {
            for (index in 0 until view.childCount) findMap(view.getChildAt(index))?.let { return it }
        }
        return null
    }
}
