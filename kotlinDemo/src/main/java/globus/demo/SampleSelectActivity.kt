package globus.demo

import android.app.ListActivity
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.ListView

enum class Samples(val tableName: String) {
    MAP("Open offline map"),
    DARK_THEME("Dark Theme"),
    MAP_EMBEDDED("Open embedded map"),
    MAP_ONLINE("Open online map"),
    MAP_ONLINE_RASTER("Open online raster map"),
    OPEN_ROUTING("Routing"),
    MAP_TEXTURE_VIEW("GLMapView in TextureView"),
    ZOOM_BBOX("Zoom to bbox"),
    OFFLINE_SEARCH("Offline Search"),
    MARKERS("Markers"),
    MARKERS_MAPCSS("Markers using mapcss"),
    IMAGE_SINGLE("Display single image"),
    IMAGE_MULTI("Display image group"),
    MULTILINE("Add multiline"),
    POLYGON("Add polygon"),
    GEO_JSON("Load GeoJSON"),
    CALLBACK_TEST("Callback test"),
    CAPTURE_SCREEN("Capture screen"),
    FLY_TO("Fly to"),
    STYLE_LIVE_RELOAD("Style live reload"),
    TILES_BULK_DOWNLOAD("Bulk tiles download"),
    RECORD_TRACK("Recording track"),
    DOWNLOAD_MAP("Download Map"),
    SVG_TEST("SVG Test")
}

class SampleSelectActivity : ListActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.sample_select)
        listAdapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, Samples.values().map { it.tableName })
    }

    override fun onListItemClick(l: ListView, v: View, position: Int, id: Long) {
        when (position) {
            Samples.OPEN_ROUTING.ordinal -> {
                val intent = Intent(this, RoutingActivity::class.java)
                this.startActivity(intent)
            }
            Samples.MAP_TEXTURE_VIEW.ordinal -> {
                val i = Intent(this, MapTextureViewActivity::class.java)
                i.putExtra("cx", 27.0)
                i.putExtra("cy", 53.0)
                this.startActivity(i)
            }
            Samples.DOWNLOAD_MAP.ordinal -> {
                val i = Intent(this, DownloadActivity::class.java)
                i.putExtra("cx", 27.0)
                i.putExtra("cy", 53.0)
                this.startActivity(i)
            }
            Samples.SVG_TEST.ordinal -> {
                val intent = Intent(this, DisplayImageActivity::class.java)
                this.startActivity(intent)
            }
            else -> {
                val intent = Intent(this, MapViewActivity::class.java)
                val b = Bundle()
                b.putInt("example", position)
                intent.putExtras(b)
                this.startActivity(intent)
            }
        }
    }
}