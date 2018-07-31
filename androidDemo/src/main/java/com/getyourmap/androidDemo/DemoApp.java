package com.getyourmap.androidDemo;

import android.support.multidex.MultiDexApplication;
import com.getyourmap.glmap.GLMapManager;

/** Created by destman on 10/18/17. */
public class DemoApp extends MultiDexApplication {
  @Override
  public void onCreate() {
    super.onCreate();
    if (!GLMapManager.Initialize(this, this.getString(R.string.api_key), null)) { // Uncomment and insert your API key into api_key in res/values/strings.xml
      // Error caching resources. Check free space for world database (~25MB)
    }
  }
}
