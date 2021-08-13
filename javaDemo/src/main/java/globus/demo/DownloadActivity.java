package globus.demo;

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
import android.widget.BaseAdapter;
import android.widget.ListAdapter;
import android.widget.ListView;
import android.widget.TextView;

import globus.glmap.GLMapDownloadTask;
import globus.glmap.GLMapInfo;
import globus.glmap.GLMapLocaleSettings;
import globus.glmap.GLMapManager;
import globus.glmap.MapPoint;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

@SuppressLint("InflateParams")
public class DownloadActivity extends ListActivity implements GLMapManager.StateListener {
  private enum ContextItems {
    DELETE
  }

  private MapPoint center;
  private GLMapInfo selectedMap = null;
  private GLMapLocaleSettings localeSettings;
  private ListView listView;

  static boolean anyDataSetHaveState(GLMapInfo info, @GLMapInfo.State int state) {
    for (int i = 0; i < GLMapInfo.DataSet.COUNT; ++i) {
      if (info.getState(i) == state) return true;
    }
    return false;
  }

  static boolean isOnDevice(GLMapInfo info) {
    return anyDataSetHaveState(info, GLMapInfo.State.IN_PROGRESS)
        || anyDataSetHaveState(info, GLMapInfo.State.DOWNLOADED)
        || anyDataSetHaveState(info, GLMapInfo.State.NEED_RESUME)
        || anyDataSetHaveState(info, GLMapInfo.State.NEED_UPDATE)
        || anyDataSetHaveState(info, GLMapInfo.State.REMOVED);
  }

  private class MapsAdapter extends BaseAdapter implements ListAdapter {
    private GLMapInfo[] maps;
    private Context context;

    MapsAdapter(GLMapInfo[] maps, Context context) {
      this.maps = maps;
      this.context = context;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
      GLMapInfo map = maps[position];
      TextView txtDescription, txtHeaderName;
      if (convertView == null) {
        convertView = LayoutInflater.from(context).inflate(R.layout.map_name, null);
      }
      txtHeaderName = convertView.findViewById(android.R.id.text1);
      txtDescription = convertView.findViewById(android.R.id.text2);

      txtHeaderName.setText(map.getLocalizedName(localeSettings));

      List<GLMapDownloadTask> tasks = GLMapManager.getDownloadTasks(map.getMapID(), GLMapInfo.DataSetMask.ALL);
      if (tasks != null && !tasks.isEmpty()) {
        long total = 0;
        long downloaded = 0;
        for(GLMapDownloadTask task : tasks) {
          total += task.total;
          downloaded += task.downloaded;
        }
        float progress;
        if (total != 0) progress = 100.0f * downloaded / total;
        else progress = 0;
        txtDescription.setText(String.format(Locale.ENGLISH, "Download %.2f%%", progress));
      } else if (map.isCollection()) {
        txtDescription.setText("Collection");
      } else if (anyDataSetHaveState(map, GLMapInfo.State.NEED_UPDATE)) {
        txtDescription.setText("Need update");
      } else if (anyDataSetHaveState(map, GLMapInfo.State.NEED_RESUME)) {
        txtDescription.setText("Need resume");
      } else {
        long size = map.getSizeOnDisk(GLMapInfo.DataSetMask.ALL);
        if (size == 0) size = map.getSizeOnServer(GLMapInfo.DataSetMask.ALL);
        txtDescription.setText(NumberFormatter.FormatSize(size));
      }
      return convertView;
    }

    @Override
    public int getCount() {
      return maps.length;
    }

    @Override
    public Object getItem(int position) {
      return maps[position];
    }

    @Override
    public long getItemId(int position) {
      return position;
    }
  }

  @Override
  protected void onCreate(@Nullable Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.download);

    localeSettings = new GLMapLocaleSettings();

    listView = findViewById(android.R.id.list);
    registerForContextMenu(listView);
    GLMapManager.addStateListener(this);

    Intent i = getIntent();
    center = new MapPoint(i.getDoubleExtra("cx", 0.0), i.getDoubleExtra("cy", 0.0));
    long collectionID = i.getLongExtra("collectionID", 0);
    if (collectionID != 0) {
      GLMapInfo collection = GLMapManager.GetMapWithID(collectionID);
      if (collection != null) {
        updateAllItems(collection.getMaps());
      }
    } else {
      updateAllItems(GLMapManager.GetMaps());
      GLMapManager.UpdateMapList(listUpdated -> updateAllItems(GLMapManager.GetMaps()));
    }
  }

  @Override
  protected void onDestroy() {
    GLMapManager.removeStateListener(this);
    super.onDestroy();
  }

  @Override
  public void onStartDownloading(GLMapDownloadTask task) {}

  @Override
  public void onDownloadProgress(GLMapDownloadTask task) {
    ((MapsAdapter) listView.getAdapter()).notifyDataSetChanged();
  }

  @Override
  public void onFinishDownloading(GLMapDownloadTask task) {}

  @Override
  public void onStateChanged(GLMapInfo map) {
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
        GLMapManager.DeleteDataSets(selectedMap, GLMapInfo.DataSetMask.ALL);
        ListView listView = findViewById(android.R.id.list);
        ((MapsAdapter) listView.getAdapter()).notifyDataSetChanged();
        break;

      default:
        return super.onOptionsItemSelected(item);
    }
    return true;
  }

  public void updateAllItems(@Nullable GLMapInfo maps[]) {
    if (maps == null) return;

    Arrays.sort(maps, (a, b) -> a.getLocalizedName(localeSettings).compareTo(b.getLocalizedName(localeSettings)));
    // Use GLMapManager.SortMaps(maps, center) to sort map array by distance from user location;
    final ListView listView = findViewById(android.R.id.list);
    listView.setAdapter(new MapsAdapter(maps, this));
    listView.setOnItemClickListener((parent, view, position, id) -> {
      GLMapInfo info = (GLMapInfo) listView.getAdapter().getItem(position);
      if (info.isCollection()) {
        Intent intent = new Intent(DownloadActivity.this, DownloadActivity.class);
        intent.putExtra("collectionID", info.getMapID());
        intent.putExtra("cx", center.x);
        intent.putExtra("cy", center.y);
        DownloadActivity.this.startActivity(intent);
      } else {
        List<GLMapDownloadTask> tasks = GLMapManager.getDownloadTasks(info.getMapID(), GLMapInfo.DataSetMask.ALL);
        if (tasks != null && !tasks.isEmpty()) {
          for(GLMapDownloadTask task : tasks)
            task.cancel();
        } else {
          GLMapManager.DownloadDataSets(info, GLMapInfo.DataSetMask.ALL);
        }
      }
    });

    listView.setOnItemLongClickListener((parent, view, position, id) -> {
      GLMapInfo info = ((MapsAdapter) listView.getAdapter()).maps[position];
      if (isOnDevice(info)) {
        selectedMap = info;
        return false;
      } else {
        return true;
      }
    });
  }
}
