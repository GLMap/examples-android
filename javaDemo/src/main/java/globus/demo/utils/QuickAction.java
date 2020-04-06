package globus.demo.utils;

import android.content.Context;
import android.graphics.drawable.BitmapDrawable;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.PopupWindow;
import android.widget.RelativeLayout;
import android.widget.TextView;
import globus.demo.R;

// Custom popup menu with pin arrow
public class QuickAction {

  private View rootView;
  private ImageView arrowUp, arrowDown;
  private LayoutInflater inflater;
  private ViewGroup track;
  private OnActionItemClickListener onActionItemClickListener;

  private PopupWindow popupWindow;

  public interface OnActionItemClickListener {
    void onItemClick(QuickAction source, int actionId);
  }

  public QuickAction(Context context) {
    inflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
    if (inflater == null) return;
    rootView = inflater.inflate(R.layout.popup_horizontal, null, false);
    track = rootView.findViewById(R.id.tracks);
    arrowDown = rootView.findViewById(R.id.arrow_down);
    arrowUp = rootView.findViewById(R.id.arrow_up);
    rootView.setLayoutParams(
        new ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
    popupWindow = new PopupWindow(context);
    popupWindow.setContentView(rootView);
  }

  public void dismiss() {
    popupWindow.dismiss();
  }

  public void setonActionItemClickListener(OnActionItemClickListener onActionItemClickListener) {
    this.onActionItemClickListener = onActionItemClickListener;
  }

  public void addActionItem(ActionItem action) {
    View container = inflater.inflate(R.layout.horizontal_popup_item_text, null, false);
    TextView name = container.findViewById(R.id.iv_horizontal_title);
    name.setText(action.getName());

    final int actionId = action.getActionId();
    container.setOnClickListener(
        new View.OnClickListener() {
          @Override
          public void onClick(View view) {
            if (onActionItemClickListener != null) {
              onActionItemClickListener.onItemClick(QuickAction.this, actionId);
            }
            popupWindow.dismiss();
          }
        });

    if (track.getChildCount() != 0) {
      View separator = inflater.inflate(R.layout.horiz_separator, null, false);
      separator.setLayoutParams(
          new RelativeLayout.LayoutParams(2, ViewGroup.LayoutParams.MATCH_PARENT));
      track.addView(separator);
    }

    RelativeLayout.LayoutParams params =
        new RelativeLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT);
    container.setLayoutParams(params);
    track.addView(container);
  }

  public void show(View parent, float screenX, float screenY) {
    popupWindow.setBackgroundDrawable(new BitmapDrawable());
    popupWindow.setTouchable(true);
    popupWindow.setFocusable(true);
    popupWindow.setOutsideTouchable(true);
    popupWindow.setContentView(rootView);

    int screenWidth = parent.getWidth();

    popupWindow.showAtLocation(parent, Gravity.NO_GRAVITY, (int) screenX, (int) screenY);
    rootView.measure(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    int rootWidth = rootView.getMeasuredWidth();
    int arrowWidth = arrowUp.getMeasuredWidth();
    if (rootWidth > screenWidth - arrowWidth) {
      rootWidth = screenWidth - arrowWidth;
      popupWindow.setWidth(rootWidth);
      rootView.measure(
          View.MeasureSpec.makeMeasureSpec(rootWidth, View.MeasureSpec.EXACTLY),
          ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    int rootHeight = rootView.getMeasuredHeight();
    View arrow;
    if (screenY > rootHeight) {
      arrow = arrowDown;
      arrowUp.setVisibility(View.INVISIBLE);
      screenY -= rootHeight;
    } else {
      arrow = arrowUp;
      arrowDown.setVisibility(View.INVISIBLE);
    }

    int halfArrowWidth = arrowWidth / 2;

    float arrowPos = screenX - (screenWidth - rootWidth) / 2;
    if (arrowPos < 0) arrowPos = 0;
    else if (arrowPos > rootWidth - halfArrowWidth * 2) {
      arrowPos = rootWidth - halfArrowWidth * 2;
    }
    screenX = screenX - arrowPos;

    ViewGroup.MarginLayoutParams param = (ViewGroup.MarginLayoutParams) arrow.getLayoutParams();
    if (arrowPos < halfArrowWidth) {
      param.leftMargin = 0;
    } else if (arrowPos > rootWidth - halfArrowWidth) {
      param.leftMargin = rootWidth - halfArrowWidth;
    } else {
      param.leftMargin = (int) arrowPos;
    }

    int coords[] = new int[2];
    parent.getLocationOnScreen(coords);
    screenY += coords[1];

    popupWindow.update((int) screenX - halfArrowWidth, (int) screenY, rootWidth, rootHeight);
  }
}
