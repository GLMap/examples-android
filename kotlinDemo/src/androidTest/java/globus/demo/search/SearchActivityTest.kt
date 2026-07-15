package globus.demo.search

import android.os.SystemClock
import android.widget.ListView
import androidx.appcompat.widget.SwitchCompat
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SearchActivityTest {
    @Test
    fun testOfflineSearchShowsRows() {
        ActivityScenario.launch(SearchActivity::class.java).use { scenario ->
            lateinit var list: ListView
            scenario.onActivity { activity ->
                assertNotNull("Search results list is missing", activity.findViewById<ListView>(android.R.id.list))
                assertNotNull("Offline search switch is missing", activity.findViewById<SwitchCompat>(android.R.id.checkbox))
                list = activity.findViewById(android.R.id.list)
                activity.findViewById<SwitchCompat>(android.R.id.checkbox).isChecked = true
            }

            var count = 0
            val deadline = SystemClock.uptimeMillis() + 2_000
            do {
                scenario.onActivity { count = list.adapter?.count ?: 0 }
                if (count > 0) break
                SystemClock.sleep(20)
            } while (SystemClock.uptimeMillis() < deadline)
            assertTrue("Offline search did not show result rows", count > 0)
        }
    }
}
