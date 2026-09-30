package globus.demo.offline

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.FrameLayout
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SearchView
import globus.demo.base.applyContentInsets
import globus.demo.base.MapDemoActivity
import globus.glmap.GLMapBBox
import globus.glmap.GLMapDownloadTask
import globus.glmap.GLMapInfo
import globus.glmap.GLMapLocaleSettings
import globus.glmap.GLMapManager
import globus.glmap.MapPoint

class DownloadBBoxActivity : MapDemoActivity() {
    override fun onMapReady() {
        title = "Download BBox"
        val bbox = GLMapBBox().apply {
            addPoint(MapPoint.CreateFromGeoCoordinates(43.73, 11.20))
            addPoint(MapPoint.CreateFromGeoCoordinates(43.80, 11.30))
        }
        fit(bbox)
        renderer.enableClipping(bbox, 9f, 16f)

        val status = TextView(this).apply {
            text = "Downloading map + navigation + elevation..."
            textSize = 15f
            setPadding(dp(16), dp(12), dp(16), dp(12))
            setBackgroundColor(0xD9262626.toInt())
            setTextColor(0xFFFFFFFF.toInt())
        }
        container.addView(
            status,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
                bottomMargin = dp(16)
            }
        )
        downloadBBoxData(
            bbox,
            listOf(
                GLMapInfo.DataSet.MAP to "bbox_map.vmtar",
                GLMapInfo.DataSet.NAVIGATION to "bbox_nav.navtar",
                GLMapInfo.DataSet.ELEVATION to "bbox_ele.eletar"
            )
        ) { error ->
            if (error != null) {
                status.text = "Download failed"
                showError(error)
            } else {
                renderer.drawElevationLines = true
                renderer.drawHillshades = true
                renderer.reloadTiles()
                status.text = "All data downloaded"
                status.animate().alpha(0f).setStartDelay(2_000).setDuration(1_000).start()
            }
        }
    }
}

