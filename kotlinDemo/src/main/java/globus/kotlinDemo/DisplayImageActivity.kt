package globus.kotlinDemo

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import android.widget.ImageView
import globus.glmap.SVGRender
import java.io.FileNotFoundException

class DisplayImageActivity : Activity() {
    private val imageView by lazy { findViewById<ImageView>(R.id.image_view) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.display_image)

        val imgPath = intent.extras?.getString("imageName")
        val bmp = if (imgPath != null) getBitmapFromPath(imgPath) else getSvgBitmap()

        bmp?.let {
            imageView.apply {
                minimumWidth = (bmp.width * 0.5).toInt()
                minimumHeight = (bmp.height * 0.5).toInt()
                maxWidth = (bmp.width * 0.5).toInt()
                maxHeight = (bmp.height * 0.5).toInt()
                setImageBitmap(bmp)
            }
        }
    }

    private fun getBitmapFromPath(path: String): Bitmap? = try {
        BitmapFactory.decodeStream(openFileInput(path))
    } catch (ignored: FileNotFoundException) {
        null
    }

    private fun getSvgBitmap(): Bitmap? {
        val transform = SVGRender.transform(8.0, 0xFF800000.toInt())
        return SVGRender.render(assets, "DefaultStyle.bundle/poi_playground.svg", transform)
    }
}
