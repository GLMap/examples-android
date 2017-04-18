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

import com.glmapview.GLMapDownloadTask;
import com.glmapview.GLMapInfo;
import com.glmapview.GLMapLocaleSettings;
import com.glmapview.GLMapManager;
import com.glmapview.GLMapView;
import com.glmapview.MapPoint;

import java.util.List;
import java.util.Locale;

@SuppressLint("InflateParams")
public class DownloadActivity extends ListActivity implements GLMapManager.StateListener
{
	private enum ContextItems {
		DELETE
	}

	private MapPoint center;
	private GLMapInfo selectedMap = null;
	private GLMapLocaleSettings localeSettings;
	private ListView listView;

	private class MapsAdapter extends BaseAdapter implements ListAdapter {
        private GLMapInfo[] maps;
        private Context context;
        
		MapsAdapter(GLMapInfo[] maps, Context context)
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
			}else if( map.getState() == GLMapInfo.State.IN_PROGRESS)
			{
				txtDescription.setText(String.format(Locale.ENGLISH, "Download %.2f%%", map.getDownloadProgress()*100));
			}else
			{
				txtDescription.setText(NumberFormatter.FormatSize(map.getSize()));
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

		listView = (ListView) findViewById(android.R.id.list);
		registerForContextMenu(listView);
		List<GLMapDownloadTask> tasks = GLMapManager.getMapDownloadTasks();
		GLMapManager.addStateListener(this);

        Intent i = getIntent();
        center = new MapPoint(i.getDoubleExtra("cx", 0.0), i.getDoubleExtra("cy", 0.0));
        long collectionID = i.getLongExtra("collectionID", 0) ;
        if(collectionID!=0)
        {
			GLMapInfo collection = GLMapManager.getMapWithID(collectionID);
			if(collection != null)
			{
				updateAllItems(collection.getMaps());
			}
        }else
        {       
	        updateAllItems(GLMapManager.getMaps());	        
	        GLMapManager.updateMapList(this, new Runnable(){
				@Override
				public void run()
				{
					updateAllItems(GLMapManager.getMaps());
				}
	        });
		}
    }

	@Override
	protected void onDestroy()
	{
		GLMapManager.removeStateListener(this);
		super.onDestroy();
	}

	@Override
	public void onStartDownloading(GLMapInfo map)
	{

	}

	@Override
	public void onDownloadProgress(GLMapInfo map)
	{
		((MapsAdapter) listView.getAdapter()).notifyDataSetChanged();
	}

	@Override
	public void onFinishDownloading(GLMapInfo map)
	{

	}

	@Override
	public void onStateChanged(GLMapInfo map)
	{
		((MapsAdapter) listView.getAdapter()).notifyDataSetChanged();
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
        listView.setAdapter(new MapsAdapter(maps, this));
        listView.setOnItemClickListener(new OnItemClickListener() {
			@Override
			public void onItemClick(AdapterView<?> parent, View view,
									int position, long id) {
				GLMapInfo info = (GLMapInfo) listView.getAdapter().getItem(position);
				if (info.isCollection()) {
					Intent intent = new Intent(DownloadActivity.this, DownloadActivity.class);
					intent.putExtra("collectionID", info.getMapID());
					intent.putExtra("cx", center.x);
					intent.putExtra("cy", center.y);
					DownloadActivity.this.startActivity(intent);
				} else {
					GLMapDownloadTask task = GLMapManager.getDownloadTask(info);
					if (task != null) {
						task.cancel();
					} else if (info.getState() != GLMapInfo.State.DOWNLOADED) {
						GLMapManager.createDownloadTask(info, DownloadActivity.this).start();
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