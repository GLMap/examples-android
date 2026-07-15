package globus.demo.search

import android.graphics.Color
import android.graphics.Point
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.LinearLayout
import androidx.appcompat.widget.SwitchCompat
import globus.demo.base.MapDemoActivity
import globus.glmap.GLMapError
import globus.glmap.GLMapInfo
import globus.glmap.GLMapLocaleSettings
import globus.glmap.GLMapManager
import globus.glmap.GLMapMarkerImage
import globus.glmap.GLMapMarkerLayer
import globus.glmap.GLMapMarkerStyleCollection
import globus.glmap.GLMapMarkerStyleCollectionDataCallback
import globus.glmap.GLMapTextAlignment
import globus.glmap.GLMapVectorObject
import globus.glmap.GLMapVectorObjectList
import globus.glmap.GLMapVectorStyle
import globus.glmap.MapGeoPoint
import globus.glmap.MapPoint
import globus.glmap.SVGRender
import globus.glsearch.GLSearch
import globus.glsearch.GLSearchRequest
import globus.glsearch.GLSearchRequestType

class SearchActivity : MapDemoActivity() {
    private val center = MapGeoPoint(42.4341, 19.26)
    private var requestID = 0L
    private var generation = 0
    private var markerLayer: GLMapMarkerLayer? = null

    override fun onMapReady() {
        title = "Search"
        GLMapManager.AddDataSet(GLMapInfo.DataSet.MAP, null, "Montenegro.vm", assets, null)
        renderer.mapGeoCenter = center
        renderer.mapZoom = 12.0

        val query = EditText(this).apply {
            hint = "Place or empty for restaurants"
            setSingleLine()
            imeOptions = EditorInfo.IME_ACTION_SEARCH
        }
        val offline = SwitchCompat(this).apply { text = "Offline" }
        addTopView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(4), dp(12), dp(4))
            setBackgroundColor(0xEFFFFFFF.toInt())
            addView(query)
            addView(offline)
        })
        query.setOnEditorActionListener { _, action, _ ->
            if (action == EditorInfo.IME_ACTION_SEARCH) {
                search(query.text.toString(), offline.isChecked)
                true
            } else {
                false
            }
        }
        offline.setOnCheckedChangeListener { _, checked -> search(query.text.toString(), checked) }
        addButton("Search") { search(query.text.toString(), offline.isChecked) }
        search("", false)
    }

    private fun search(text: String, offline: Boolean) {
        cancelRequest()
        val currentGeneration = generation
        title = if (offline) "Searching offline..." else "Searching online..."

        val request = GLSearchRequest(
            GLSearchRequestType.Search,
            text,
            center,
            50,
            arrayOf("en", "native"),
            if (text.isBlank()) arrayOf("restaurant") else null,
        )
        val callback = object : GLSearchRequest.ResultsCallback {
            override fun onResult(objects: GLMapVectorObjectList) = runOnUiThread {
                if (currentGeneration != generation) {
                    objects.dispose()
                    return@runOnUiThread
                }
                requestID = 0
                val results = objects.toArray()
                objects.dispose()
                showResults(results, if (offline) "Offline" else "Online")
            }

            override fun onError(error: GLMapError) = runOnUiThread {
                if (currentGeneration != generation) return@runOnUiThread
                requestID = 0
                showError(error.message ?: error.toString())
            }
        }
        requestID = if (offline) request.startOffline(callback) else request.startOnline(callback)
    }

    private fun showResults(objects: Array<GLMapVectorObject>, source: String) {
        markerLayer?.let {
            renderer.remove(it)
            it.dispose()
        }
        markerLayer = null
        title = "$source: ${objects.size} results"
        if (objects.isEmpty()) return

        val bitmap = SVGRender.render(
            assets,
            "cluster.svg",
            SVGRender.transform(renderer.screenScale * 0.2, Color.rgb(0, 102, 204)),
        ) ?: return
        val styles = GLMapMarkerStyleCollection().apply {
            addStyle(GLMapMarkerImage("result", bitmap))
            setDataCallback(SearchMarkerStyle(renderer.localeSettings))
        }
        markerLayer = GLMapMarkerLayer(objects, styles, 0.0, 3).also(renderer::add)

        val bbox = globus.glmap.GLMapBBox()
        objects.forEach { bbox.addPoint(it.point()) }
        fit(bbox)
    }

    private fun cancelRequest() {
        generation++
        if (requestID != 0L) GLSearchRequest.cancel(requestID)
        requestID = 0
    }

    override fun onDestroy() {
        cancelRequest()
        super.onDestroy()
    }
}

private class SearchMarkerStyle(private val locale: GLMapLocaleSettings) : GLMapMarkerStyleCollectionDataCallback() {
    private val textStyle = GLMapVectorStyle.createStyle("{text-color:black;font-size:12;font-stroke-width:1pt;font-stroke-color:white;}")!!

    override fun getLocation(marker: Any) = (marker as GLMapVectorObject).point()
    override fun fillUnionData(markersCount: Int, nativeMarker: Long) = Unit
    override fun fillData(marker: Any, nativeMarker: Long) {
        GLMapMarkerStyleCollection.setMarkerStyle(nativeMarker, 0)
        (marker as GLMapVectorObject).localizedName(locale)?.string?.let {
            GLMapMarkerStyleCollection.setMarkerText(
                nativeMarker,
                it,
                GLMapTextAlignment.Undefined,
                Point(0, 10),
                textStyle,
            )
        }
    }
}

class POITapActivity : MapDemoActivity() {
    override fun onMapReady() {
        title = "Tap to find POI"
        renderer.mapGeoCenter = MapGeoPoint(43.7696, 11.2558)
        renderer.mapZoom = 16.0
        setGestures(onTap = { touch ->
            val objectAtPoint = GLSearch.MapObjectNearPoint(renderer, touch.x, touch.y, 20.0)
            if (objectAtPoint == null) {
                title = "No POI here"
            } else {
                val name = objectAtPoint.localizedName(renderer.localeSettings)?.string
                val point = MapGeoPoint(objectAtPoint.point())
                val text = name?.takeIf(String::isNotBlank) ?: "%.4f, %.4f".format(point.lat, point.lon)
                title = text
                showError(text)
            }
        })
    }
}
