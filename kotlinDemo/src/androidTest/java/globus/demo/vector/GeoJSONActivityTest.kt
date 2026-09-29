package globus.demo.vector

import android.os.SystemClock
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GeoJSONActivityTest {
    @Test
    fun vectorUpdateReportsReady() {
        ActivityScenario.launch(GeoJSONActivity::class.java).use { scenario ->
            assertReady(scenario)
            // Recreating the activity must load and prepare a fresh layer, too.
            scenario.recreate()
            assertReady(scenario)
        }
    }

    private fun assertReady(scenario: ActivityScenario<GeoJSONActivity>) {
        var title = ""
        val deadline = SystemClock.uptimeMillis() + 10_000
        do {
            scenario.onActivity { title = it.title.toString() }
            if (title == "Tap on any UK region" || title == "GeoJSON failed") break
            SystemClock.sleep(50)
        } while (SystemClock.uptimeMillis() < deadline)
        assertEquals("Vector update did not report Ready", "Tap on any UK region", title)
    }
}
