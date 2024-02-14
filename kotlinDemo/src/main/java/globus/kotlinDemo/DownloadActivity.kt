package globus.kotlinDemo

import android.annotation.SuppressLint
import android.app.ListActivity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.*
import android.view.ContextMenu.ContextMenuInfo
import android.widget.*
import android.widget.AdapterView.OnItemClickListener
import android.widget.AdapterView.OnItemLongClickListener
import globus.glmap.*
import globus.glmap.GLMapManager
import java.util.*

@SuppressLint("SetTextI18n")
class DownloadActivity : ListActivity(), GLMapManager.StateListener {
    private enum class ContextItems {
        DELETE
    }

    private var center: MapPoint? = null
    private var selectedMap: GLMapInfo? = null
    private var localeSettings = GLMapLocaleSettings()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.download)
        registerForContextMenu(listView)
        GLMapManager.addStateListener(this)
        val i = intent
        center = MapPoint(i.getDoubleExtra("cx", 0.0), i.getDoubleExtra("cy", 0.0))
        val collectionID = i.getLongExtra("collectionID", 0)
        if (collectionID != 0L) {
            updateAllItems(GLMapManager.GetMapWithID(collectionID)?.maps)
        } else {
            updateAllItems(GLMapManager.GetMaps())
            GLMapManager.UpdateMapList { updateAllItems(GLMapManager.GetMaps()) }
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onDestroy() {
        GLMapManager.removeStateListener(this)
        super.onDestroy()
    }

    private inner class MapsAdapter internal constructor(
        val maps: Array<GLMapInfo>,
        private val context: Context
    ) : BaseAdapter(), ListAdapter {
        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val map = maps[position]
            val convertViewFinal = convertView ?: LayoutInflater.from(context).inflate(R.layout.map_name, parent, false)
            val txtHeaderName = convertViewFinal.findViewById<TextView>(android.R.id.text1)
            val txtDescription = convertViewFinal.findViewById<TextView>(android.R.id.text2)
            txtHeaderName.text = map.getLocalizedName(localeSettings)
            val tasks = GLMapManager.getDownloadTasks(map.mapID, GLMapInfo.DataSetMask.ALL)
            if (!tasks.isNullOrEmpty()) {
                var total: Long = 0
                var downloaded: Long = 0
                for (task in tasks) {
                    total += task.total.toLong()
                    downloaded += task.downloaded.toLong()
                }
                val progress: Float
                progress = if (total != 0L) 100.0f * downloaded / total else 0f
                txtDescription.text = String.format(Locale.ENGLISH, "Download %.2f%%", progress)
            } else if (map.isCollection) {
                txtDescription.text = "Collection"
            } else if (anyDataSetHaveState(map, GLMapInfo.State.NEED_UPDATE)) {
                txtDescription.text = "Need update"
            } else if (anyDataSetHaveState(map, GLMapInfo.State.NEED_RESUME)) {
                txtDescription.text = "Need resume"
            } else {
                var size = map.getSizeOnDisk(GLMapInfo.DataSetMask.ALL)
                if (size == 0L) size = map.getSizeOnServer(GLMapInfo.DataSetMask.ALL)
                txtDescription.text = NumberFormatter.formatSize(size)
            }
            return convertViewFinal
        }

        override fun getCount() = maps.size
        override fun getItem(position: Int) = maps[position]
        override fun getItemId(position: Int) = position.toLong()
    }

    override fun onStartDownloading(task: GLMapDownloadTask) {}
    override fun onDownloadProgress(task: GLMapDownloadTask) {
        (listView.adapter as MapsAdapter).notifyDataSetChanged()
    }

    override fun onFinishDownloading(task: GLMapDownloadTask) {}
    override fun onStateChanged(map: GLMapInfo, @GLMapInfo.DataSet dataSet: Int) {
        (listView.adapter as MapsAdapter).notifyDataSetChanged()
    }

    override fun onCreateContextMenu(menu: ContextMenu, v: View, menuInfo: ContextMenuInfo) {
        super.onCreateContextMenu(menu, v, menuInfo)
        val selectedMap = selectedMap
        if (selectedMap != null) {
            menu.setHeaderTitle(selectedMap.getLocalizedName(localeSettings))
            menu.add(0, ContextItems.DELETE.ordinal, ContextItems.DELETE.ordinal, "Delete")
        }
    }

    override fun onContextItemSelected(item: MenuItem): Boolean {
        val selectedMap = selectedMap ?: return false
        when (ContextItems.values()[item.itemId]) {
            ContextItems.DELETE -> {
                GLMapManager.DeleteDataSets(selectedMap, GLMapInfo.DataSetMask.ALL)
                (listView.adapter as MapsAdapter).notifyDataSetChanged()
            }
        }
        return true
    }

    private fun updateAllItems(maps: Array<GLMapInfo>?) {
        if (maps == null) return
        Arrays.sort(maps) { a, b -> a.getLocalizedName(localeSettings).compareTo(b.getLocalizedName(localeSettings)) }
        // Use GLMapManager.SortMaps(maps, center) to sort map array by distance from user location;
        val listView = findViewById<ListView>(android.R.id.list)
        listView.adapter = MapsAdapter(maps, this)
        listView.onItemClickListener = OnItemClickListener { _, _, position, _ ->
            val info = listView.adapter.getItem(position) as GLMapInfo
            if (info.isCollection) {
                val intent = Intent(this@DownloadActivity, DownloadActivity::class.java)
                intent.putExtra("collectionID", info.mapID)
                intent.putExtra("cx", center!!.x)
                intent.putExtra("cy", center!!.y)
                startActivity(intent)
            } else {
                val tasks = GLMapManager.getDownloadTasks(info.mapID, GLMapInfo.DataSetMask.ALL)
                if (!tasks.isNullOrEmpty()) {
                    for (task in tasks) task.cancel()
                } else {
                    GLMapManager.DownloadDataSets(info, GLMapInfo.DataSetMask.ALL)
                }
            }
        }
        listView.onItemLongClickListener = OnItemLongClickListener { _, _, position, _ ->
            val info = (listView.adapter as MapsAdapter).maps[position]
            if (isOnDevice(info)) {
                selectedMap = info
                false
            } else {
                true
            }
        }
    }

    companion object {
        fun anyDataSetHaveState(info: GLMapInfo, @GLMapInfo.State state: Int): Boolean {
            for (i in 0 until GLMapInfo.DataSet.COUNT) {
                if (info.getState(i) == state) return true
            }
            return false
        }
        fun isOnDevice(info: GLMapInfo): Boolean {
            return (
                anyDataSetHaveState(info, GLMapInfo.State.IN_PROGRESS) ||
                    anyDataSetHaveState(info, GLMapInfo.State.DOWNLOADED) ||
                    anyDataSetHaveState(info, GLMapInfo.State.NEED_RESUME) ||
                    anyDataSetHaveState(info, GLMapInfo.State.NEED_UPDATE) ||
                    anyDataSetHaveState(info, GLMapInfo.State.REMOVED)
                )
        }
    }
}
