package globus.demo.vector

import globus.demo.base.MapDemoActivity
import globus.glmap.GLMapVectorCascadeStyle
import globus.glmap.GLMapVectorLayer
import globus.glmap.GLMapVectorObject
import globus.glmap.GLMapVectorObjectList
import globus.glmap.GeometryBuilder
import globus.glmap.MapGeoPoint
import globus.glmap.MapPoint
import kotlin.concurrent.thread
import kotlin.div
import kotlin.math.cos
import kotlin.math.sin
import kotlin.times

class LinesPolygonsActivity : MapDemoActivity() {
    override fun onMapReady() {
        title = "Lines & Polygons"
        renderer.mapGeoCenter = MapGeoPoint(48.8566, 2.3522)
        renderer.mapZoom = 5.0

        val builder = GeometryBuilder()

        builder.addPointLatLon(51.5072, -0.1275)
        builder.addPointLatLon(48.8566, 2.3522)
        builder.addPointLatLon(46.2044, 6.1432)
        builder.addPointLatLon(41.8933, 12.4829)
        addVectorObject(builder, 3, "line{width:4pt;color:#E74C3C;}")

        builder.addPointLatLon(52.5037, 13.4102)
        builder.addPointLatLon(50.0755, 14.4378)
        builder.addPointLatLon(48.2082, 16.3738)
        builder.addPointLatLon(47.4979, 19.0402)
        addVectorObject(builder, 3, "line{width:4pt;color:#3498DB;}")

        builder.addPointLatLon(52.3690, 4.9021)
        builder.addPointLatLon(50.8263, 4.3458)
        builder.addPointLatLon(49.6072, 6.1296)
        builder.addPointLatLon(48.8566, 2.3522)
        addVectorObject(builder, 3, "line{width:3pt;color:#2ECC71;linecap:round;}")

        builder.beginPolygon()
        val tmpPoint = MapPoint()
        builder.addLineCb(11) { index ->
            val angle = index * Math.PI / 5 - Math.PI / 2
            val radius = if (index % 2 == 0) 3.0 else 1.2
            tmpPoint.setLatLon(
                48.8566 + radius * sin(angle),
                2.3522 + radius * cos(angle) / cos(Math.toRadians(48.8566))
            )
            tmpPoint
        }
        addVectorObject(builder, 2, "area{fill-color:#F39C1230;width:2pt;color:#F39C12;}")

        builder.beginPolygon()
        addHexagon(builder, 52.5037, 13.4102, 1.5)
        addHexagon(builder, 52.5037, 13.4102, 0.6)
        addVectorObject(builder, 2, "area{fill-color:#9B59B630;width:2pt;color:#9B59B6;}")
    }

    private fun addVectorObject(builder: GeometryBuilder, order: Int, css: String) {
        val layer = GLMapVectorLayer(order)
        layer.setVectorObject(
            builder.build()!!,
            GLMapVectorCascadeStyle.createStyle(css)!!,
            null
        )
        renderer.add(layer)
    }

    private fun addHexagon(builder: GeometryBuilder, lat: Double, lon: Double, radius: Double) {
        val tmpPoint = MapPoint()
        builder.addLineCb(7) { index ->
            val angle = index * Math.PI / 3
            tmpPoint.setLatLon(lat + radius * sin(angle), lon + radius * cos(angle) / cos(Math.toRadians(lat)))
            tmpPoint
        }
    }
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
                        "area{fill-color:#3498DB40;width:1.5pt;color:#2C3E50;}"
                    )!!
                    layer.setVectorObjects(loaded, style, null)
                    renderer.add(layer)
                    fit(loaded.bBox)
                    setGestures(onTap = { touch ->
                        renderer.state?.use { state ->
                            val point = state.convertDisplayToInternal(touch.x.toDouble(), touch.y.toDouble(), MapPoint())
                            state.findNearPoint(loaded, loaded.size() - 1, -1, point, 10.0)?.use {
                                showError("Tapped: ${it.asGeoJSON()}")
                            }
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
