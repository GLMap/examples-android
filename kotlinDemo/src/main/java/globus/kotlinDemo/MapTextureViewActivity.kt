package globus.javaDemo

import android.app.Activity
import android.os.Bundle
import globus.glmap.GLMapScaleRuler
import globus.glmap.GLMapStyleParser
import globus.glmap.GLMapTextureView
import globus.glmap.GLMapViewRenderer
import globus.kotlinDemo.R

class MapTextureViewActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.texture_view_map)
        val textureView = findViewById<GLMapTextureView>(R.id.texture_view)
        val style = GLMapStyleParser(assets, "DefaultStyle.bundle").parseFromResources()
        if (style != null) textureView.renderer.setStyle(style)

        val ruler = GLMapScaleRuler(Int.MAX_VALUE)
        ruler.setPlacement(GLMapViewRenderer.GLMapPlacement.BottomCenter, 10, 10, 200.0)
        textureView.renderer.add(ruler)
        textureView.renderer.setAttributionPosition(GLMapViewRenderer.GLMapPlacement.TopCenter)
    }
}