package globus.demo.search

import android.graphics.Color
import android.os.SystemClock
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.widget.ListView
import android.widget.TextView
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
                assertNotNull(
                    "Offline search switch is missing",
                    activity.findViewById<SwitchCompat>(android.R.id.checkbox)
                )
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
            scenario.onActivity {
                // Exercise getSpanned synchronously, rather than only checking the row count.
                // Non-Cloneable span prototypes cause a native crash in SDK 2.2.0.
                val row = list.adapter.getView(0, null, list)
                val title = row.findViewById<TextView>(android.R.id.text1).text
                assertTrue("Search title is not styled", title is Spanned)
                val spans = (title as Spanned).getSpans(0, title.length, ForegroundColorSpan::class.java)
                assertTrue("Search title has no normal color span", spans.any { it.foregroundColor == Color.BLACK })
            }
        }
    }
}
