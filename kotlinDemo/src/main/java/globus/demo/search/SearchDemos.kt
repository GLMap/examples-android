package globus.demo.search

import android.graphics.Color
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.Spanned
import android.text.TextWatcher
import android.text.style.ForegroundColorSpan
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.BaseAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import androidx.appcompat.widget.SwitchCompat
import globus.demo.base.MapDemoActivity
import globus.glmap.GLMapError
import globus.glmap.GLMapBalloon
import globus.glmap.GLMapImage
import globus.glmap.GLMapInfo
import globus.glmap.GLMapManager
import globus.glmap.GLMapMarkerImage
import globus.glmap.GLMapMarkerLayer
import globus.glmap.GLMapMarkerStyleCollection
import globus.glmap.GLMapMarkerStyleCollectionDataCallback
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
    private var results = emptyArray<GLMapVectorObject>()
    private var selectedResult: GLMapVectorObject? = null
    private var selectedMarker: GLMapImage? = null
    private lateinit var query: EditText
    private lateinit var offline: SwitchCompat
    private lateinit var resultsList: ListView
    private val resultsAdapter = ResultsAdapter()
    private val searchHandler = Handler(Looper.getMainLooper())
    private val autocomplete =
        Runnable { search(query.text.toString(), offline.isChecked, GLSearchRequestType.Autocomplete) }

    override fun onMapReady() {
        title = "Search"
        if (!GLMapManager.AddDataSet(GLMapInfo.DataSet.MAP, null, "Montenegro.vm", assets, null)) {
            showError("Cannot open bundled Montenegro map")
        }
        renderer.mapGeoCenter = center
        renderer.mapZoom = 12.0

        query = EditText(this).apply {
            hint = "Place or empty for restaurants"
            setSingleLine()
            imeOptions = EditorInfo.IME_ACTION_SEARCH
            setTextColor(Color.BLACK)
            setHintTextColor(Color.GRAY)
        }
        offline = SwitchCompat(this).apply {
            id = android.R.id.checkbox
            text = "Offline"
            setTextColor(Color.DKGRAY)
        }
        val searchButton = Button(this).apply { text = "Search" }
        val searchPanel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(4), dp(12), dp(4))
            setBackgroundColor(0xEFFFFFFF.toInt())
            addView(
                LinearLayout(context).apply {
                    addView(query, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
                    addView(searchButton)
                }
            )
            addView(offline)
        }
        addTopView(searchPanel)

        resultsList = ListView(this).apply {
            id = android.R.id.list
            adapter = resultsAdapter
            choiceMode = ListView.CHOICE_MODE_SINGLE
            setBackgroundColor(Color.WHITE)
            setOnItemClickListener { _, _, position, _ ->
                selectResult(position)
            }
        }
        container.removeView(mapView)
        container.addView(
            LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                addView(mapView, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 3f))
                addView(resultsList, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 2f))
            },
            0,
            FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
        )
        searchPanel.addOnLayoutChangeListener { panel, _, _, _, _, _, _, _, _ ->
            val panelLocation = IntArray(2).also(panel::getLocationInWindow)
            val mapLocation = IntArray(2).also(mapView::getLocationInWindow)
            setVisibleMapInsets(dp(16), panelLocation[1] + panel.height - mapLocation[1] + dp(16), dp(16), dp(16))
        }

        selectedMarker = SVGRender.render(
            assets,
            "pin.svg",
            SVGRender.transform(renderer.screenScale * 1.4, Color.rgb(230, 60, 60))
        )?.let { bitmap ->
            GLMapImage(4).apply {
                setBitmap(bitmap)
                setOffset(bitmap.width / 2, 0)
                scale = 0.01
                isHidden = true
                renderer.add(this)
            }
        }
        setGestures(onTap = { touch ->
            val layer = markerLayer ?: return@setGestures
            val point = renderer.convertDisplayToInternal(touch.x.toDouble(), touch.y.toDouble())
            val hits = layer.objectsNearPoint(renderer, point, 24.0) ?: return@setGestures
            val row = results.indexOfFirst(hits::contains)
            if (row >= 0) {
                resultsList.setItemChecked(row, true)
                resultsList.smoothScrollToPosition(row)
                selectResult(row)
            }
        })

        query.setOnEditorActionListener { _, action, _ ->
            if (action == EditorInfo.IME_ACTION_SEARCH) {
                searchHandler.removeCallbacks(autocomplete)
                search(query.text.toString(), offline.isChecked, GLSearchRequestType.Search)
                true
            } else {
                false
            }
        }
        query.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(text: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(text: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(text: Editable?) {
                searchHandler.removeCallbacks(autocomplete)
                searchHandler.postDelayed(autocomplete, 300)
            }
        })
        offline.setOnCheckedChangeListener { _, checked ->
            searchHandler.removeCallbacks(autocomplete)
            search(query.text.toString(), checked, GLSearchRequestType.Search)
        }
        searchButton.setOnClickListener {
            searchHandler.removeCallbacks(autocomplete)
            search(query.text.toString(), offline.isChecked, GLSearchRequestType.Search)
        }
        search("", false, GLSearchRequestType.Search)
    }

    private fun search(text: String, offline: Boolean, type: Int) {
        cancelRequest()
        val currentGeneration = generation
        val source = if (offline) "Offline" else "Online"
        title = "Searching ${source.lowercase()}..."

        val request = GLSearchRequest(
            type,
            text,
            center,
            50,
            arrayOf("en", "native"),
            if (text.isBlank()) arrayOf("restaurant") else null
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
                showResults(results, source)
            }

            override fun onError(error: GLMapError) = runOnUiThread {
                if (currentGeneration != generation) return@runOnUiThread
                requestID = 0
                title = "$source Search Failed"
                showError(error.toString())
            }
        }
        requestID = if (offline) request.startOffline(callback) else request.startOnline(callback)
    }

    private fun showResults(objects: Array<GLMapVectorObject>, source: String) {
        clearResults()
        results = objects
        resultsAdapter.notifyDataSetChanged()
        title = "$source: ${objects.size} results"
        if (objects.isEmpty()) return

        val bitmap = SVGRender.render(
            assets,
            "cluster.svg",
            SVGRender.transform(renderer.screenScale * 0.2, Color.rgb(0, 102, 204))
        ) ?: return showError("Cannot render result marker SVG")
        val styles = GLMapMarkerStyleCollection().apply {
            addStyle(GLMapMarkerImage("result", bitmap))
            setDataCallback(SearchMarkerStyle)
        }
        markerLayer = GLMapMarkerLayer(objects, styles, 0.0, 3).also(renderer::add)

        val bbox = globus.glmap.GLMapBBox()
        objects.forEach { bbox.addPoint(it.point()) }
        fit(bbox)
    }

    private fun selectResult(position: Int) {
        val result = results[position]
        if (selectedResult !== result) {
            // Animate the bulk marker out while an individual pin grows in at the same point.
            markerLayer?.modify(selectedResult?.let { arrayOf(it) }, setOf(result), true, null)
            selectedResult = result
            selectedMarker?.apply {
                this.position = result.point()
                scale = 0.01
                isHidden = false
            }
        }
        resultsList.setItemChecked(position, true)
        renderer.animate { animation ->
            animation.setDuration(0.3)
            selectedMarker?.let { animation.setScale(it, 1.0) }
            centerMapOn(result.point())
        }
    }

    private fun clearResults() {
        selectedMarker?.isHidden = true
        selectedResult = null
        if (::resultsList.isInitialized) resultsList.clearChoices()
        markerLayer?.let {
            renderer.remove(it)
            it.dispose()
        }
        markerLayer = null
        results.forEach(GLMapVectorObject::dispose)
        results = emptyArray()
    }

    private fun cancelRequest() {
        generation++
        if (requestID != 0L) GLSearchRequest.cancel(requestID)
        requestID = 0
    }

    override fun onDestroy() {
        searchHandler.removeCallbacks(autocomplete)
        cancelRequest()
        clearResults()
        super.onDestroy()
    }

    private inner class ResultsAdapter : BaseAdapter() {
        override fun getCount() = results.size
        override fun getItem(position: Int) = results[position]
        override fun getItemId(position: Int) = position.toLong()

        override fun getView(position: Int, convertView: View?, parent: android.view.ViewGroup): View {
            val view =
                convertView ?: layoutInflater.inflate(android.R.layout.simple_list_item_activated_2, parent, false)
            val info = GLSearch.GetDisplayInfo(getItem(position), renderer.localeSettings)
            val title = info?.title?.getSpanned(
                ForegroundColorSpan(Color.BLACK),
                ForegroundColorSpan(Color.rgb(0, 102, 204)),
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            ) ?: "Unnamed"
            val secondaryText = info?.secondaryText?.string
            info?.close()
            view.findViewById<TextView>(android.R.id.text1).apply {
                text = title
                setTextColor(Color.BLACK)
            }
            view.findViewById<TextView>(android.R.id.text2).apply {
                text = secondaryText
                setTextColor(Color.DKGRAY)
            }
            return view
        }
    }
}