class DownloadMapsActivity :
    AppCompatActivity(),
    GLMapManager.StateListener {
    private val locale = GLMapLocaleSettings(arrayOf("en", "native"), GLMapLocaleSettings.UnitSystem.International)
    private val adapter = MapsAdapter()
    private var mapGroup: GLMapInfo? = null
    private var allMaps = emptyArray<GLMapInfo>()
    private var query = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        mapGroup = intent.getLongExtra(EXTRA_MAP_ID, 0L)
            .takeIf { it != 0L }
            ?.let(GLMapManager::GetMapWithID)
        title = mapGroup?.getLocalizedName(locale) ?: "Download Maps"
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        val list = ListView(this).apply {
            this.adapter = this@DownloadMapsActivity.adapter
            setOnItemClickListener { _, _, position, _ ->
                this@DownloadMapsActivity.adapter.getItem(position).map?.let(::openOrToggleMap)
            }
        }
        setContentView(list)
        applyContentInsets(list)
        GLMapManager.addStateListener(this)
        reloadMaps()
        if (mapGroup == null) {
            GLMapManager.UpdateMapList callback@{ _, error ->
                if (isDestroyed) return@callback
                if (error != null) Toast.makeText(this, error.toString(), Toast.LENGTH_LONG).show()
                reloadMaps()
            }
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        val search = SearchView(this).apply {
            queryHint = "Search maps"
            setOnQueryTextListener(object : SearchView.OnQueryTextListener {
                override fun onQueryTextSubmit(text: String?): Boolean = false
                override fun onQueryTextChange(text: String?): Boolean {
                    this@DownloadMapsActivity.query = text.orEmpty().trim()
                    showMaps()
                    return true
                }
            })
        }
        menu.add("Search")
            .setIcon(android.R.drawable.ic_menu_search)
            .setActionView(search)
            .setShowAsActionFlags(MenuItem.SHOW_AS_ACTION_ALWAYS or MenuItem.SHOW_AS_ACTION_COLLAPSE_ACTION_VIEW)
        return true
    }

    private fun reloadMaps() {
        allMaps = (mapGroup?.maps ?: GLMapManager.GetMaps())
            .sortedBy { it.getLocalizedName(locale) }
            .toTypedArray()
        showMaps()
    }

    private fun showMaps() {
        val visible = if (query.isEmpty()) {
            allMaps.asList()
        } else {
            allMaps.filter { map ->
                map.getLocalizedName(locale).contains(query, ignoreCase = true) ||
                    map.isoCode?.contains(query, ignoreCase = true) == true
            }
        }
        val (onDevice, available) = visible.partition(::isOnDevice)
        adapter.rows = buildList {
            if (onDevice.isNotEmpty()) {
                add(MapRow(header = "ON DEVICE"))
                onDevice.forEach { add(MapRow(map = it)) }
            }
            if (available.isNotEmpty()) {
                add(MapRow(header = "AVAILABLE"))
                available.forEach { add(MapRow(map = it)) }
            }
        }
        adapter.notifyDataSetChanged()
    }

    private fun isOnDevice(map: GLMapInfo): Boolean =
        map.dataSetsWithState(GLMapInfo.State.NOT_DOWNLOADED) != GLMapInfo.DataSetMask.ALL ||
            map.maps?.any(::isOnDevice) == true

    private fun openOrToggleMap(map: GLMapInfo) {
        if (map.isCollection) {
            startActivity(Intent(this, DownloadMapsActivity::class.java).putExtra(EXTRA_MAP_ID, map.mapID))
            return
        }
        val tasks = GLMapManager.getDownloadTasks(map.mapID, GLMapInfo.DataSetMask.ALL)
        if (!tasks.isNullOrEmpty()) {
            tasks.forEach(GLMapDownloadTask::cancel)
            return
        }
        val pending = pendingDataSets(map)
        if (map.getSizeOnDisk(GLMapInfo.DataSetMask.ALL) > 0 || map.getTempSize(GLMapInfo.DataSetMask.ALL) > 0) {
            val actions = mutableListOf<String>()
            if (pending != 0) actions += "Download / update missing data"
            actions += "Delete downloaded data"
            AlertDialog.Builder(this)
                .setTitle(map.getLocalizedName(locale))
                .setItems(actions.toTypedArray()) { _, index ->
                    if (pending != 0 && index == 0) {
                        GLMapManager.DownloadDataSets(map, pending)
                    } else {
                        GLMapManager.DeleteDataSets(map, GLMapInfo.DataSetMask.ALL)
                    }
                }
                .setNegativeButton("Cancel", null)
                .show()
        } else if (pending != 0) {
            GLMapManager.DownloadDataSets(map, pending)
        }
    }

    @GLMapInfo.DataSetMask
    private fun pendingDataSets(map: GLMapInfo): Int {
        var available = 0
        if (map.getSizeOnServer(GLMapInfo.DataSetMask.MAP) > 0) available = available or GLMapInfo.DataSetMask.MAP
        if (map.getSizeOnServer(GLMapInfo.DataSetMask.NAVIGATION) > 0) {
            available = available or GLMapInfo.DataSetMask.NAVIGATION
        }
        if (map.getSizeOnServer(GLMapInfo.DataSetMask.ELEVATION) > 0) {
            available = available or GLMapInfo.DataSetMask.ELEVATION
        }
        val pending = map.dataSetsWithState(GLMapInfo.State.NOT_DOWNLOADED) or
            map.dataSetsWithState(GLMapInfo.State.NEED_RESUME) or map.dataSetsWithState(GLMapInfo.State.NEED_UPDATE)
        return available and pending
    }

    override fun onStartDownloading(task: GLMapDownloadTask) = notifyChanged()
    override fun onDownloadProgress(task: GLMapDownloadTask) {
        if (!isDestroyed) adapter.notifyDataSetChanged()
    }
    override fun onFinishDownloading(task: GLMapDownloadTask) {
        notifyChanged()
        val error = task.error
        if (!isFinishing && !isDestroyed && error != null && !error.isCancelled) {
            supportActionBar?.subtitle = "Download failed — tap the map to retry"
            Toast.makeText(this, error.toString(), Toast.LENGTH_LONG).show()
        }
    }
    override fun onStateChanged(map: GLMapInfo?, dataSet: Int) = notifyChanged()

    private fun notifyChanged() {
        if (!isDestroyed) reloadMaps()
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    override fun onDestroy() {
        GLMapManager.removeStateListener(this)
        locale.dispose()
        super.onDestroy()
    }

    private data class MapRow(val header: String? = null, val map: GLMapInfo? = null)

    private inner class MapsAdapter : BaseAdapter() {
        var rows = emptyList<MapRow>()

        override fun getCount() = rows.size
        override fun getItem(position: Int) = rows[position]
        override fun getItemId(position: Int) = rows[position].map?.mapID ?: position.toLong()
        override fun getViewTypeCount() = 2
        override fun getItemViewType(position: Int) = if (rows[position].map == null) 0 else 1
        override fun areAllItemsEnabled() = false
        override fun isEnabled(position: Int) = rows[position].map != null

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val row = getItem(position)
            if (row.map == null) {
                return (convertView as? TextView ?: TextView(this@DownloadMapsActivity)).apply {
                    text = row.header
                    textSize = 12f
                    setTextColor(0xFF666666.toInt())
                    setPadding(dp(16), dp(16), dp(16), dp(6))
                }
            }

            val view = if (convertView == null || convertView is TextView) {
                layoutInflater.inflate(android.R.layout.simple_list_item_2, parent, false)
            } else {
                convertView
            }
            val map = row.map
            view.findViewById<TextView>(android.R.id.text1).text = map.getLocalizedName(locale)

            val tasks = GLMapManager.getDownloadTasks(map.mapID, GLMapInfo.DataSetMask.ALL).orEmpty()
            val total = tasks.sumOf { it.total.toLong() }
            val downloaded = tasks.sumOf { it.downloaded.toLong() }
            view.findViewById<TextView>(android.R.id.text2).text = when {
                map.isCollection -> "Browse regions"

                tasks.isNotEmpty() && total > 0 -> "Downloading ${tasks.size} data sets · ${downloaded * 100 / total}%"

                tasks.isNotEmpty() -> "Starting download..."

                map.dataSetsWithState(
                    GLMapInfo.State.DOWNLOADED
                ) != 0 -> "On device · %.1f MB".format(map.getSizeOnDisk(GLMapInfo.DataSetMask.ALL) / 1_000_000.0)

                else -> "%.1f MB".format(map.getSizeOnServer(GLMapInfo.DataSetMask.ALL) / 1_000_000.0)
            }
            return view
        }
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    companion object {
        private const val EXTRA_MAP_ID = "map_id"
    }
}
