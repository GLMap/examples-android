package com.getyourmap.androidDemo;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Point;
import android.graphics.Rect;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.view.GestureDetector;
import android.view.GestureDetector.SimpleOnGestureListener;
import android.view.Menu;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnTouchListener;
import android.widget.Button;
import android.widget.EditText;

import com.glmapview.GLMapBBox;
import com.glmapview.GLMapDownloadTask;
import com.glmapview.GLMapImage;
import com.glmapview.GLMapImageGroup;
import com.glmapview.GLMapImageGroupCallback;
import com.glmapview.GLMapInfo;
import com.glmapview.GLMapLocaleSettings;
import com.glmapview.GLMapManager;
import com.glmapview.GLMapMarkerImage;
import com.glmapview.GLMapMarkerLayer;
import com.glmapview.GLMapMarkerStyleCollection;
import com.glmapview.GLMapMarkerStyleCollectionDataCallback;
import com.glmapview.GLMapRasterTileSource;
import com.glmapview.GLMapVectorCascadeStyle;
import com.glmapview.GLMapVectorObject;
import com.glmapview.GLMapVectorObjectList;
import com.glmapview.GLMapVectorStyle;
import com.glmapview.GLMapView;
import com.glmapview.GLMapView.GLMapPlacement;
import com.glmapview.GLMapView.GLUnits;
import com.glmapview.MapGeoPoint;
import com.glmapview.MapPoint;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public class MapViewActivity extends Activity implements GLMapView.ScreenCaptureCallback, GLMapManager.StateListener {

	class Pin
	{
		public MapPoint pos;
		public int imageVariant;		
	}

	private GLMapImage image=null;
	private GLMapImageGroup imageGroup=null;
	private List<Pin> pins = new ArrayList<>();	
	private GestureDetector gestureDetector;
	private GLMapView mapView;
	private GLMapInfo mapToDownload=null;
	private Button btnDownloadMap;

	GLMapMarkerLayer markerLayer;
	GLMapLocaleSettings localeSettings;

	@Override
    protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.map);
		mapView = (GLMapView) this.findViewById(R.id.map_view);

		// Map list is updated, because download button depends on available map list and during first launch this list is empty
		GLMapManager.updateMapList(this, null);

		btnDownloadMap = (Button) this.findViewById(R.id.button_dl_map);
		btnDownloadMap.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				if (mapToDownload != null) {
					GLMapDownloadTask task = GLMapManager.getDownloadTask(mapToDownload);
					if (task != null)
					{
						task.cancel();
					} else {
						GLMapManager.createDownloadTask(mapToDownload, MapViewActivity.this).start();
					}
					updateMapDownloadButtonText();
				} else {
					Intent i = new Intent(v.getContext(), DownloadActivity.class);

                    MapPoint pt = mapView.getMapCenter();
					i.putExtra("cx", pt.x);
					i.putExtra("cy", pt.y);
					v.getContext().startActivity(i);
				}
			}
		});

		GLMapManager.addStateListener(this);

		localeSettings = new GLMapLocaleSettings();
		mapView.setLocaleSettings(localeSettings);
		mapView.loadStyle(getAssets(), "DefaultStyle.bundle");
		mapView.setUserLocationImages(
				mapView.imageManager.open("DefaultStyle.bundle/circle-new.svgpb", 1, 0),
				mapView.imageManager.open("DefaultStyle.bundle/arrow-new.svgpb", 1, 0));

		mapView.setShowsUserLocation(true);

		mapView.setScaleRulerStyle(GLUnits.SI, GLMapPlacement.BottomCenter, new MapPoint(10, 10), 200);
		mapView.setAttributionPosition(GLMapPlacement.TopCenter);

		Bundle b = getIntent().getExtras();
		final int example = b.getInt("example");

		if (example == SampleSelectActivity.Samples.MAP_EMBEDD.ordinal()) {
			if (!GLMapManager.AddMap(getAssets(), "Montenegro.vm", null)) {
				//Failed to unpack to caches. Check free space.
			}
			zoomToPoint();
		} else if (example == SampleSelectActivity.Samples.MAP_ONLINE.ordinal()) {
			GLMapManager.SetAllowedTileDownload(true);
		} else if (example == SampleSelectActivity.Samples.MAP_ONLINE_RASTER.ordinal()) {

			mapView.setRasterTileSources(new GLMapRasterTileSource[]{ new OSMTileSource(this) } );
		} else if (example == SampleSelectActivity.Samples.ZOOM_BBOX.ordinal()) {
            zoomToBBox();
        } else if (example == SampleSelectActivity.Samples.FLY_TO.ordinal()) {

			mapView.setMapCenter( MapPoint.CreateFromGeoCoordinates(37.3257, -122.0353), false);
			mapView.setMapZoom(14, false);

			final Button btn = (Button) this.findViewById(R.id.button_action);
			btn.setVisibility(View.VISIBLE);
			btn.setText("Fly");
			btn.setOnClickListener(new OnClickListener() {
				@Override
				public void onClick(View v) {
					double min_lat = 33;
					double max_lat = 48;
					double min_lon = -118;
					double max_lon = -85;

                    double lat = min_lat + (max_lat-min_lat) * Math.random();
                    double lon = min_lon + (max_lon-min_lon) * Math.random();

                    MapGeoPoint geoPoint = new MapGeoPoint(lat, lon);

					mapView.flyTo(geoPoint, 15, 0, 0);
				}
			});
			GLMapManager.SetAllowedTileDownload(true);
		} else if (example == SampleSelectActivity.Samples.MARKERS.ordinal()) {
            mapView.setLongClickable(true);

            gestureDetector = new GestureDetector(this, new SimpleOnGestureListener() {
                @Override
                public boolean onSingleTapConfirmed(MotionEvent e) {
                    deleteMarker(e.getX(), e.getY());
                    return true;
                }

                @Override
                public void onLongPress(MotionEvent e) {
                    addMarker(e.getX(), e.getY());
                }
            });

            mapView.setOnTouchListener(new OnTouchListener() {
                @Override
                public boolean onTouch(View arg0, MotionEvent ev) {
                    return gestureDetector.onTouchEvent(ev);
                }
            });

            addMarkers();
            GLMapManager.SetAllowedTileDownload(true);
        } else if (example == SampleSelectActivity.Samples.MARKERS_MAPCSS.ordinal()) {
			addMarkersWithMapcss();

			gestureDetector = new GestureDetector(this, new SimpleOnGestureListener() {
				@Override
				public boolean onSingleTapConfirmed(MotionEvent e) {
					deleteMarker(e.getX(), e.getY());
					return true;
				}

				@Override
				public void onLongPress(MotionEvent e) {
					addMarkerAsVectorObject(e.getX(), e.getY());
				}
			});

			mapView.setOnTouchListener(new OnTouchListener() {
				@Override
				public boolean onTouch(View arg0, MotionEvent ev) {
					return gestureDetector.onTouchEvent(ev);
				}
			});

			GLMapManager.SetAllowedTileDownload(true);
		}else if (example == SampleSelectActivity.Samples.MULTILINE.ordinal()) {
			addMultiline();
		} else if (example == SampleSelectActivity.Samples.POLYGON.ordinal()) {
			addPolygon();
		} else if (example == SampleSelectActivity.Samples.CAPTURE_SCREEN.ordinal()) {
			zoomToPoint();
			captureScreen();
		} else if (example == SampleSelectActivity.Samples.IMAGE_SINGLE.ordinal()) {
			final Button btn = (Button) this.findViewById(R.id.button_action);
			btn.setVisibility(View.VISIBLE);
			delImage(btn);
		} else if (example == SampleSelectActivity.Samples.IMAGE_MULTI.ordinal()) {
			mapView.setLongClickable(true);

			gestureDetector = new GestureDetector(this, new SimpleOnGestureListener() {
				@Override
				public boolean onSingleTapConfirmed(MotionEvent e) {
					deletePin(e.getX(), e.getY());
					return true;
				}

				@Override
				public void onLongPress(MotionEvent e) {
					addPin(e.getX(), e.getY());
				}
			});

			mapView.setOnTouchListener(new OnTouchListener() {
				@Override
				public boolean onTouch(View arg0, MotionEvent ev) {
					return gestureDetector.onTouchEvent(ev);
				}
			});

		} else if (example == SampleSelectActivity.Samples.GEO_JSON.ordinal()) {
			loadGeoJSON();
		} else if (example == SampleSelectActivity.Samples.STYLE_LIVE_RELOAD.ordinal()) {
			styleLiveReload();
		}

		mapView.setCenterTileStateChangedCallback(new Runnable() {
			@Override
			public void run() {
				runOnUiThread(new Runnable() {
					@Override
					public void run() {
						updateMapDownloadButton();
					}
				});
			}
		});
		mapView.setMapDidMoveCallback(new Runnable() {
			@Override
			public void run() {

				if (example == SampleSelectActivity.Samples.CALLBACK_TEST.ordinal()) {
					Log.w("GLMapView", "Did move");
				}
				runOnUiThread(new Runnable() {
					@Override
					public void run() {
						updateMapDownloadButtonText();
					}
				});
			}
		});
	}

	@Override
	protected void onDestroy()
	{
		GLMapManager.removeStateListener(this);
		if(markerLayer!=null)
		{
			markerLayer.dispose();
			markerLayer = null;
		}
		super.onDestroy();
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) 
	{
    	MapPoint pt = new MapPoint(mapView.getWidth()/2, mapView.getHeight()/2);
		mapView.changeMapZoom(-1, pt, true);
	    return false;
	}

	@Override
	public void onStartDownloading(GLMapInfo map)
	{

	}

	@Override
	public void onDownloadProgress(GLMapInfo map)
	{
		updateMapDownloadButtonText();
	}

	@Override
	public void onFinishDownloading(GLMapInfo map)
	{
		mapView.reloadTiles();
	}

	@Override
	public void onStateChanged(GLMapInfo map)
	{
		updateMapDownloadButtonText();
	}

	void updateMapDownloadButtonText()
	{
		if(btnDownloadMap.getVisibility()==View.VISIBLE)
		{
			MapPoint center = mapView.getMapCenter();

			mapToDownload = GLMapManager.MapAtPoint(center);

			if (mapToDownload != null)
			{
				String text;
				if(mapToDownload.getState() == GLMapInfo.State.IN_PROGRESS)
				{
					text = String.format(Locale.getDefault(), "Downloading %s %d%%", mapToDownload.getLocalizedName(localeSettings), (int)(mapToDownload.getDownloadProgress()*100));
				}else
				{
					text = String.format(Locale.getDefault(), "Download %s", mapToDownload.getLocalizedName(localeSettings));
				}
				btnDownloadMap.setText(text);
			} else {
				btnDownloadMap.setText("Download maps");
			}
		}
	}

	void updateMapDownloadButton()
	{
		switch (mapView.getCenterTileState())
		{
			case NoData:
			{
				if(btnDownloadMap.getVisibility()==View.INVISIBLE)
				{
					btnDownloadMap.setVisibility(View.VISIBLE);
					btnDownloadMap.getParent().requestLayout();
					updateMapDownloadButtonText();
				}
				break;
			}

			case Loaded:
			{
				if(btnDownloadMap.getVisibility()==View.VISIBLE)
				{
					btnDownloadMap.setVisibility(View.INVISIBLE);
				}
				break;
			}
			case Unknown:
				break;
		}
	}

	// Example how to calcludate zoom level for some bbox
	void zoomToBBox()
	{
		mapView.doWhenSurfaceCreated(new Runnable(){
			@Override
			public void run()
			{
				GLMapBBox bbox = new GLMapBBox();
				bbox.addPoint(MapPoint.CreateFromGeoCoordinates(52.5037, 13.4102)); // Berlin
				bbox.addPoint(MapPoint.CreateFromGeoCoordinates(53.9024, 27.5618)); // Minsk

			    mapView.setMapCenter(bbox.center(), false);
				mapView.setMapZoom(mapView.mapZoomForBBox(bbox, mapView.getWidth(), mapView.getHeight()), false);
			}
		});
	}
	    
    void zoomToPoint()
    {
    	//New York
    	//MapPoint pt = new MapPoint(-74.0059700 , 40.7142700	);
    	
    	//Belarus
    	//MapPoint pt = new MapPoint(27.56, 53.9);
    	//;

		// Move map to the Montenegro capital
		MapPoint pt = MapPoint.CreateFromGeoCoordinates(42.4341, 19.26);
    	GLMapView mapView = (GLMapView) this.findViewById(R.id.map_view);
    	mapView.setMapCenter(pt, false);
    	mapView.setMapZoom(16, false);
    }
          
    void addPin(float touchX, float touchY)
    {
    	if(imageGroup == null)
    	{
    		final Bitmap images[] = new Bitmap[3];    		
    		images[0] = mapView.imageManager.open("1.svgpb", 1, 0xFFFF0000);
    		images[1] = mapView.imageManager.open("2.svgpb", 1, 0xFF00FF00);
    		images[2] = mapView.imageManager.open("3.svgpb", 1, 0xFF0000FF);
    		
        	class Callback implements GLMapImageGroupCallback {
        		@Override
        		public int getImagesCount() 
        		{
        			return pins.size();
        		}

        		@Override
        		public int getImageIndex(int i) 
        		{
        			return pins.get(i).imageVariant;
        		}

        		@Override
        		public MapPoint getImagePos(int i)
        		{
        			return pins.get(i).pos;
        		}

				@Override
				public void updateStarted()
				{
					Log.i("GLMapImageGroupCallback", "Update started");
				}

				@Override
				public void updateFinished()
				{
					Log.i("GLMapImageGroupCallback", "Update finished");
				}

				@Override
        		public int getImageVariantsCount()
        		{
        			return images.length;
        		}
        		
        		@Override        		
        		public Bitmap getImageVariantBitmap(int i)
        		{
        			return images[i];
        		}
        		@Override        		
        		public MapPoint getImageVariantOffset(int i)
        		{
        			return new MapPoint(images[i].getWidth()/2, 0);
        		}
            }          	    		
    		imageGroup = mapView.createImageGroup(new Callback());    		
    	}    	
    	
    	MapPoint pt = mapView.convertDisplayToInternal(new MapPoint(touchX, touchY));
    	
    	Pin pin = new Pin();
    	pin.pos = pt;
    	pin.imageVariant = pins.size() % 3;    	
    	pins.add( pin );    	
    	imageGroup.setNeedsUpdate();    	   	
    }
    
    
    void deletePin(float touchX, float touchY)
    {
    	for(int i=0; i<pins.size(); ++i)
    	{    	    	
    		MapPoint pos = pins.get(i).pos;
    		MapPoint screenPos = mapView.convertInternalToDisplay(new MapPoint(pos));
    		
    		Rect rt = new Rect(-40,-40,40,40);
    		rt.offset( (int)screenPos.x, (int)screenPos.y );
    		if(rt.contains((int)touchX, (int)touchY))
    		{
    			pins.remove(i);
    			imageGroup.setNeedsUpdate();
    			break;	
    		}    		    		    		
    	}
    }

	void deleteMarker(float x, float y)
	{
		if(markerLayer != null)
		{
			Object markersToRemove[] = markerLayer.objectsNearPoint(mapView, mapView.convertDisplayToInternal(new MapPoint(x, y)), 30);
			if (markersToRemove != null && markersToRemove.length == 1)
			{
				markerLayer.modify(null, Collections.singleton(markersToRemove[0]), null, true, new Runnable()
				{
					@Override
					public void run()
					{
						Log.d("MarkerLayer", "Marker deleted");
					}
				});
			}
		}
	}

	void addMarker(float x, float y)
	{
		if(markerLayer != null)
		{
			MapPoint newMarkers[] = new MapPoint[1];
			newMarkers[0] = mapView.convertDisplayToInternal(new MapPoint(x, y));

			markerLayer.modify(newMarkers, null, null, true, new Runnable()
			{
				@Override
				public void run()
				{
					Log.d("MarkerLayer", "Marker added");
				}
			});
		}
	}

	void addMarkerAsVectorObject(float x, float y)
	{
		if(markerLayer != null)
		{
			GLMapVectorObject newMarkers[] = new GLMapVectorObject[1];
			newMarkers[0] = GLMapVectorObject.createPoint(mapView.convertDisplayToInternal(new MapPoint(x, y)));

			markerLayer.modify(newMarkers, null, null, true, new Runnable()
			{
				@Override
				public void run()
				{
					Log.d("MarkerLayer", "Marker added");
				}
			});
		}
	}

	static private int unionColours[] = {
			Color.argb(255, 33, 0, 255),
			Color.argb(255, 68, 195, 255),
			Color.argb(255, 63, 237, 198),
			Color.argb(255, 15, 228, 36),
			Color.argb(255, 168, 238, 25),
			Color.argb(255, 214, 234, 25),
			Color.argb(255, 223, 180, 19),
			Color.argb(255, 255, 0, 0)
	};

	void addMarkersWithMapcss()
	{
		final GLMapMarkerStyleCollection styleCollection = new GLMapMarkerStyleCollection();
		for(int i=0; i<unionColours.length; i++)
		{
			float scale = (float)(0.2+0.1*i);
			int index = styleCollection.addStyle(new GLMapMarkerImage("marker"+scale, mapView.imageManager.open("cluster.svgpb", scale, unionColours[i])));
			styleCollection.setStyleName(i, "uni"+index);
		}

		final GLMapVectorCascadeStyle style = GLMapVectorCascadeStyle.createStyle(
				"node{icon-image:\"uni0\"; text:eval(tag(\"name\")); text-color:#2E2D2B; font-size:12; font-stroke-width:1pt; font-stroke-color:#FFFFFFEE;}" +
				"node[count>=2]{icon-image:\"uni1\"; text:eval(tag(\"count\"));}" +
				"node[count>=4]{icon-image:\"uni2\";}" +
				"node[count>=8]{icon-image:\"uni3\";}" +
				"node[count>=16]{icon-image:\"uni4\";}" +
				"node[count>=32]{icon-image:\"uni5\";}" +
				"node[count>=64]{icon-image:\"uni6\";}" +
				"node[count>=128]{icon-image:\"uni7\";}");

		new AsyncTask<Void, Void, GLMapMarkerLayer>()
		{
			@Override
			protected GLMapMarkerLayer doInBackground(Void... voids)
			{
				GLMapMarkerLayer rv;
				try
				{
					Log.w("GLMapView", "Start parsing");
					GLMapVectorObjectList objects = GLMapVectorObject.createFromGeoJSONStream(getAssets().open("cluster_data.json"));
					Log.w("GLMapView", "Finish parsing");

					final GLMapBBox bbox = objects.getBBox();
					runOnUiThread(new Runnable()
					{
						@Override
						public void run()
						{
							mapView.setMapCenter(bbox.center(), false);
							mapView.setMapZoom(mapView.mapZoomForBBox(bbox, mapView.getWidth(), mapView.getHeight()), false);
						}
					});

					Log.w("GLMapView", "Start creating layer");
					rv = new GLMapMarkerLayer(objects, style, styleCollection);
					Log.w("GLMapView", "Finish creating layer");
					objects.dispose();
				} catch (Exception e)
				{
					rv = null;
				}
				return rv;
			}

			@Override
			protected void onPostExecute(GLMapMarkerLayer layer)
			{
				if(layer != null)
				{
					markerLayer = layer;
					mapView.displayMarkerLayer(layer);
				}
			}
		}.execute();
	}

	void addMarkers()
	{
		final GLMapMarkerStyleCollection style = new GLMapMarkerStyleCollection();
		final int unionCounts[] = {1, 2, 4, 8, 16, 32, 64, 128};
		for(int i=0; i<unionCounts.length; i++)
		{
			float scale = (float)(0.2+0.1*i);
			style.addStyle(new GLMapMarkerImage("marker"+scale, mapView.imageManager.open("cluster.svgpb", scale, unionColours[i])));
		}

		final GLMapVectorStyle textStyle = GLMapVectorStyle.createStyle("{text-color:black;font-size:12;font-stroke-width:1pt;font-stroke-color:#FFFFFFEE;}");
		style.setDataCallback(new GLMapMarkerStyleCollectionDataCallback()
		{
			@Override
			public void fillUnionData(int markersCount, long nativeMarker)
			{
				for(int i=unionCounts.length-1; i>=0; i--)
				{
					if(markersCount > unionCounts[i])
					{
						GLMapMarkerStyleCollection.setMarkerStyle(nativeMarker, i);
						break;
					}
				}
				GLMapMarkerStyleCollection.setMarkerText(nativeMarker, Integer.toString(markersCount) , new Point(0, 0), textStyle);
			}

			@Override
			public void fillData(Object marker, long nativeMarker)
			{
				if(marker instanceof MapPoint)
				{
					GLMapMarkerStyleCollection.setMarkerLocation(nativeMarker, (MapPoint) marker);
					GLMapMarkerStyleCollection.setMarkerText(nativeMarker, "Test", new Point(0,0), textStyle);
				}else if(marker instanceof GLMapVectorObject)
				{
					GLMapVectorObject obj = (GLMapVectorObject)marker;
					GLMapMarkerStyleCollection.setMarkerLocationFromVectorObject(nativeMarker, obj);
					String name = obj.valueForKey("name");
					if(name!=null)
					{
						GLMapMarkerStyleCollection.setMarkerText(nativeMarker, name, new Point(0, 15/2), textStyle);
					}
				}
				GLMapMarkerStyleCollection.setMarkerStyle(nativeMarker, 0);
			}
		});

		new AsyncTask<Void, Void, GLMapMarkerLayer>()
		{
			@Override
			protected GLMapMarkerLayer doInBackground(Void... voids)
			{
				GLMapMarkerLayer rv;
				try
				{
					Log.w("GLMapView", "Start parsing");
					GLMapVectorObjectList objects = GLMapVectorObject.createFromGeoJSONStream(getAssets().open("cluster_data.json"));
					Log.w("GLMapView", "Finish parsing");

					final GLMapBBox bbox = objects.getBBox();
					runOnUiThread(new Runnable()
					{
						@Override
						public void run()
						{
							mapView.setMapCenter(bbox.center(), false);
							mapView.setMapZoom(mapView.mapZoomForBBox(bbox, mapView.getWidth(), mapView.getHeight()), false);
						}
					});

					Log.w("GLMapView", "Start creating layer");
					rv = new GLMapMarkerLayer(objects.toArray(), style);
					Log.w("GLMapView", "Finish creating layer");
					objects.dispose();
				} catch (Exception e)
				{
					rv = null;
				}
				return rv;
			}

			@Override
			protected void onPostExecute(GLMapMarkerLayer layer)
			{
				if(layer != null)
				{
					markerLayer = layer;
					mapView.displayMarkerLayer(layer);
				}
			}

		}.execute();
	}

    
    void addImage(final Button btn)
    {
    	Bitmap bmp = mapView.imageManager.open("arrow-maphint.svgpb", 1, 0);
    	image = mapView.displayImage(bmp);
    	image.setOffset(new MapPoint(bmp.getWidth(), bmp.getHeight()/2));
    	image.setRotatesWithMap(true);
    	image.setAngle((float)Math.random()*360);

    	image.setPosition(mapView.getMapCenter());

		btn.setText("Move image");
    	btn.setOnClickListener(new OnClickListener(){
			@Override
			public void onClick(View v) {
				moveImage(btn);
			}
    	});    	
    }
    
    void moveImage(final Button btn)
	{
		image.setPosition(mapView.getMapCenter());
		btn.setText("Remove image");
		btn.setOnClickListener(new OnClickListener()
		{
			@Override
			public void onClick(View v)
			{
				delImage(btn);
			}
		});
	}
    
    void delImage(final Button btn)
    {
		if(image != null)
		{
			image.dispose();
			image = null;
		}
		btn.setText("Add image");
    	btn.setOnClickListener(new OnClickListener(){
			@Override
			public void onClick(View v) {
				addImage(btn);
			}
    	});     	
    }
    
    void addMultiline()
    {
        MapPoint[] line1 = new MapPoint[5];
        line1[0] = new MapPoint(27.7151, 53.8869); // Minsk
        line1[1] = new MapPoint(30.5186, 50.4339); // Kiev
        line1[2] = new MapPoint(21.0103, 52.2251); // Warsaw
        line1[3] = new MapPoint(13.4102, 52.5037); // Berlin
        line1[4] = new MapPoint(2.3343, 48.8505); // Paris
        
        MapPoint[] line2 = new MapPoint[3];
        line2[0] = new MapPoint(4.9021, 52.3690); // Amsterdam
        line2[1] = new MapPoint(4.3458, 50.8263); // Brussel
        line2[2] = new MapPoint(6.1296, 49.6072); // Luxembourg
       
        MapPoint[][] multiline = {line1, line2};

		final GLMapVectorObject obj = GLMapVectorObject.createMultiline(multiline);
		// style applied to all lines added. Style is string with mapcss rules. Read more in manual.
		mapView.addVectorObjectWithStyle(obj, GLMapVectorCascadeStyle.createStyle("line{width: 2pt;color:green;layer:100;}"), null);
     }
    
    void addPolygon()
    {
        int pointCount = 25;
        MapPoint[] outerRing = new MapPoint[pointCount];
        MapPoint[] innerRing = new MapPoint[pointCount];
        
        float rOuter = 20, rInner = 10;
        float cx = 30, cy = 30;

        // let's display circle
        for (int i=0; i<pointCount; i++) 
        {
        	outerRing[i] = new MapPoint(cx + Math.sin(2*Math.PI / pointCount * i) * rOuter,
                                      cy + Math.cos(2*Math.PI / pointCount * i) * rOuter);
        	
        	innerRing[i] =  new MapPoint(cx + Math.sin(2*Math.PI / pointCount * i) * rInner,
                    cy + Math.cos(2*Math.PI / pointCount * i) * rInner);        	
        }
        
        MapPoint[][] outerRings = {outerRing};
        MapPoint[][] innerRings = {innerRing};

		GLMapVectorObject obj = GLMapVectorObject.createPolygon(outerRings, innerRings);
		mapView.addVectorObjectWithStyle(obj, GLMapVectorCascadeStyle.createStyle("area{fill-color:#10106050; fill-color:#10106050; width:4pt; color:green;}"), null); // #RRGGBBAA format
    }

	private void styleLiveReload()
	{
		final EditText editText = (EditText) this.findViewById(R.id.edit_text);
		editText.setVisibility(View.VISIBLE);

		final Button btn = (Button) this.findViewById(R.id.button_action);
		btn.setVisibility(View.VISIBLE);
		btn.setText("Reload");
		btn.setOnClickListener(new OnClickListener()
		{
			@Override
			public void onClick(View view)
			{
				new AsyncTask<String, String, byte[]>()
				{
					@Override
					protected byte[] doInBackground(String... strings)
					{
						byte rv[];
						try
						{
							URLConnection connection = new URL(strings[0]).openConnection();
							connection.connect();
							InputStream inputStream = connection.getInputStream();

							ByteArrayOutputStream buffer = new ByteArrayOutputStream();
							int nRead;
							byte[] data = new byte[16384];
							while ((nRead = inputStream.read(data, 0, data.length)) != -1)
							{
								buffer.write(data, 0, nRead);
							}
							buffer.flush();
							rv = buffer.toByteArray();
							buffer.close();
							inputStream.close();
						} catch (Exception ignore)
						{
							rv = null;
						}
						return rv;
					}

					@Override
					protected void onPostExecute(final byte newStyleData[])
					{
						mapView.loadStyle(new GLMapView.ResourceLoadCallback()
						{
							@Override
							public byte[] loadResource(String name)
							{
								byte rv[];
								if(name.equals("Style.mapcss"))
								{
									rv = newStyleData;
								}else
								{
									try
									{
										InputStream stream = getAssets().open("DefaultStyle.bundle/" + name);
										rv = new byte[stream.available()];
										if (stream.read(rv) < rv.length)
										{
											rv = null;
										}
										stream.close();
									} catch (IOException ignore)
									{
										rv = null;
									}
								}
								return rv;
							}
						});
					}
				}.execute(editText.getText().toString());
			}
		});
	}
    
	private void loadGeoJSON() 
	{	
		GLMapVectorObjectList objects = GLMapVectorObject.createFromGeoJSON(
				"[{\"type\": \"Feature\", \"geometry\": {\"type\": \"Point\", \"coordinates\": [30.5186, 50.4339]}, \"properties\": {\"id\": \"1\", \"text\": \"test1\"}},"
				+ "{\"type\": \"Feature\", \"geometry\": {\"type\": \"Point\", \"coordinates\": [27.7151, 53.8869]}, \"properties\": {\"id\": \"2\", \"text\": \"test2\"}},"
				+ "{\"type\":\"LineString\",\"coordinates\": [ [27.7151, 53.8869], [30.5186, 50.4339], [21.0103, 52.2251], [13.4102, 52.5037], [2.3343, 48.8505]]},"
				+ "{\"type\":\"Polygon\",\"coordinates\":[[ [0.0, 10.0], [10.0, 10.0], [10.0, 20.0], [0.0, 20.0] ],[ [2.0, 12.0], [ 8.0, 12.0], [ 8.0, 18.0], [2.0, 18.0] ]]}]");

        GLMapVectorCascadeStyle style = GLMapVectorCascadeStyle.createStyle(
				"node[id=1]{icon-image:\"bus.svgpb\";icon-scale:0.5;icon-tint:green;text:eval(tag('text'));text-color:red;font-size:12;text-priority:100;}"
				+ "node|z-9[id=2]{icon-image:\"bus.svgpb\";icon-scale:0.7;icon-tint:blue;text:eval(tag('text'));text-color:red;font-size:12;text-priority:100;}"
				+ "line{linecap: round; width: 5pt; color:blue;}"
				+ "area{fill-color:green; width:1pt; color:red;}");

		mapView.addVectorObjectsWithStyle(objects.toArray(), style);
	}
    
    void captureScreen()
    {
    	GLMapView mapView = (GLMapView) this.findViewById(R.id.map_view);    	
    	mapView.captureFrameWhenFinish(this);
    }

	@Override
	public void screenCaptured(final Bitmap bmp) 
	{
		this.runOnUiThread(new Runnable() {
            @Override
            public void run() {            	
                ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                bmp.compress(Bitmap.CompressFormat.PNG, 100, bytes);
				try {
	                FileOutputStream fo = openFileOutput("screenCapture", Context.MODE_PRIVATE);
	                fo.write(bytes.toByteArray());
	                fo.close();               
	                
	                File file = new File(getExternalFilesDir(null), "Test.jpg");
	                fo = new FileOutputStream(file);
	                fo.write(bytes.toByteArray());
	                fo.close();	                
				} catch (IOException e) 
				{
					e.printStackTrace();
				}
            	            	
            	Intent intent = new Intent(MapViewActivity.this, DisplayImageActivity.class);
            	Bundle b = new Bundle();
            	b.putString("imageName", "screenCapture");
            	intent.putExtras(b);
            	startActivity(intent);            	
            }
        });		
    } 		
}