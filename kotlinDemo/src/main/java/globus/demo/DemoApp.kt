package globus.demo

import android.app.Application
import android.util.Log
import globus.glmap.GLMapManager
import globus.glsearch.GLSearch

class DemoApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // Get an API key at https://user.globus.software/apps/ and replace api_key in strings.xml.
        if (!GLMapManager.Initialize(this, getString(R.string.api_key), null)) {
            Log.e("GLMapDemo", "GLMap initialization failed. Check the API key and free storage space.")
        }
        GLSearch.Initialize(this)
        GLMapManager.SetTileDownloadingAllowed(true)
    }
}
