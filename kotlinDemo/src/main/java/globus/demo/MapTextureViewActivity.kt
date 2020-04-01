package globus.demo

import android.app.Activity
import android.os.Bundle
import android.view.TextureView
import globus.glmap.GLMapView
import globus.glmap.MapPoint

class MapTextureViewActivity : Activity() {
    private var mapView: GLMapView? = null
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.texture_view_map)
        val textureView = findViewById<TextureView>(R.id.texture_view)
        val mapView = GLMapView(this, textureView)
        mapView.loadStyle(assets, "DefaultStyle.bundle")
        mapView.setScaleRulerStyle(
                GLMapView.GLUnitSystem.International,
                GLMapView.GLMapPlacement.BottomCenter,
                MapPoint(10.0, 10.0), 200.0)
        mapView.setAttributionPosition(GLMapView.GLMapPlacement.TopCenter)
        this.mapView = mapView //Save mapView to keep map rendering after gc
    }
}