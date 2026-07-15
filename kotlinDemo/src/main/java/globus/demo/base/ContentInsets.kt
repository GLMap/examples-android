package globus.demo.base

import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

fun AppCompatActivity.applyContentInsets(view: View) {
    ViewCompat.setOnApplyWindowInsetsListener(view) { content, insets ->
        val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
        content.setPadding(bars.left, bars.top, bars.right, bars.bottom)
        insets
    }
}
