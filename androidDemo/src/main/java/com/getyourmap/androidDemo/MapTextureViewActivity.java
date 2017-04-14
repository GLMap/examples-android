package com.getyourmap.androidDemo;

import android.app.Activity;
import android.os.Bundle;
import android.view.TextureView;

import com.glmapview.GLMapView;
import com.glmapview.MapPoint;

public class MapTextureViewActivity extends Activity
{
    GLMapView mapView;

    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.texture_view_map);

        TextureView textureView = (TextureView)findViewById(R.id.texture_view);
        mapView = new GLMapView(this, textureView);

        mapView.loadStyle(getAssets(), "DefaultStyle.bundle");
        mapView.setScaleRulerStyle(GLMapView.GLUnits.SI, GLMapView.GLMapPlacement.BottomCenter, new MapPoint(10, 10), 200);
        mapView.setAttributionPosition(GLMapView.GLMapPlacement.TopCenter);
    }
}
