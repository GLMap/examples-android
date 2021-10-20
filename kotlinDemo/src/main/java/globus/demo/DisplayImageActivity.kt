package globus.demo

import android.app.Activity
import android.graphics.BitmapFactory
import android.os.Bundle
import android.widget.ImageView
import globus.glmap.ImageManager
import java.io.FileNotFoundException

class DisplayImageActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.display_image)
        val b = intent.extras
        val imgPath = b?.getString("imageName")
        if (imgPath != null) {
            try {
                val bmp = BitmapFactory.decodeStream(openFileInput(imgPath))
                val imageView = findViewById<ImageView>(R.id.image_view)
                imageView.minimumWidth = (bmp.width * 0.5).toInt()
                imageView.minimumHeight = (bmp.height * 0.5).toInt()
                imageView.maxWidth = (bmp.width * 0.5).toInt()
                imageView.maxHeight = (bmp.height * 0.5).toInt()
                imageView.setImageBitmap(bmp)
            } catch (ignored: FileNotFoundException) {
            }
        } else {
            val mgr = ImageManager(this.assets, 1f)
            val bmp = mgr.open("DefaultStyle.bundle/theme_park.svgpb", 4f, -0x800000)!!
            // Bitmap bmp = mgr.open("star.svgpb", 4, 0xFFFFFFFF);
            val imageView = findViewById<ImageView>(R.id.image_view)
            imageView.minimumWidth = bmp.width * 2
            imageView.minimumHeight = bmp.height * 2
            imageView.maxWidth = bmp.width * 2
            imageView.maxHeight = bmp.height * 2
            imageView.setImageBitmap(bmp)
        }
    }
}
