package globus.kotlinDemo

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.ViewGroup.MarginLayoutParams
import android.widget.ImageView
import android.widget.PopupWindow
import android.widget.RelativeLayout
import android.widget.TextView

typealias OnActionItemClickListener = (Int) -> Unit

// Custom popup menu with pin arrow
class QuickAction(context: Context, private val callback: OnActionItemClickListener) {
    private val rootView: View
    private val arrowUp: ImageView
    private val arrowDown: ImageView
    private val inflater = context.getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
    private val track: ViewGroup
    private val popupWindow: PopupWindow

    init {
        rootView = inflater.inflate(R.layout.popup_horizontal, null, false)
        track = rootView.findViewById(R.id.tracks)
        arrowDown = rootView.findViewById(R.id.arrow_down)
        arrowUp = rootView.findViewById(R.id.arrow_up)
        rootView.layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        popupWindow = PopupWindow(context)
        popupWindow.contentView = rootView
    }

    fun dismiss() {
        popupWindow.dismiss()
    }

    fun addActionItem(title: String?, actionID: Int) {
        val container = inflater.inflate(R.layout.horizontal_popup_item_text, track, false)
        val name = container.findViewById<TextView>(R.id.iv_horizontal_title)
        name.text = title
        container.setOnClickListener {
            callback(actionID)
            popupWindow.dismiss()
        }
        if (track.childCount != 0) {
            val separator = inflater.inflate(R.layout.horiz_separator, track, false)
            separator.layoutParams = RelativeLayout.LayoutParams(2, ViewGroup.LayoutParams.MATCH_PARENT)
            track.addView(separator)
        }
        val params = RelativeLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
        container.layoutParams = params
        track.addView(container)
    }

    fun show(parent: View, screenX: Float, screenY: Float) {
        var finalScreenX = screenX
        var finalScreenY = screenY
        popupWindow.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        popupWindow.isTouchable = true
        popupWindow.isFocusable = true
        popupWindow.isOutsideTouchable = true
        popupWindow.contentView = rootView
        val screenWidth = parent.width
        popupWindow.showAtLocation(parent, Gravity.NO_GRAVITY, finalScreenX.toInt(), finalScreenY.toInt())
        rootView.measure(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        var rootWidth = rootView.measuredWidth
        val arrowWidth = arrowUp.measuredWidth
        if (rootWidth > screenWidth - arrowWidth) {
            rootWidth = screenWidth - arrowWidth
            popupWindow.width = rootWidth
            rootView.measure(
                View.MeasureSpec.makeMeasureSpec(rootWidth, View.MeasureSpec.EXACTLY),
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }
        val rootHeight = rootView.measuredHeight
        val arrow: View
        if (finalScreenY > rootHeight) {
            arrow = arrowDown
            arrowUp.visibility = View.INVISIBLE
            finalScreenY -= rootHeight.toFloat()
        } else {
            arrow = arrowUp
            arrowDown.visibility = View.INVISIBLE
        }
        val halfArrowWidth = arrowWidth / 2
        var arrowPos = finalScreenX - (screenWidth - rootWidth) / 2
        if (arrowPos < 0) {
            arrowPos = 0f
        } else if (arrowPos > rootWidth - halfArrowWidth * 2) {
            arrowPos = rootWidth - halfArrowWidth * 2.toFloat()
        }
        finalScreenX -= arrowPos
        val param = arrow.getLayoutParams() as MarginLayoutParams
        param.leftMargin = when {
            arrowPos < halfArrowWidth -> 0
            arrowPos > rootWidth - halfArrowWidth -> rootWidth - halfArrowWidth
            else -> arrowPos.toInt()
        }
        val coords = IntArray(2)
        parent.getLocationOnScreen(coords)
        finalScreenY += coords[1]
        popupWindow.update(finalScreenX.toInt() - halfArrowWidth, finalScreenY.toInt(), rootWidth, rootHeight)
    }
}
