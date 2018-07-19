package com.getyourmap.androidDemo;

import android.app.Activity;
import android.util.DisplayMetrics;
import android.util.Log;
import com.glmapview.GLMapRasterTileSource;
import java.io.File;

/** Created by destman on 11/11/15. */
class OSMTileSource extends GLMapRasterTileSource {
  private String mirrors[];

  OSMTileSource(Activity activity) throws OutOfMemoryError {
    super(CachePath(activity));

    mirrors = new String[3];
    mirrors[0] = "https://a.tile.openstreetmap.org/%d/%d/%d.png";
    mirrors[1] = "https://b.tile.openstreetmap.org/%d/%d/%d.png";
    mirrors[2] = "https://c.tile.openstreetmap.org/%d/%d/%d.png";

    setValidZoomMask((1 << 20) - 1); // Set as valid zooms all levels from 0 to 19

    DisplayMetrics metrics = new DisplayMetrics();
    activity.getWindowManager().getDefaultDisplay().getMetrics(metrics);
    // For devices with high screen density we can make tile size a bit bigger.
    if (metrics.scaledDensity >= 2) {
      setTileSize(192);
    }

    setAttributionText("© OpenStreetMap contributors");
  }

  private static String CachePath(Activity activity) {
    File filesDir = new File(activity.getFilesDir().getAbsolutePath(), "RasterCache");
    filesDir.mkdir();
    return new File(filesDir.getAbsolutePath(), "osm.db").getAbsolutePath();
  }

  @Override
  public String urlForTilePos(int x, int y, int z) {
    String mirror = mirrors[(int) (Math.random() * mirrors.length)];
    String rv = String.format(mirror, z, x, y);
    Log.i("OSMTileSource", rv);
    return rv;
  }
}
