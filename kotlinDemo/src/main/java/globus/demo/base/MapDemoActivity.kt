package globus.demo.base

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.os.Bundle
import android.view.GestureDetector
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import globus.glmap.GLMapBBox
import globus.glmap.GLMapError
import globus.glmap.GLMapManager
import globus.glmap.GLMapView
import globus.glmap.GLMapViewRenderer
import globus.glmap.MapPoint
import java.io.File
import java.util.concurrent.ConcurrentHashMap

abstract class MapDemoActivity : AppCompatActivity() {
    protected lateinit var mapView: GLMapView
    protected val renderer: GLMapViewRenderer get() = mapView.renderer
    protected lateinit var container: FrameLayout
    private val visibleMapInsets = Rect()
    private val downloadTaskIDs = ConcurrentHashMap<Long, Unit>()
    private var active = true

    final override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        mapView = GLMapView(this)
        setVisibleMapInsets(dp(16), dp(16), dp(16), dp(16))
        container = FrameLayout(this).apply { addView(mapView) }
        setContentView(container)
        applyContentInsets(container)
        onMapReady()
    }

    protected abstract fun onMapReady()

    protected fun addButton(text: String, onClick: () -> Unit): Button {
        setVisibleMapInsets(dp(16), dp(16), dp(16), dp(72))
        val button = Button(this).apply {
            this.text = text
            setOnClickListener { onClick() }
        }
        container.addView(
            button,
            FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT).apply {
                gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
                bottomMargin = dp(12)
            },
        )
        return button
    }

    protected fun createBalloonBackground(): Bitmap =
        Bitmap.createBitmap(dp(180), dp(64), Bitmap.Config.ARGB_8888).also {
            Canvas(it).drawRoundRect(
                0f,
                0f,
                it.width.toFloat(),
                it.height.toFloat(),
                dp(12).toFloat(),
                dp(12).toFloat(),
                Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE },
            )
        }

    protected fun addTopView(view: View) {
        container.addView(
            view,
            FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT).apply {
                gravity = Gravity.TOP
                setMargins(dp(12), dp(12), dp(12), 0)
            },
        )
    }

    protected fun setGestures(onTap: ((MapPointTouch) -> Unit)? = null, onLongPress: ((MapPointTouch) -> Unit)? = null) {
        val detector = GestureDetector(
            this,
            object : GestureDetector.SimpleOnGestureListener() {
                override fun onDown(event: MotionEvent) = true
                override fun onSingleTapConfirmed(event: MotionEvent): Boolean {
                    onTap?.invoke(MapPointTouch(event.x, event.y))
                    return onTap != null
                }
                override fun onLongPress(event: MotionEvent) {
                    onLongPress?.invoke(MapPointTouch(event.x, event.y))
                }
            },
        )
        mapView.setOnTouchListener { _: View, event: MotionEvent ->
            detector.onTouchEvent(event)
            false
        }
    }

    protected fun fit(bbox: GLMapBBox) {
        renderer.doWhenSurfaceCreated {
            val zoom = renderer.mapZoomForBBox(bbox)
            renderer.mapZoom = if (zoom.isFinite()) zoom else 15.0
            centerMapOn(bbox.center())
        }
    }

    protected fun setVisibleMapInsets(left: Int, top: Int, right: Int, bottom: Int) {
        visibleMapInsets.set(left, top, right, bottom)
        mapView.setVisibleMapInsets(left, top, right, bottom)
    }

    protected fun centerMapOn(point: MapPoint) {
        // Insets affect fitted zoom but intentionally do not move the camera. Apply the offset
        // explicitly so the requested point lands in the center of the unobscured map area.
        val origin = renderer.mapOrigin
        val offset = renderer.convertDisplayDeltaToInternal(
            (visibleMapInsets.right - visibleMapInsets.left) * 0.5 + mapView.width * (0.5 - origin.x),
            (visibleMapInsets.bottom - visibleMapInsets.top) * 0.5 + mapView.height * (0.5 - origin.y),
        )
        renderer.mapCenter = MapPoint(point).add(offset)
    }

    protected fun downloadBBoxData(
        bbox: GLMapBBox,
        files: List<Pair<Int, String>>,
        completion: (String?) -> Unit,
    ) {
        if (files.isEmpty()) {
            completion(null)
            return
        }
        var remaining = files.size
        var firstError: String? = null
        val stateLock = Any()

        fun finished(error: String? = null) {
            val (complete, result) = synchronized(stateLock) {
                if (firstError == null) firstError = error
                remaining--
                (remaining == 0) to firstError
            }
            if (!complete) return
            runOnUiThread {
                if (active) completion(result)
            }
        }

        files.forEach { (dataSet, filename) ->
            val file = File(cacheDir, filename)
            if (file.exists()) {
                if (!GLMapManager.AddDataSet(dataSet, bbox, file.absolutePath, null, null)) {
                    file.delete()
                    finished("Cannot open ${file.name}")
                } else {
                    finished()
                }
            } else {
                var taskID = 0L
                taskID = GLMapManager.DownloadDataSet(dataSet, file.absolutePath, bbox, object : GLMapManager.DownloadCallback {
                    override fun onProgress(totalSize: Long, downloadedSize: Long, downloadSpeed: Double) = Unit
                    override fun onFinished(error: GLMapError?) {
                        downloadTaskIDs.remove(taskID)
                        if (error != null) {
                            file.delete()
                            finished(error.toString())
                        } else if (!GLMapManager.AddDataSet(dataSet, bbox, file.absolutePath, null, null)) {
                            file.delete()
                            finished("Cannot open ${file.name}")
                        } else {
                            finished()
                        }
                    }
                })
                if (taskID == 0L) {
                    finished("Cannot start download for ${file.name}")
                } else {
                    downloadTaskIDs[taskID] = Unit
                }
            }
        }
    }

    protected fun showError(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }

    final override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    override fun onDestroy() {
        active = false
        downloadTaskIDs.keys.forEach(GLMapManager::CancelDownloadTask)
        downloadTaskIDs.clear()
        mapView.dispose()
        super.onDestroy()
    }

    protected fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
}

data class MapPointTouch(val x: Float, val y: Float)
