package com.getyourmap.androidDemo;

import android.app.Application;

import com.glmapview.GLMapManager;

/**
 * Created by destman on 10/18/17.
 */

public class DemoApp extends Application
{

    @Override
    public void onCreate()
    {
        super.onCreate();
        if(!GLMapManager.initialize(this, this.getString(R.string.api_key), null))
        {
            //Error caching resources. Check free space for world database (~25MB)
        }
    }
}