private object SearchMarkerStyle : GLMapMarkerStyleCollectionDataCallback() {
    override fun getLocation(marker: Any) = (marker as GLMapVectorObject).point()
    override fun fillUnionData(markersCount: Int, nativeMarker: Long) = Unit
    override fun fillData(marker: Any, nativeMarker: Long) {
        GLMapMarkerStyleCollection.setMarkerStyle(nativeMarker, 0)
    }
}

class POITapActivity : MapDemoActivity() {
    private var balloon: GLMapBalloon? = null

    override fun onMapReady() {
        title = "Tap to find POI"
        renderer.mapGeoCenter = MapGeoPoint(43.7696, 11.2558)
        renderer.mapZoom = 16.0
        setGestures(onTap = { touch ->
            balloon?.let {
                renderer.remove(it)
                it.dispose()
            }
            balloon = null

            renderer.state?.use { state ->
                val objectAtPoint = GLSearch.MapObjectNearPoint(state, touch.x, touch.y, 20.0)
                if (objectAtPoint == null) {
                    title = "No POI here"
                } else {
                    objectAtPoint.use { objectOnMap ->
                        val name = objectOnMap.localizedName(renderer.localeSettings)?.use { it.string }
                        val position = objectOnMap.point()
                        val point = MapGeoPoint(position)
                        val text = name?.takeIf(String::isNotBlank)
                            ?: "%.4f, %.4f".format(point.lat, point.lon)
                        balloon = GLMapBalloon(10).apply {
                            setBackgroundBitmap(createBalloonBackground(), Rect(dp(20), dp(20), dp(20), dp(20)))
                            setText(
                                text,
                                GLMapVectorStyle.createStyle("{text-color:black;font-size:14;}")!!,
                                Rect(dp(12), dp(8), dp(12), dp(8)),
                                null
                            )
                            this.position = position
                            renderer.add(this)
                        }
                        title = text
                    }
                }
            }
        })
    }
}
