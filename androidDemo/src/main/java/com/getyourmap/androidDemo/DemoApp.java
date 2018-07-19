package com.getyourmap.androidDemo;

import android.support.multidex.MultiDexApplication;
import com.glmapview.GLMapManager;

/** Created by destman on 10/18/17. */
public class DemoApp extends MultiDexApplication {
  @Override
  public void onCreate() {
    super.onCreate();
    if (!GLMapManager.Initialize(this, this.getString(R.string.api_key), null)) {
      // Error caching resources. Check free space for world database (~25MB)
    }
  }
}
