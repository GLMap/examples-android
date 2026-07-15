package globus.demo.vector

import globus.demo.base.MapDemoActivity
import globus.glmap.GLMapVectorCascadeStyle
import globus.glmap.GLMapVectorLayer
import globus.glmap.GLMapVectorObject
import globus.glmap.GLMapVectorObjectList
import globus.glmap.MapGeoPoint
import globus.glmap.MapPoint
import kotlin.concurrent.thread
import kotlin.math.cos
import kotlin.math.sin

class LinesPolygonsActivity : MapDemoActivity() {
    override fun onMapReady() {
        title = "Lines & Polygons"
        renderer.mapGeoCenter = MapGeoPoint(48.8566, 2.3522)
        renderer.mapZoom = 5.0

        addLine(
            arrayOf(
                point(51.5072, -0.1275), point(48.8566, 2.3522),
                point(46.2044, 6.1432), point(41.8933, 12.4829),
            ),
            "line{width:4pt;color:#E74C3C;}",
        )
        addLine(
            arrayOf(
                point(52.5037, 13.4102), point(50.0755, 14.4378),
                point(48.2082, 16.3738), point(47.4979, 19.0402),
            ),
            "line{width:4pt;color:#3498DB;}",
        )
        addLine(
            arrayOf(
                point(52.3690, 4.9021), point(50.8263, 4.3458),
                point(49.6072, 6.1296), point(48.8566, 2.3522),
            ),
            "line{width:3pt;color:#2ECC71;linecap:round;}",
        )

        val star = Array(11) { index ->
            val angle = index * Math.PI / 5 - Math.PI / 2
            val radius = if (index % 2 == 0) 3.0 else 1.2
            point(48.8566 + radius * sin(angle), 2.3522 + radius * cos(angle) / cos(Math.toRadians(48.8566)))
        }
        addPolygon(arrayOf(star), null, "area{fill-color:#F39C1230;width:2pt;color:#F39C12;}")

        val outer = hexagon(52.5037, 13.4102, 1.5)
        val inner = hexagon(52.5037, 13.4102, 0.6)
        addPolygon(arrayOf(outer), arrayOf(inner), "area{fill-color:#9B59B630;width:2pt;color:#9B59B6;}")
    }

    private fun addLine(points: Array<MapPoint>, css: String) {
        val layer = GLMapVectorLayer(3)
        layer.setVectorObject(GLMapVectorObject.createMultiline(arrayOf(points)), GLMapVectorCascadeStyle.createStyle(css)!!, null)
        renderer.add(layer)
    }

    private fun addPolygon(outer: Array<Array<MapPoint>>, inner: Array<Array<MapPoint>>?, css: String) {
        val layer = GLMapVectorLayer(2)
        layer.setVectorObject(GLMapVectorObject.createPolygon(outer, inner), GLMapVectorCascadeStyle.createStyle(css)!!, null)
        renderer.add(layer)
    }

    private fun hexagon(lat: Double, lon: Double, radius: Double) = Array(7) { index ->
        val angle = index * Math.PI / 3
        point(lat + radius * sin(angle), lon + radius * cos(angle) / cos(Math.toRadians(lat)))
    }

    private fun point(lat: Double, lon: Double) = MapPoint.CreateFromGeoCoordinates(lat, lon)
}

class GeoJSONActivity : MapDemoActivity() {
    private var objects: GLMapVectorObjectList? = null

    override fun onMapReady() {
        title = "Tap on any UK region"
        thread(name = "GLMap demo GeoJSON") {
            try {
                val loaded = assets.open("uk_postcodes.geojson").use(GLMapVectorObject::createFromGeoJSONStreamOrThrow)
                runOnUiThread {
                    if (isDestroyed) {
                        loaded.dispose()
                        return@runOnUiThread
                    }
                    objects = loaded
                    val layer = GLMapVectorLayer()
                    val style = GLMapVectorCascadeStyle.createStyle(
                        "area{fill-color:#3498DB40;width:1.5pt;color:#2C3E50;}",
                    )!!
                    layer.setVectorObjects(loaded, style, null)
                    renderer.add(layer)
                    fit(loaded.bBox)
                    setGestures(onTap = { touch ->
                        val point = renderer.convertDisplayToInternal(touch.x.toDouble(), touch.y.toDouble())
                        loaded.findNearPoint(loaded.size() - 1, 0, renderer, point, 10.0)?.use {
                            showError("Tapped: ${it.asGeoJSON()}")
                        }
                    })
                }
            } catch (error: Exception) {
                runOnUiThread {
                    if (!isDestroyed) showError(error.message ?: "Cannot load GeoJSON")
                }
            }
        }
    }

    override fun onDestroy() {
        objects?.dispose()
        super.onDestroy()
    }
}
