package globus.demo.map

import android.app.Activity
import android.widget.Button
import globus.demo.base.MapDemoActivity
import globus.glmap.GLMapBBox
import globus.glmap.GLMapFileStorage
import globus.glmap.GLMapInfo
import globus.glmap.GLMapRasterTileSource
import globus.glmap.GLMapStorageFile
import globus.glmap.GLMapStyleParser
import globus.glmap.GLMapVectorTileSource
import globus.glmap.MapGeoPoint
import globus.glmap.MapPoint

class OnlineMapActivity : MapDemoActivity() {
    private var rasterSource: OSMTileSource? = null

    override fun onMapReady() {
        title = "Online Map"
        renderer.mapGeoCenter = MapGeoPoint(46.5369, 12.1356)
        renderer.mapZoom = 13.0
        renderer.drawElevationLines = true
        renderer.drawHillshades = true

        val button = addButton("OSM Raster") {}
        button.setOnClickListener { toggleSource(button) }
        setGestures(onTap = { touch ->
            val point = MapGeoPoint(renderer.convertDisplayToInternal(touch.x.toDouble(), touch.y.toDouble()))
            showError("%.4f, %.4f".format(point.lat, point.lon))
        })
    }

    private fun toggleSource(button: Button) {
        if (rasterSource == null) {
            rasterSource = OSMTileSource(this)
            renderer.setBase(rasterSource!!)
            renderer.drawElevationLines = false
            renderer.drawHillshades = false
            button.text = "GLMap Vector"
        } else {
            renderer.setBase(GLMapVectorTileSource())
            renderer.drawElevationLines = true
            renderer.drawHillshades = true
            rasterSource = null
            button.text = "OSM Raster"
        }
    }
}

class DarkThemeActivity : MapDemoActivity() {
    private var dark = true

    override fun onMapReady() {
        title = "Dark Theme"
        renderer.mapGeoCenter = MapGeoPoint(45.4371, 12.3326)
        renderer.mapZoom = 14.0
        applyTheme()

        val button = addButton("Light") {}
        button.setOnClickListener {
            dark = !dark
            applyTheme()
            button.text = if (dark) "Light" else "Dark"
        }
    }

    private fun applyTheme() {
        val parser = GLMapStyleParser(assets, "DefaultStyle.bundle")
        parser.setOptions(if (dark) mapOf("Theme" to "Dark") else emptyMap(), true)
        val style = parser.parseFromResources() ?: return showError("Cannot parse default map style")
        renderer.setStyle(style)
        renderer.reloadTiles()
    }
}

class TerrainActivity : MapDemoActivity() {
    override fun onMapReady() {
        title = "3D Terrain"
        val bbox = GLMapBBox().apply {
            addPoint(MapPoint.CreateFromGeoCoordinates(45.85, 6.75))
            addPoint(MapPoint.CreateFromGeoCoordinates(46.05, 7.05))
        }
        fit(bbox)
        renderer.mapPitch = 45f
        renderer.altitudeScale = 1f
        renderer.drawHillshades = true
        renderer.drawElevationLines = true

        downloadBBoxData(
            bbox,
            listOf(
                GLMapInfo.DataSet.MAP to "terrain_map.vmtar",
                GLMapInfo.DataSet.ELEVATION to "terrain_ele.eletar",
            ),
        ) { error ->
            if (error != null) showError(error) else renderer.reloadTiles()
        }
    }
}

private class OSMTileSource(activity: Activity) : GLMapRasterTileSource(cacheStorage(activity)) {
    private val mirrors = arrayOf(
        "https://a.tile.openstreetmap.org/%d/%d/%d.png",
        "https://b.tile.openstreetmap.org/%d/%d/%d.png",
        "https://c.tile.openstreetmap.org/%d/%d/%d.png",
    )

    init {
        setValidZoomMask((1 shl 20) - 1)
        if (activity.resources.displayMetrics.density >= 2) tileSize = 192
        setAttributionText("© OpenStreetMap contributors")
    }

    override fun urlForTilePos(x: Int, y: Int, z: Int): String =
        mirrors[Math.floorMod(x + y, mirrors.size)].format(z, x, y)

    companion object {
        private fun cacheStorage(activity: Activity): GLMapStorageFile? =
            GLMapFileStorage(activity.filesDir).findStorage("RasterCache", true)?.findFile("osm.db", true)
    }
}
