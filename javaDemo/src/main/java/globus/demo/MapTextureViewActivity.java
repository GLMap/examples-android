package globus.demo;

import android.app.Activity;
import android.os.Bundle;
import android.view.TextureView;
import globus.glmap.GLMapView;
import globus.glmap.MapPoint;

public class MapTextureViewActivity extends Activity {
  GLMapView mapView;

  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.texture_view_map);

    TextureView textureView = findViewById(R.id.texture_view);
    mapView = new GLMapView(this, textureView);

    mapView.loadStyle(getAssets(), "DefaultStyle.bundle");
    mapView.setScaleRulerStyle(
        GLMapView.GLUnitSystem.International,
        GLMapView.GLMapPlacement.BottomCenter,
        new MapPoint(10, 10),
        200);
    mapView.setAttributionPosition(GLMapView.GLMapPlacement.TopCenter);
  }
}
