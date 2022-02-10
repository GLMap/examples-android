package globus.javaDemo;

import androidx.multidex.MultiDexApplication;

import globus.glmap.GLMapManager;

/** Created by destman on 10/18/17. */
public class DemoApp extends MultiDexApplication {
    @Override
    public void onCreate() {
        super.onCreate();
        // Uncomment and insert your API key into api_key in res/values/strings.xml
        String apiKey = this.getString(R.string.api_key);
        if (!GLMapManager.Initialize(this, apiKey, null)) {
            // Error caching resources. Check free space for world database (~25MB)
        }
    }
}
