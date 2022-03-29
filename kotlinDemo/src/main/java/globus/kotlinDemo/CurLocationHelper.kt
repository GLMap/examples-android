package globus.kotlinDemo

import android.location.Location
import globus.glmap.*
import kotlin.math.cos
import kotlin.math.sin

/** Created by destman on 6/1/17.  */
internal class CurLocationHelper(
    private val renderer: GLMapViewRenderer,
    private val imageManager: ImageManager
) :
    DemoApp.LocationCallback {
    private var userMovementImage: GLMapDrawable
    private var userLocationImage: GLMapDrawable
    private var accuracyCircle: GLMapDrawable
    private var lastLocation: Location? = null
    var isFollowLocationEnabled = false

    init {
        val locationImage = imageManager.open("circle_new.svg", 1f, 0)!!
        userLocationImage = GLMapDrawable(locationImage, 100)
        userLocationImage.isHidden = true
        userLocationImage.setOffset(locationImage.width / 2, locationImage.height / 2)
        renderer.add(userLocationImage)
        locationImage.recycle()

        val movementImage = imageManager.open("arrow_new.svg", 1f, 0)!!
        userMovementImage = GLMapDrawable(movementImage, 100)
        userMovementImage.isHidden = true
        userMovementImage.setOffset(movementImage.width / 2, movementImage.height / 2)
        userMovementImage.isRotatesWithMap = true
        renderer.add(userMovementImage)
        movementImage.recycle()

        // Calculate radius of accuracy circle
        val pointCount = 100
        // Use MapPoint to avoid distortions of circle
        val points = Array(pointCount) {
            val f = 2 * Math.PI * it / pointCount
            // If radius of circle will be 1 only 2 points will be in final geometry (after
            // douglas-peucker)
            MapPoint(sin(f) * 2048, cos(f) * 2048)
        }
        val circleStyle = GLMapVectorCascadeStyle.createStyle("area{layer:100; width:1pt; fill-color:#3D99FA26; color:#3D99FA26;}")!!
        val circle = GLMapVectorObject.createPolygon(arrayOf(points), null)
        accuracyCircle = GLMapDrawable(99)
        accuracyCircle.setTransformMode(GLMapDrawable.TransformMode.Custom)
        accuracyCircle.setVectorObject(circle, circleStyle, null)
        renderer.add(accuracyCircle)
    }

    override fun onLocationChanged(location: Location) {
        val position = MapPoint.CreateFromGeoCoordinates(location.latitude, location.longitude)
        if (isFollowLocationEnabled)
            renderer.animate { it.flyToPoint(position) }

        // Accuracy radius
        val r = renderer.convertMetersToInternal(location.accuracy.toDouble()).toFloat()

        // Set initial position
        if (lastLocation == null) {
            lastLocation = location
            userMovementImage.position = position
            userLocationImage.position = position
            if (location.hasBearing()) userLocationImage.angle = -location.bearing
            accuracyCircle.position = position
            accuracyCircle.scale = r / 2048.0f.toDouble()
        }

        // Select what image to display
        if (location.hasBearing()) {
            userMovementImage.isHidden = false
            userLocationImage.isHidden = true
        } else {
            userLocationImage.isHidden = false
            userMovementImage.isHidden = true
        }

        // If accuracy circle drawable not exits - create it and set initial position
        renderer.animate {
            it.setTransition(GLMapAnimation.Linear)
            it.setDuration(1.0)
            userMovementImage.position = position
            userLocationImage.position = position
            accuracyCircle.position = position
            accuracyCircle.scale = r / 2048.0f.toDouble()
            if (location.hasBearing()) userLocationImage.angle = -location.bearing
        }
    }
}
