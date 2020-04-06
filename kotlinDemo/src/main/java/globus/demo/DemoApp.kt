package globus.demo

import androidx.multidex.MultiDexApplication
import globus.glmap.GLMapManager

/** Created by destman on 10/18/17.  */
class DemoApp : MultiDexApplication() {
    override fun onCreate() {
        super.onCreate()
        // Uncomment and insert your API key into api_key in res/values/strings.xml
        if (!GLMapManager.Initialize(this, this.getString(R.string.api_key), null)) {
            // Error caching resources. Check free space for world database (~25MB)
        }
    }
}