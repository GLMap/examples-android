package globus.javaDemo;

import android.app.Activity;
import android.util.DisplayMetrics;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import globus.glmap.GLMapFileStorage;
import globus.glmap.GLMapRasterTileSource;
import globus.glmap.GLMapStorage;
import globus.glmap.GLMapStorageFile;

import java.io.File;

/** Created by destman on 11/11/15. */
class OSMTileSource extends GLMapRasterTileSource {
    private String[] mirrors;

    OSMTileSource(Activity activity) throws OutOfMemoryError
    {
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

    @Nullable
    private static GLMapStorageFile CachePath(Activity activity)
    {
        GLMapStorage storage = new GLMapFileStorage(activity.getFilesDir()).findStorage("RasterCache", true);
        return storage == null ? null : storage.findFile("osm.db", true);
    }

    @Override
    public String urlForTilePos(int x, int y, int z)
    {
        String mirror = mirrors[(int)(Math.random() * mirrors.length)];
        String rv = String.format(mirror, z, x, y);
        Log.i("OSMTileSource", rv);
        return rv;
    }
}
