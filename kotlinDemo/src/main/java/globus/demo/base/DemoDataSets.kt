package globus.demo.base

import android.content.res.AssetManager
import globus.glmap.GLMapBBox
import globus.glmap.GLMapManager
import java.io.File

/** Custom datasets live in the shared SDK manager, not in an Activity. */
internal object DemoDataSets {
    private val registered = mutableSetOf<Pair<Int, String>>()

    @Synchronized
    fun registerAsset(dataSet: Int, name: String, assets: AssetManager): Boolean {
        val key = dataSet to "asset:$name"
        if (key in registered) return true
        if (!GLMapManager.AddDataSet(dataSet, null, name, assets, null)) return false
        registered += key
        return true
    }

    @Synchronized
    fun registerFile(dataSet: Int, bbox: GLMapBBox, file: File): Boolean {
        val key = dataSet to file.absolutePath
        if (key in registered) return true
        if (!file.isFile || file.length() == 0L) return false
        if (!GLMapManager.AddDataSet(dataSet, bbox, file.absolutePath, null, null)) return false
        registered += key
        return true
    }

    /** Publish only complete downloads. Never overwrite or delete a registered dataset. */
    @Synchronized
    fun install(dataSet: Int, bbox: GLMapBBox, temporary: File, destination: File): Boolean {
        if (destination.exists()) return registerFile(dataSet, bbox, destination)
        if (!temporary.renameTo(destination)) return false
        return registerFile(dataSet, bbox, destination)
    }
}
