package com.getyourmap.androidDemo;

import android.annotation.SuppressLint;
import android.app.ListActivity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.ContextMenu;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.BaseAdapter;
import android.widget.ListAdapter;
import android.widget.ListView;
import android.widget.TextView;

import com.glmapview.FieldListener;
import com.glmapview.GLMapDownloadTask;
import com.glmapview.GLMapInfo;
import com.glmapview.GLMapLocaleSettings;
import com.glmapview.GLMapManager;
import com.glmapview.GLMapView;
import com.glmapview.PointD;

import java.util.List;
import java.util.Locale;

@SuppressLint("InflateParams")
public class DownloadActivity extends ListActivity implements FieldListener
{
	private enum ContextItems {
		DELETE
	}

	PointD center;
	GLMapInfo selectedMap = null;
	GLMapLocaleSettings localeSettings;

	private class MapsAdapter extends BaseAdapter implements ListAdapter {
        private GLMapInfo[] maps;
        private Context context;
        
        public MapsAdapter(GLMapInfo[] maps, Context context) 
        {
        	this.maps= maps;
        	this.context = context;
			localeSettings = new GLMapLocaleSettings();
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) 
        {
        	GLMapInfo map = maps[position]; 
            TextView txtDescription, txtHeaderName;
            if (convertView == null) 
            {
                convertView = LayoutInflater.from(context).inflate(R.layout.map_name, null);
            }
            txtHeaderName = ((TextView) convertView.findViewById(android.R.id.text1));
            txtDescription = ((TextView) convertView.findViewById(android.R.id.text2));
            
            GLMapDownloadTask task = GLMapManager.getDownloadTask(map);
            if(task!=null)
            {
                txtHeaderName.setText(map.getLocalizedName(localeSettings));
                txtDescription.setText(String.format(Locale.ENGLISH, "Download %.2f%%", task.progressDownload*100));
            }else
            {
                txtHeaderName.setText(map.getLocalizedName(localeSettings));
            	if(map.isCollection())
            	{
            		txtDescription.setText("Collection");            		
            	} else if (map.getState() == GLMapInfo.State.DOWNLOADED)
				{
            		txtDescription.setText("Downloaded");
            	} else if( map.getState() == GLMapInfo.State.NEED_UPDATE)
				{
					txtDescription.setText("Need update");
				}else if( map.getState() == GLMapInfo.State.NEED_RESUME)
				{
					txtDescription.setText("Need resume");
				}else
            	{            	
            		txtDescription.setText(NumberFormatter.FormatSize(map.getSize()));
            	}
            }           
            return convertView;
        }

		@Override
		public int getCount() 
		{
			return maps.length;
		}

		@Override
		public Object getItem(int position) 
		{
			return maps[position];
		}

		@Override
		public long getItemId(int position) 
		{
			return position;
		}	
	}
	
	
    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.download);

		final ListView listView = (ListView) findViewById(android.R.id.list);
		registerForContextMenu(listView);
		List<GLMapDownloadTask> tasks = GLMapManager.getMapDownloadTasks();
		if(tasks != null){
			for (GLMapDownloadTask task : tasks) {
				task.addListener(this);
			}
		}
        
        Intent i = getIntent();
        center = GLMapView.convertGeoToInternal( new PointD(i.getDoubleExtra("cx", 0.0), i.getDoubleExtra("cy", 0.0)) );        
        GLMapInfo collection = (GLMapInfo) i.getSerializableExtra("maps");
        if(collection!=null)
        {
	        updateAllItems(collection.getMaps());        	        	
        }else
        {       
	        updateAllItems(GLMapManager.getMaps());	        
	        GLMapManager.updateMapList(new Runnable(){
	    		            @Override
	    		            public void run() 
	    		            {
	    		            	runOnUiThread(new Runnable(){
									@Override
									public void run() {
										updateAllItems(GLMapManager.getMaps());
									}
	    		            	});
	    		            }
	    				});
	        }
        
    }

	@Override
	public boolean fieldValueChanged(Object obj, String fieldName, Object newValue) {
		final ListView listView = (ListView) findViewById(android.R.id.list);
		((MapsAdapter) listView.getAdapter()).notifyDataSetChanged();
		return true;
	}

	@Override
	public void onCreateContextMenu(ContextMenu menu, View v, ContextMenu.ContextMenuInfo menuInfo) {
		super.onCreateContextMenu(menu, v, menuInfo);

		if (selectedMap != null) {
			menu.setHeaderTitle(selectedMap.getLocalizedName(localeSettings));
			menu.add(0, ContextItems.DELETE.ordinal(), ContextItems.DELETE.ordinal(), "Delete");
		}
	}

	@Override
	public boolean onContextItemSelected(MenuItem item) {
		ContextItems selected = ContextItems.values()[item.getItemId()];
		switch (selected) {
			case DELETE:
				selectedMap.deleteFiles();
				final ListView listView = (ListView) findViewById(android.R.id.list);
				((MapsAdapter)listView.getAdapter()).notifyDataSetChanged();
				break;

			default:
				return super.onOptionsItemSelected(item);
		}
		return true;
	}
    
    public void updateAllItems(GLMapInfo maps[]) 
    {
        if (maps == null)
            return;

        GLMapManager.SortMaps(maps, center);
        
       	final ListView listView = (ListView) findViewById(android.R.id.list);
		final DownloadActivity activity = this;
        listView.setAdapter(new MapsAdapter(maps, this));
        listView.setOnItemClickListener(new OnItemClickListener() {
			@Override
			public void onItemClick(AdapterView<?> parent, View view,
									int position, long id) {
				GLMapInfo info = (GLMapInfo) listView.getAdapter().getItem(position);
				if (info.isCollection()) {
					Intent intent = new Intent(DownloadActivity.this, DownloadActivity.class);
					intent.putExtra("maps", info);
					intent.putExtra("cx", center.getX());
					intent.putExtra("cy", center.getY());
					DownloadActivity.this.startActivity(intent);
				} else {
					GLMapDownloadTask task = GLMapManager.getDownloadTask(info);
					if (task != null) {
						task.cancel();
					} else if (info.getState() != GLMapInfo.State.DOWNLOADED) {
						task = GLMapManager.createDownloadTask(info);
						task.addListener(activity);
						task.start();
					}
				}
			}
		});

		listView.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
			@Override
			public boolean onItemLongClick(AdapterView<?> parent, View view, int position, long id) {
				GLMapInfo info = ((MapsAdapter) listView.getAdapter()).maps[position];
				GLMapInfo.State state = info.getState();
				if (state == GLMapInfo.State.DOWNLOADED || state == GLMapInfo.State.NEED_RESUME || state == GLMapInfo.State.NEED_UPDATE)
				{
					selectedMap = info;
					return false;
				} else
				{
					return true;
				}
			}
		});
    }    

}