package globus.demo

import android.app.AlertDialog
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageButton
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import globus.demo.tour.DemoTour
import globus.demo.tour.DemoTourOverlay
import globus.glmap.GLMapStyleParser
import globus.glmap.GLMapView

/** Automatic Amalfi walking tour, matching the iOS Demo Mode. */
class DemoModeActivity : AppCompatActivity() {
    private lateinit var mapView: GLMapView
    private lateinit var overlay: DemoTourOverlay
    private var tour: DemoTour? = null
    private var resumed = false
    private var destroyed = false
    private var surfaceReady = false
    private var pendingError: String? = null
    private var errorDialog: AlertDialog? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supportActionBar?.hide()
        WindowCompat.getInsetsController(window, window.decorView).hide(WindowInsetsCompat.Type.statusBars())
        mapView = GLMapView(this)
        overlay = DemoTourOverlay(this).apply {
            isClickable = true
            setOnClickListener { finish() }
        }
        val close = ImageButton(this).apply {
            setImageResource(android.R.drawable.ic_menu_close_clear_cancel)
            setColorFilter(Color.DKGRAY)
            setBackgroundColor(Color.TRANSPARENT)
            contentDescription = "Close demo"
            setOnClickListener { finish() }
        }
        val controls = FrameLayout(this).apply {
            addView(
                close,
                FrameLayout.LayoutParams(dp(44), dp(44), Gravity.BOTTOM or Gravity.START).apply {
                    setMargins(dp(14), 0, 0, dp(8))
                }
            )
        }
        ViewCompat.setOnApplyWindowInsetsListener(controls) { view, insets ->
            val safe = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            view.setPadding(safe.left, safe.top, safe.right, safe.bottom)
            insets
        }
        setContentView(
            FrameLayout(this).apply {
                addView(mapView)
                addView(this@DemoModeActivity.overlay)
                addView(controls)
            }
        )
        GLMapStyleParser(assets, "DefaultStyle.bundle").use { parser ->
            parser.setOptions(emptyMap(), true)
            val style = parser.parseFromResources()
            if (style == null) {
                pendingError = "Cannot load the default map style."
            } else {
                style.use { mapView.renderer.setStyle(it) }
            }
        }
        mapView.renderer.doWhenSurfaceCreated {
            runOnUiThread {
                if (destroyed) return@runOnUiThread
                surfaceReady = true
                startIfReady()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        resumed = true
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        mapView.renderer.setRenderingEnabled(true)
        startIfReady()
        tour?.resume()
        presentError()
    }

    override fun onPause() {
        resumed = false
        tour?.pause()
        mapView.renderer.setRenderingEnabled(false)
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        super.onPause()
    }

    private fun startIfReady() {
        if (!resumed || !surfaceReady || destroyed || tour != null || pendingError != null) return
        tour = DemoTour(mapView.renderer, overlay) { message ->
            pendingError = message
            presentError()
        }.also {
            it.start()
            it.resume()
        }
    }

    private fun presentError() {
        val message = pendingError ?: return
        if (!resumed || destroyed || isFinishing || errorDialog != null) return
        errorDialog = AlertDialog.Builder(this)
            .setTitle("Demo unavailable")
            .setMessage(message)
            .setPositiveButton("Retry") { _, _ -> recreate() }
            .setNegativeButton("Close") { _, _ -> finish() }
            .setOnCancelListener { finish() }
            .show()
    }

    override fun onDestroy() {
        destroyed = true
        errorDialog?.dismiss()
        tour?.close()
        tour = null
        mapView.dispose()
        super.onDestroy()
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
}
