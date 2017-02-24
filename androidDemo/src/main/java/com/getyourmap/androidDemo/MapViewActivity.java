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

import com.glmapview.FieldListener;
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
import com.glmapview.GLMapVectorStyle;
import com.glmapview.GLMapView;
import com.glmapview.GLMapView.GLMapPlacement;
import com.glmapview.GLMapView.GLUnits;
import com.glmapview.PointD;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class MapViewActivity extends Activity implements GLMapView.ScreenCaptureCallback, FieldListener {
	class Pin
	{
		public PointD pos;
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
		GLMapManager.updateMapList(null);

		btnDownloadMap = (Button) this.findViewById(R.id.button_dl_map);
		btnDownloadMap.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				if (mapToDownload != null) {
					GLMapDownloadTask task = GLMapManager.getDownloadTask(mapToDownload);
					if (task != null) {
						task.cancel();
					} else {
						task = GLMapManager.createDownloadTask(mapToDownload);
						task.addListener(MapViewActivity.this);
						task.start();
					}
					updateMapDownloadButtonText();
				}
			}
		});

		for (GLMapDownloadTask task : GLMapManager.getMapDownloadTasks()) {
			task.addListener(this);
		}

		localeSettings = new GLMapLocaleSettings();

		mapView.setLocaleSettings(localeSettings);
		mapView.loadStyle(getAssets(), "DefaultStyle.bundle");
		mapView.setUserLocationImages(
				mapView.imageManager.open("DefaultStyle.bundle/circle-new.svgpb", 1, 0),
				mapView.imageManager.open("DefaultStyle.bundle/arrow-new.svgpb", 1, 0));

		mapView.setShowsUserLocation(true);

		mapView.setScaleRulerStyle(GLUnits.SI, GLMapPlacement.BottomCenter, new PointD(10, 10), 200);
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

			mapView.setMapCenter(GLMapView.convertGeoToInternal(new PointD(-122.0353, 37.3257)), false);
			mapView.setMapZoom(14, false);

			final Button btn = (Button) this.findViewById(R.id.button_img_action);
			btn.setVisibility(View.VISIBLE);
			btn.setText("Fly");
			btn.setOnClickListener(new OnClickListener() {
				@Override
				public void onClick(View v) {
					double min_lat = 33;
					double max_lat = 48;
					double min_lon = -118;
					double max_lon = -85;
					mapView.flyTo(GLMapView.convertGeoToInternal(new PointD(min_lon + (max_lon-min_lon) * Math.random(), min_lat + (max_lat-min_lat) * Math.random())), 15, 0 ,0);
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

            PointD point = GLMapView.convertGeoToInternal(new PointD(-2.4102, 54.5037));
            mapView.setMapCenter(point, false);
            mapView.setMapZoom(6, false);
        } else if (example == SampleSelectActivity.Samples.MULTILINE.ordinal()) {
			addMultiline();
		} else if (example == SampleSelectActivity.Samples.POLYGON.ordinal()) {
			addPolygon();
		} else if (example == SampleSelectActivity.Samples.CAPTURE_SCREEN.ordinal()) {
			zoomToPoint();
			captureScreen();
		} else if (example == SampleSelectActivity.Samples.IMAGE_SINGLE.ordinal()) {
			final Button btn = (Button) this.findViewById(R.id.button_img_action);
			btn.setVisibility(View.VISIBLE);
			btn.setOnClickListener(new OnClickListener() {
				@Override
				public void onClick(View v) {
					btn.setText("Add image");
					addImage();
				}
			});
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
    	PointD pt = new PointD(mapView.getWidth()/2, mapView.getHeight()/2);
		mapView.changeMapZoom(-1, pt, true);
	    return false;
	}

	@Override
	public boolean fieldValueChanged(Object obj, String fieldName, Object newValue)
	{
		if(fieldName.equals("finished") && newValue.equals(1))
		{
			mapView.reloadTiles();
		}
		runOnUiThread(new Runnable() {
			@Override
			public void run() {
				updateMapDownloadButtonText();
			}
		});
		return true;
	}

	void updateMapDownloadButtonText()
	{
		if(btnDownloadMap.getVisibility()==View.VISIBLE)
		{
			PointD center = mapView.getMapCenter(new PointD());

			if(mapToDownload==null || GLMapManager.DistanceToMap(mapToDownload, center)>0)
			{
				mapToDownload = GLMapManager.FindNearestMap(GLMapManager.getChildMaps(), center);
			}

			if (mapToDownload != null)
			{
				String text;
				GLMapDownloadTask task = GLMapManager.getDownloadTask(mapToDownload);
				if(task != null )
				{
					text = String.format("Downloading %s %d%%", mapToDownload.getLocalizedName(localeSettings), (int)(task.progressDownload*100));
				}else
				{
					text = String.format("Download %s", mapToDownload.getLocalizedName(localeSettings));
				}
				btnDownloadMap.setText(text);
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
				bbox.addPoint(GLMapView.convertGeoToInternal(new PointD(13.4102, 52.5037))); // Berlin
				bbox.addPoint(GLMapView.convertGeoToInternal(new PointD(27.5618, 53.9024))); // Minsk

			    mapView.setMapCenter(bbox.center(), false);
				mapView.setMapZoom(mapView.mapZoomForBBox(bbox, mapView.getWidth(), mapView.getHeight()), false);
			}
		});
	}
	    
    void zoomToPoint()
    {
    	//New York
    	//PointD pt = new PointD(-74.0059700 , 40.7142700	);
    	
    	//Belarus
    	//PointD pt = new PointD(27.56, 53.9);
    	//;

		// Move map to the Montenegro capital
		PointD pt = GLMapView.convertGeoToInternal(new PointD(19.26, 42.4341));
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
        		public PointD getImagePos(int i) 
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
        		public PointD getImageVariantOffset(int i)
        		{
        			return new PointD(images[i].getWidth()/2, 0);
        		}
            }          	    		
    		imageGroup = mapView.createImageGroup(new Callback());    		
    	}    	
    	
    	PointD pt = mapView.convertDisplayToInternal(new PointD(touchX, touchY));
    	
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
    		PointD pos = pins.get(i).pos;
    		PointD screenPos = mapView.convertInternalToDisplay(new PointD(pos)); 
    		
    		Rect rt = new Rect(-40,-40,40,40);
    		rt.offset( (int)screenPos.getX(), (int)screenPos.getY() );    	
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
		Object markersToRemove[] =  markerLayer.objectsNearPoint(mapView, mapView.convertDisplayToInternal(new PointD(x, y)), 30);
		if(markersToRemove!=null && markersToRemove.length==1)
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

	void addMarker(float x, float y)
	{
		PointD newMarkers[] = new PointD[1];
		newMarkers[0] = mapView.convertDisplayToInternal(new PointD(x, y));

		markerLayer.modify(newMarkers, null, null, true, new Runnable()
		{
			@Override
			public void run()
			{
				Log.d("MarkerLayer", "Marker added");
			}
		});
	}

	void addMarkers()
	{
		final GLMapMarkerStyleCollection style = new GLMapMarkerStyleCollection();

		final int unionCounts[] = {1, 2, 4, 8, 16, 32, 64, 128};
		final int unionColours[] = {
				Color.argb(255, 33, 0, 255),
				Color.argb(255, 68, 195, 255),
				Color.argb(255, 63, 237, 198),
				Color.argb(255, 15, 228, 36),
				Color.argb(255, 168, 238, 25),
				Color.argb(255, 214, 234, 25),
				Color.argb(255, 223, 180, 19),
				Color.argb(255, 255, 0, 0)
		};

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
				//GLMapMarkerStyleCollection.setMarkerText(nativeMarker, Integer.toString(markersCount) , new Point(0, 0), textStyle);
			}

			@Override
			public void fillData(Object marker, long nativeMarker)
			{
				if(marker.getClass() == PointD.class)
				{
					GLMapMarkerStyleCollection.setMarkerLocation(nativeMarker, (PointD) marker);
					GLMapMarkerStyleCollection.setMarkerText(nativeMarker, "Test", new Point(0,0), textStyle);
				}else if(marker.getClass() == GLMapVectorObject.class)
				{
					GLMapVectorObject obj = (GLMapVectorObject)marker;

					GLMapMarkerStyleCollection.setMarkerLocation(nativeMarker, obj.point());
					String name = obj.valueForKey("name");
					if(name!=null)
					{
						GLMapMarkerStyleCollection.setMarkerText(nativeMarker, name, new Point(0, 15/2), textStyle);
					}
				}
				GLMapMarkerStyleCollection.setMarkerStyle(nativeMarker, 0);
			}
		});

		new AsyncTask<Void, Void, GLMapVectorObject[]>()
		{
			@Override
			protected GLMapVectorObject[] doInBackground(Void... voids)
			{
				GLMapVectorObject rv[];
				try
				{
					rv = GLMapVectorObject.createFromGeoJSONStream(getAssets().open("cluster_data.json"));
				} catch (Exception e)
				{
					rv = new GLMapVectorObject[0];
				}
				return rv;
			}

			@Override
			protected void onPostExecute(GLMapVectorObject objects[])
			{
				markerLayer = new GLMapMarkerLayer(objects, style);
				mapView.displayMarkerLayer(markerLayer);
			}

		}.execute();
	}

    
    void addImage()
    {
    	Bitmap bmp = mapView.imageManager.open("arrow-maphint.svgpb", 1, 0);
    	image = mapView.displayImage(bmp);
    	image.setOffset(new PointD(bmp.getWidth(), bmp.getHeight()/2));
    	image.setRotatesWithMap(true);
    	image.setAngle((float)Math.random()*360);

    	image.setPosition(mapView.getMapCenter( new PointD()));
    	
    	final Button btn = (Button) this.findViewById(R.id.button_img_action);
		btn.setText("Move image");
    	btn.setOnClickListener(new OnClickListener(){
			@Override
			public void onClick(View v) {
				moveImage();
			}
    	});    	
    }
    
    void moveImage()
	{
		image.setPosition(mapView.getMapCenter(new PointD()));

		final Button btn = (Button) this.findViewById(R.id.button_img_action);
		btn.setText("Remove image");
		btn.setOnClickListener(new OnClickListener()
		{
			@Override
			public void onClick(View v)
			{
				delImage();
			}
		});
	}
    
    void delImage()
    {
		image.dispose();
		image = null;

    	final Button btn = (Button) this.findViewById(R.id.button_img_action);
		btn.setText("Add image");
    	btn.setOnClickListener(new OnClickListener(){
			@Override
			public void onClick(View v) {
				addImage();
			}
    	});     	
    }
    
    void addMultiline()
    {
        PointD[] line1 = new PointD[5];
        line1[0] = new PointD(27.7151, 53.8869); // Minsk   
        line1[1] = new PointD(30.5186, 50.4339); // Kiev
        line1[2] = new PointD(21.0103, 52.2251); // Warsaw
        line1[3] = new PointD(13.4102, 52.5037); // Berlin
        line1[4] = new PointD(2.3343, 48.8505); // Paris
        
        PointD[] line2 = new PointD[3];
        line2[0] = new PointD(4.9021, 52.3690); // Amsterdam  
        line2[1] = new PointD(4.3458, 50.8263); // Brussel
        line2[2] = new PointD(6.1296, 49.6072); // Luxembourg
       
        PointD[][] multiline = {line1, line2};

		final GLMapVectorObject obj = GLMapVectorObject.createMultiline(multiline);
		// style applied to all lines added. Style is string with mapcss rules. Read more in manual.
		mapView.addVectorObjectWithStyle(obj, GLMapVectorCascadeStyle.createStyle("line{width: 2pt;color:green;layer:100;}"), null);
     }
    
    void addPolygon()
    {
        int pointCount = 25;
        PointD[] outerRing = new PointD[pointCount];
        PointD[] innerRing = new PointD[pointCount];
        
        float rOuter = 20, rInner = 10;
        float cx = 30, cy = 30;

        // let's display circle
        for (int i=0; i<pointCount; i++) 
        {
        	outerRing[i] = new PointD(cx + Math.sin(2*Math.PI / pointCount * i) * rOuter,
                                      cy + Math.cos(2*Math.PI / pointCount * i) * rOuter);
        	
        	innerRing[i] =  new PointD(cx + Math.sin(2*Math.PI / pointCount * i) * rInner,
                    cy + Math.cos(2*Math.PI / pointCount * i) * rInner);        	
        }
        
        PointD[][] outerRings = {outerRing};
        PointD[][] innerRings = {innerRing};

		GLMapVectorObject obj = GLMapVectorObject.createPolygon(outerRings, innerRings);
		mapView.addVectorObjectWithStyle(obj, GLMapVectorCascadeStyle.createStyle("area{fill-color:#10106050; fill-color:#10106050; width:4pt; color:green;}"), null); // #RRGGBBAA format
    }
    
	private void loadGeoJSON() 
	{	
		GLMapVectorObject []objects = GLMapVectorObject.createFromGeoJSON(
				"[{\"type\": \"Feature\", \"geometry\": {\"type\": \"Point\", \"coordinates\": [30.5186, 50.4339]}, \"properties\": {\"id\": \"1\", \"text\": \"test1\"}},"
				+ "{\"type\": \"Feature\", \"geometry\": {\"type\": \"Point\", \"coordinates\": [27.7151, 53.8869]}, \"properties\": {\"id\": \"2\", \"text\": \"test2\"}},"
				+ "{\"type\":\"LineString\",\"coordinates\": [ [27.7151, 53.8869], [30.5186, 50.4339], [21.0103, 52.2251], [13.4102, 52.5037], [2.3343, 48.8505]]},"
				+ "{\"type\":\"Polygon\",\"coordinates\":[[ [0.0, 10.0], [10.0, 10.0], [10.0, 20.0], [0.0, 20.0] ],[ [2.0, 12.0], [ 8.0, 12.0], [ 8.0, 18.0], [2.0, 18.0] ]]}]");

        GLMapVectorCascadeStyle style = GLMapVectorCascadeStyle.createStyle(
				"node[id=1]{icon-image:\"bus.svgpb\";icon-scale:0.5;icon-tint:green;text:eval(tag('text'));text-color:red;font-size:12;text-priority:100;}"
				+ "node|z-9[id=2]{icon-image:\"bus.svgpb\";icon-scale:0.7;icon-tint:blue;text:eval(tag('text'));text-color:red;font-size:12;text-priority:100;}"
				+ "line{linecap: round; width: 5pt; color:blue;}"
				+ "area{fill-color:green; width:1pt; color:red;}");

		mapView.addVectorObjectsWithStyle(objects, style);
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
