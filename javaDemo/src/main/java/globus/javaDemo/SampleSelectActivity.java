package globus.javaDemo;

import android.app.ListActivity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.ListView;

public class SampleSelectActivity extends ListActivity {
    public enum Samples {
        MAP,
        DARK_THEME,
        MAP_EMBEDDED,
        MAP_ONLINE,
        MAP_ONLINE_RASTER,
        OPEN_ROUTING,
        MAP_TEXTURE_VIEW,
        ZOOM_BBOX,
        OFFLINE_SEARCH,
        MARKERS,
        MARKERS_MAPCSS,
        IMAGE_SINGLE,
        IMAGE_MULTI,
        MULTILINE,
        POLYGON,
        GEO_JSON,
        CALLBACK_TEST,
        CAPTURE_SCREEN,
        FLY_TO,
        DOWNLOAD_IN_BBOX,
        STYLE_LIVE_RELOAD,
        RECORD_TRACK,
        DOWNLOAD_MAP,
        SVG_TEST,
    }

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.sample_select);

        String[] values = new String[] {
            "Open offline map",
            "Dark Theme",
            "Open embedded map",
            "Open online map",
            "Open online raster map",
            "Routing",
            "GLMapView in TextureView",
            "Zoom to bbox",
            "Offline Search",
            "Markers",
            "Markers using mapcss",
            "Display single image",
            "Display image group",
            "Add multiline",
            "Add polygon",
            "Load GeoJSON",
            "Callback test",
            "Capture screen",
            "Fly to",
            "Download data in bounding box",
            "Style live reload",
            "Recording track",
            "Download Map",
            "SVG Test",
        };
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, values);
        setListAdapter(adapter);
    }

    @Override
    protected void onListItemClick(ListView l, View v, int position, long id)
    {
        if (position == Samples.OPEN_ROUTING.ordinal()) {
            Intent intent = new Intent(this, RoutingActivity.class);
            this.startActivity(intent);
            return;
        }

        if (position == Samples.MAP_TEXTURE_VIEW.ordinal()) {
            Intent i = new Intent(this, MapTextureViewActivity.class);
            i.putExtra("cx", 27.0);
            i.putExtra("cy", 53.0);
            this.startActivity(i);
            return;
        }

        if (position == Samples.DOWNLOAD_MAP.ordinal()) {
            Intent i = new Intent(this, DownloadActivity.class);
            i.putExtra("cx", 27.0);
            i.putExtra("cy", 53.0);
            this.startActivity(i);
            return;
        }

        if (position == Samples.SVG_TEST.ordinal()) {
            Intent intent = new Intent(this, DisplayImageActivity.class);
            this.startActivity(intent);
            return;
        }

        Intent intent = new Intent(this, MapViewActivity.class);
        Bundle b = new Bundle();
        b.putInt("example", position);
        intent.putExtras(b);
        this.startActivity(intent);
    }
}
