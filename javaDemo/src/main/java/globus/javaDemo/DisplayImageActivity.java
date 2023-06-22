package globus.javaDemo;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.widget.ImageView;
import globus.glmap.SVGRender;
import java.io.FileNotFoundException;

public class DisplayImageActivity extends Activity {
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.display_image);
        Bundle b = getIntent().getExtras();
        String imgPath = b != null ? b.getString("imageName") : null;
        if (imgPath != null) {
            try {
                Bitmap bmp = BitmapFactory.decodeStream(openFileInput(imgPath));
                ImageView imageView = findViewById(R.id.image_view);
                imageView.setMinimumWidth((int)(bmp.getWidth() * 0.5));
                imageView.setMinimumHeight((int)(bmp.getHeight() * 0.5));
                imageView.setMaxWidth((int)(bmp.getWidth() * 0.5));
                imageView.setMaxHeight((int)(bmp.getHeight() * 0.5));
                imageView.setImageBitmap(bmp);
            } catch (FileNotFoundException ignored) {
            }
        } else {
            SVGRender.Transform transform = SVGRender.transform(8, 0xFF800000);
            // Bitmap bmp = SVGRender.render(getAssets(), "DefaultStyle.bundle/poi_theme_park.svg", transform);
            Bitmap bmp = SVGRender.render(getAssets(), "DefaultStyle.bundle/poi_playground.svg", transform);
            ImageView imageView = findViewById(R.id.image_view);
            imageView.setMinimumWidth(bmp.getWidth() * 2);
            imageView.setMinimumHeight(bmp.getHeight() * 2);
            imageView.setMaxWidth(bmp.getWidth() * 2);
            imageView.setMaxHeight(bmp.getHeight() * 2);
            imageView.setImageBitmap(bmp);
        }
    }
}
