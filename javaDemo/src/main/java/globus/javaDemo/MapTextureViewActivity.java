package globus.javaDemo;

import android.app.Activity;
import android.os.Bundle;

import globus.glmap.GLMapStyleParser;
import globus.glmap.GLMapTextureView;
import globus.glmap.GLMapVectorCascadeStyle;
import globus.glmap.GLMapViewRenderer;

public class MapTextureViewActivity extends Activity {

    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.texture_view_map);
        GLMapTextureView textureView = findViewById(R.id.texture_view);
        GLMapVectorCascadeStyle style =
                new GLMapStyleParser(getAssets(), "DefaultStyle.bundle").parseFromResources();
        if (style != null) textureView.renderer.setStyle(style);
        textureView.renderer.setScaleRulerStyle(
                GLMapViewRenderer.GLMapPlacement.BottomCenter, 10, 10, 200);
        textureView.renderer.setAttributionPosition(GLMapViewRenderer.GLMapPlacement.TopCenter);
    }
}
