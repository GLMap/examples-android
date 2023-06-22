package globus.javaDemo;

import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.location.Location;
import androidx.annotation.NonNull;
import globus.glmap.GLMapAnimation;
import globus.glmap.GLMapDrawable;
import globus.glmap.GLMapImage;
import globus.glmap.GLMapVectorCascadeStyle;
import globus.glmap.GLMapVectorLayer;
import globus.glmap.GLMapVectorObject;
import globus.glmap.GLMapViewRenderer;
import globus.glmap.MapPoint;
import globus.glmap.SVGRender;
import java.util.Objects;

/** Created by destman on 6/1/17. */
class CurLocationHelper implements DemoApp.LocationCallback {
    private GLMapImage userMovementImage, userLocationImage;
    private GLMapVectorLayer accuracyCircle;
    private boolean isFollowLocationEnabled = false;
    private final GLMapViewRenderer renderer;
    CurLocationHelper(GLMapViewRenderer renderer) { this.renderer = renderer; }

    @Override
    public void onLocationChanged(@NonNull Location location)
    {
        final MapPoint position = MapPoint.CreateFromGeoCoordinates(location.getLatitude(), location.getLongitude());
        if (isFollowLocationEnabled) {
            renderer.animate(animation -> animation.flyToPoint(position));
        }

        // Create drawables if not exist and set initial positions.
        if (userLocationImage == null) {
            AssetManager manager = renderer.attachedView.getContext().getAssets();
            Bitmap locationImage = Objects.requireNonNull(SVGRender.render(manager, "circle_new.svg", SVGRender.transform(renderer.screenScale)));
            userLocationImage = new GLMapImage(100);
            userLocationImage.setBitmap(locationImage);
            userLocationImage.setHidden(true);
            userLocationImage.setOffset(locationImage.getWidth() / 2, locationImage.getHeight() / 2);
            userLocationImage.setPosition(position);
            renderer.add(userLocationImage);
            locationImage.recycle();
        }

        if (userMovementImage == null) {
            AssetManager manager = renderer.attachedView.getContext().getAssets();
            Bitmap movementImage = Objects.requireNonNull(SVGRender.render(manager, "arrow_new.svg", SVGRender.transform(renderer.screenScale)));
            userMovementImage = new GLMapImage(100);
            userLocationImage.setBitmap(movementImage);
            userMovementImage.setHidden(true);
            userMovementImage.setOffset(movementImage.getWidth() / 2, movementImage.getHeight() / 2);
            userMovementImage.setRotatesWithMap(true);
            userMovementImage.setPosition(position);
            if (location.hasBearing())
                userLocationImage.setAngle(-location.getBearing());
            renderer.add(userMovementImage);
            movementImage.recycle();
        }

        // Select what image to display
        if (location.hasBearing()) {
            userMovementImage.setHidden(false);
            userLocationImage.setHidden(true);
        } else {
            userLocationImage.setHidden(false);
            userMovementImage.setHidden(true);
        }

        // Calculate radius of accuracy circle
        final float r = (float)renderer.convertMetersToInternal(location.getAccuracy());
        // If accuracy circle drawable not exits - create it and set initial position
        if (accuracyCircle == null) {
            final int pointCount = 100;
            // Use MapPoint to avoid distortions of circle
            MapPoint[] points = new MapPoint[pointCount];
            for (int i = 0; i < pointCount; i++) {
                double f = 2 * Math.PI * i / pointCount;
                // If radius of circle will be 1 only 2 points will be in final geometry (after
                // douglas-peucker)
                points[i] = new MapPoint(Math.sin(f) * 2048, Math.cos(f) * 2048);
            }
            GLMapVectorObject circle = GLMapVectorObject.createPolygon(new MapPoint[][] {points}, null);

            accuracyCircle = new GLMapVectorLayer(99);
            accuracyCircle.setTransformMode(GLMapDrawable.TransformMode.Custom);
            accuracyCircle.setPosition(position);
            accuracyCircle.setScale(r / 2048.0f);
            accuracyCircle.setVectorObject(
                circle,
                Objects.requireNonNull(GLMapVectorCascadeStyle.createStyle(
                    "area{layer:100; width:1pt; fill-color:#3D99FA26;"
                    + " color:#3D99FA26;}")),
                null);
            renderer.add(accuracyCircle);
        }

        renderer.animate(animation -> {
            animation.setTransition(GLMapAnimation.Linear);
            animation.setDuration(1);
            userMovementImage.setPosition(position);
            userLocationImage.setPosition(position);
            accuracyCircle.setPosition(position);
            accuracyCircle.setScale(r / 2048.0f);
            if (location.hasBearing())
                userLocationImage.setAngle(-location.getBearing());
        });
    }
}
