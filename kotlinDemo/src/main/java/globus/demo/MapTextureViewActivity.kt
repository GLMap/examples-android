package globus.demo

import android.app.Activity
import android.os.Bundle
import android.view.TextureView
import globus.glmap.GLMapStyleParser
import globus.glmap.GLMapView
import globus.glmap.GLMapTextureView
import globus.glmap.GLMapViewRenderer
import globus.glmap.MapPoint

class MapTextureViewActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.texture_view_map)
        val textureView = findViewById<GLMapTextureView>(R.id.texture_view)
        textureView.renderer.setStyle(GLMapStyleParser(assets, "DefaultStyle.bundle").parseFromResources()!!)
        textureView.renderer.setScaleRulerStyle(GLMapViewRenderer.GLMapPlacement.BottomCenter, 10, 10, 200.0)
        textureView.renderer.setAttributionPosition(GLMapViewRenderer.GLMapPlacement.TopCenter)
    }
}
