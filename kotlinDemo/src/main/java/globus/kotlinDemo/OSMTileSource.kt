package globus.kotlinDemo

import android.app.Activity
import android.util.DisplayMetrics
import android.util.Log
import globus.glmap.GLMapRasterTileSource
import java.io.File

/** Created by destman on 11/11/15.  */
internal class OSMTileSource(activity: Activity) : GLMapRasterTileSource(cachePath(activity)) {
    private val mirrors: Array<String> = arrayOf(
        "https://a.tile.openstreetmap.org/%d/%d/%d.png",
        "https://b.tile.openstreetmap.org/%d/%d/%d.png",
        "https://c.tile.openstreetmap.org/%d/%d/%d.png"
    )

    init {
        setValidZoomMask((1 shl 20) - 1) // Set as valid zooms all levels from 0 to 19
        val metrics = DisplayMetrics()
        activity.windowManager.defaultDisplay.getMetrics(metrics)
        // For devices with high screen density we can make tile size a bit bigger.
        if (metrics.scaledDensity >= 2) {
            setTileSize(192)
        }
        setAttributionText("© OpenStreetMap contributors")
    }

    override fun urlForTilePos(x: Int, y: Int, z: Int): String? {
        val mirror = mirrors[(Math.random() * mirrors.size).toInt()]
        val rv = String.format(mirror, z, x, y)
        Log.i("OSMTileSource", rv)
        return rv
    }

    companion object {
        private fun cachePath(activity: Activity): String {
            val filesDir = File(activity.filesDir.absolutePath, "RasterCache")
            filesDir.mkdir()
            return File(filesDir.absolutePath, "osm.db").absolutePath
        }
    }
}
