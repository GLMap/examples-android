package globus.demo.tour

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.widget.TextViewCompat
import globus.demo.tour.DemoTourMotion as Motion
import kotlin.math.max
import kotlin.math.roundToInt

/** Screen-space captions only; all map markers are native GLMapImage drawables. */
internal class DemoTourOverlay(context: Context) : FrameLayout(context) {
    private val heading = label("Amalfi Coast", 30f, true)
    private val subheading = label("Preparing your walk…", 15f)
    private val searchBar = LinearLayout(context)
    private val searchIcon = ImageView(context)
    private val searchText = label("", 18f, true)
    private val summary = label("", 20f, true)
    private val preview = label("Accelerated route preview", 11f)
    private val card = FrameLayout(context)
    private val placeName = label("", 23f, true)
    private val icons = DemoTourArtwork(resources.displayMetrics.density).let { art -> (0..3).map(art::phaseIcon) }
    private var phase = -1

    init {
        ViewCompat.setOnApplyWindowInsetsListener(this) { view, insets ->
            val safe = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            view.setPadding(safe.left, safe.top, safe.right, safe.bottom)
            insets
        }
        heading.setShadowLayer(dp(8).toFloat(), 0f, 0f, Color.WHITE)
        addView(heading, top(36, 22, 26))
        addView(subheading, top(LayoutParams.WRAP_CONTENT, 62, 26))

        panel(searchBar, 20)
        searchBar.gravity = Gravity.CENTER_VERTICAL
        searchBar.setPadding(dp(18), 0, dp(16), 0)
        searchBar.addView(searchIcon, LinearLayout.LayoutParams(dp(20), dp(20)))
        searchBar.addView(
            searchText,
            LinearLayout.LayoutParams(0, LayoutParams.MATCH_PARENT, 1f).apply {
                marginStart = dp(12)
            }
        )
        searchText.gravity = Gravity.CENTER_VERTICAL
        TextViewCompat.setAutoSizeTextTypeUniformWithConfiguration(
            searchText,
            14,
            18,
            1,
            android.util.TypedValue.COMPLEX_UNIT_SP
        )
        addView(searchBar, top(56, 18))

        summary.setTextColor(Color.WHITE)
        summary.gravity = Gravity.CENTER
        summary.background = background(DemoTourArtwork.ACCENT, 15)
        addView(summary, top(43, 86))
        preview.gravity = Gravity.CENTER
        preview.background = background(0xE6FFFFFF.toInt(), 12)
        addView(
            preview,
            LayoutParams(dp(170), dp(24), Gravity.TOP or Gravity.CENTER_HORIZONTAL).apply {
                topMargin = dp(137)
            }
        )

        panel(card, 24)
        card.addView(label("RESTAURANT", 11f, true).apply { setTextColor(DemoTourArtwork.ACCENT) }, top(18, 15, 20))
        TextViewCompat.setAutoSizeTextTypeUniformWithConfiguration(
            placeName,
            15,
            23,
            1,
            android.util.TypedValue.COMPLEX_UNIT_SP
        )
        card.addView(placeName, top(38, 36, 20))
        card.addView(
            label("A coastal walk  →", 17f, true).apply {
                gravity = Gravity.CENTER
                setTextColor(Color.WHITE)
                background = background(DemoTourArtwork.ACCENT, 15)
            },
            LayoutParams(LayoutParams.MATCH_PARENT, dp(46), Gravity.BOTTOM).apply {
                setMargins(dp(18), 0, dp(18), dp(17))
            }
        )
        addView(
            card,
            LayoutParams(LayoutParams.MATCH_PARENT, dp(154), Gravity.BOTTOM).apply {
                setMargins(dp(22), 0, dp(22), dp(52))
            }
        )
        listOf(searchBar, summary, preview, card).forEach { it.alpha = 0f }
    }

    fun showPlace(name: String, routeSummary: String) {
        placeName.text = name
        summary.text = routeSummary
        subheading.text = "A place worth the walk"
    }

    fun showError(message: String) {
        subheading.text = message
    }

    fun update(time: Double) {
        val intro = max(1 - Motion.ease((time - 3.8) / 0.5), Motion.ease((time - 24.5) / 1.0)).toFloat()
        val outro = Motion.outroOpacity(time)
        heading.alpha = intro
        subheading.alpha = intro
        searchBar.alpha = (Motion.ease((time - Motion.SEARCH_START) / 0.45) * outro).toFloat()
        val nextPhase = when {
            time < Motion.ROUTE_START -> 0
            time < Motion.FOLLOW_START -> 1
            time < Motion.ARRIVAL -> 2
            else -> 3
        }
        if (phase != nextPhase) {
            phase = nextPhase
            searchText.text =
                arrayOf("Restaurants nearby", "Your coastal walk", "Walking preview", "You've arrived")[phase]
            searchIcon.setImageBitmap(icons[phase])
        }
        summary.alpha = (Motion.ease((time - Motion.REVEAL_END) / 0.5) * outro).toFloat()
        preview.alpha = (
            Motion.ease((time - Motion.FOLLOW_START) / 0.4) *
                (1 - Motion.ease((time - Motion.ARRIVAL) / 0.4))
            ).toFloat()
        card.alpha = (
            Motion.ease((time - Motion.SELECTION_START) / 0.4) *
                (1 - Motion.ease((time - Motion.OVERVIEW_START) / 0.4))
            ).toFloat()
        card.translationY = dp(12) * (1 - card.alpha)
    }

    private fun label(text: String, size: Float, bold: Boolean = false) = TextView(context).apply {
        this.text = text
        textSize = size
        setTextColor(DemoTourArtwork.INK)
        if (bold) setTypeface(typeface, Typeface.BOLD)
        setSingleLine()
        includeFontPadding = false
    }

    private fun top(height: Int, top: Int, margin: Int = 22) = LayoutParams(
        LayoutParams.MATCH_PARENT,
        if (height < 0) height else dp(height),
        Gravity.TOP
    ).apply { setMargins(dp(margin), dp(top), dp(margin), 0) }

    private fun background(color: Int, radius: Int) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = dp(radius).toFloat()
    }

    private fun panel(view: android.view.View, radius: Int) {
        view.background = background(Color.WHITE, radius)
        view.elevation = dp(6).toFloat()
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).roundToInt()
}
