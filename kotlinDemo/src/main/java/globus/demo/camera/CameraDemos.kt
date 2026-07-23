package globus.demo.camera

import globus.demo.base.MapDemoActivity
import globus.glmap.GLMapAnimation
import globus.glmap.GLMapBBox
import globus.glmap.GLMapVectorCascadeStyle
import globus.glmap.GLMapVectorLayer
import globus.glmap.GLMapVectorObject
import globus.glmap.MapGeoPoint
import globus.glmap.MapPoint

class FlyToActivity : MapDemoActivity() {
    private val destinations = listOf(
        "Porto" to MapPoint.CreateFromGeoCoordinates(41.1579, -8.6291),
        "San Sebastián" to MapPoint.CreateFromGeoCoordinates(43.3183, -1.9812),
        "Lucerne" to MapPoint.CreateFromGeoCoordinates(47.0502, 8.3093),
        "Bruges" to MapPoint.CreateFromGeoCoordinates(51.2093, 3.2247),
        "Dubrovnik" to MapPoint.CreateFromGeoCoordinates(42.6507, 18.0944),
        "Tallinn" to MapPoint.CreateFromGeoCoordinates(59.4370, 24.7536),
    )
    private var index = 0

    override fun onMapReady() {
        showDestination()
        addButton("Fly") {
            index = (index + 1) % destinations.size
            showDestination()
        }
    }

    private fun showDestination() {
        title = destinations[index].first
        renderer.animate { animation ->
            animation.flyToMode = GLMapAnimation.FlyToMode.Enabled
            renderer.mapZoom = 14.0
            renderer.mapCenter = destinations[index].second
        }
    }
}

class ZoomToBBoxActivity : MapDemoActivity() {
    private val cityPoints = arrayOf(
        MapPoint.CreateFromGeoCoordinates(52.5037, 13.4102),
        MapPoint.CreateFromGeoCoordinates(48.8505, 2.3343),
        MapPoint.CreateFromGeoCoordinates(51.5072, -0.1275),
        MapPoint.CreateFromGeoCoordinates(41.8933, 12.4829),
        MapPoint.CreateFromGeoCoordinates(40.4168, -3.7038),
        MapPoint.CreateFromGeoCoordinates(52.2251, 21.0103),
        MapPoint.CreateFromGeoCoordinates(48.2082, 16.3738),
        MapPoint.CreateFromGeoCoordinates(50.0755, 14.4378),
    )
    private val bbox = GLMapBBox().apply { cityPoints.forEach(::addPoint) }

    override fun onMapReady() {
        title = "Zoom to BBox"
        val line = GLMapVectorObject.createMultiline(arrayOf(cityPoints))
        val style = GLMapVectorCascadeStyle.createStyle("line{width:4pt; color:#E74C3C;}")!!
        renderer.add(GLMapVectorLayer().apply { setVectorObject(line, style, null) })
        fit(bbox)

        addButton("Zoom to Fit") {
            renderer.animate { animation ->
                animation.flyToMode = GLMapAnimation.FlyToMode.Enabled
                animation.setDuration(2.0)
                renderer.mapZoom = renderer.mapZoomForBBox(bbox)
                centerMapOn(bbox.center())
            }
        }
    }
}
