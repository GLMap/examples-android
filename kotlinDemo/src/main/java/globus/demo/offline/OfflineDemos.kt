package globus.demo.offline

import android.app.AlertDialog
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
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
        addTopView(status)
        downloadBBoxData(
            bbox,
            listOf(
                GLMapInfo.DataSet.MAP to "bbox_map.vmtar",
                GLMapInfo.DataSet.NAVIGATION to "bbox_nav.navtar",
                GLMapInfo.DataSet.ELEVATION to "bbox_ele.eletar",
            ),
        ) { error ->
            if (error != null) {
                status.text = "Download failed"
                showError(error)
            } else {
                renderer.drawElevationLines = true
                renderer.drawHillshades = true
                renderer.reloadTiles()
                status.text = "All data downloaded"
            }
        }
    }
}

class DownloadMapsActivity : AppCompatActivity(), GLMapManager.StateListener {
    private val locale = GLMapLocaleSettings(arrayOf("en", "native"), GLMapLocaleSettings.UnitSystem.International)
    private lateinit var adapter: MapsAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = "Download Maps"
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        adapter = MapsAdapter()
        val list = ListView(this).apply {
            this.adapter = this@DownloadMapsActivity.adapter
            setOnItemClickListener { _, _, position, _ -> toggleMap(this@DownloadMapsActivity.adapter.getItem(position)!!) }
        }
        setContentView(list)
        applyContentInsets(list)
        GLMapManager.addStateListener(this)
        reloadMaps()
        GLMapManager.UpdateMapList callback@{ _, error ->
            if (isDestroyed) return@callback
            if (error != null) Toast.makeText(this, error.toString(), Toast.LENGTH_LONG).show()
            reloadMaps()
        }
    }

    private fun reloadMaps() {
        val maps = GLMapManager.GetChildMaps()
            .filterNot(GLMapInfo::isCollection)
            .sortedBy { it.getLocalizedName(locale) }
        adapter.clear()
        adapter.addAll(maps)
        adapter.notifyDataSetChanged()
    }

    private fun toggleMap(map: GLMapInfo) {
        val tasks = GLMapManager.getDownloadTasks(map.mapID, GLMapInfo.DataSetMask.ALL)
        if (!tasks.isNullOrEmpty()) {
            tasks.forEach(GLMapDownloadTask::cancel)
            return
        }
        if (map.dataSetsWithState(GLMapInfo.State.DOWNLOADED) != 0) {
            AlertDialog.Builder(this)
                .setMessage("Delete ${map.getLocalizedName(locale)} from this device?")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Delete") { _, _ -> GLMapManager.DeleteDataSets(map, GLMapInfo.DataSetMask.ALL) }
                .show()
        } else {
            GLMapManager.DownloadDataSets(map, GLMapInfo.DataSetMask.ALL)
        }
    }

    override fun onStartDownloading(task: GLMapDownloadTask) = notifyChanged()
    override fun onDownloadProgress(task: GLMapDownloadTask) = notifyChanged()
    override fun onFinishDownloading(task: GLMapDownloadTask) = notifyChanged()
    override fun onStateChanged(map: GLMapInfo?, dataSet: Int) = notifyChanged()

    private fun notifyChanged() {
        if (!isDestroyed) adapter.notifyDataSetChanged()
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

    private inner class MapsAdapter : ArrayAdapter<GLMapInfo>(this@DownloadMapsActivity, android.R.layout.simple_list_item_2) {
        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val view = super.getView(position, convertView, parent)
            val map = getItem(position)!!
            view.findViewById<TextView>(android.R.id.text1).text = map.getLocalizedName(locale)

            val task = GLMapManager.getDownloadTasks(map.mapID, GLMapInfo.DataSetMask.ALL)?.firstOrNull()
            view.findViewById<TextView>(android.R.id.text2).text = when {
                task != null && task.total > 0 -> "Downloading ${task.downloaded.toLong() * 100 / task.total}%"
                task != null -> "Starting download..."
                map.dataSetsWithState(GLMapInfo.State.DOWNLOADED) != 0 -> "On device · %.1f MB".format(map.getSizeOnDisk(GLMapInfo.DataSetMask.ALL) / 1_000_000.0)
                else -> "%.1f MB".format(map.getSizeOnServer(GLMapInfo.DataSetMask.ALL) / 1_000_000.0)
            }
            return view
        }
    }
}
