package globus.demo

import android.app.Application
import globus.glmap.GLMapManager

class DemoApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // Get an API key at https://user.globus.software/apps/ and replace api_key in strings.xml.
        // Fail before Android creates Activities whose fields call native SDK APIs.
        // Returning from Application.onCreate would still allow those Activities to run.
        check(GLMapManager.Initialize(this, getString(R.string.api_key), null)) {
            "GLMap initialization failed. Check free storage space and storage-directory permissions."
        }
        GLMapManager.SetTileDownloadingAllowed(true)
    }
}
