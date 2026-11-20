package globus.demo.routing

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import globus.demo.R
import globus.glmap.GLMapBBox
import globus.glmap.GLMapError
import globus.glmap.GLMapInfo
import globus.glmap.GLMapManager
import globus.glmap.MapGeoPoint
import globus.glroute.CostingOptions
import globus.glroute.GLRoute
import globus.glroute.GLRoutePoint
import globus.glroute.GLRouteRequest
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class OfflineRouteTest {
    @Test fun bundledConfigBuildsOfflineRoute() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val navigation = GLMapInfo.DataSet.NAVIGATION
        // Valhalla tiles of levels 1 and 2 around Podgorica, installed (VTCR) format.
        val pack = File.createTempFile("demo-navigation-", ".rt", context.cacheDir)
        try {
            val assets = instrumentation.context.assets
            assets.open("Podgorica.rt").use { input -> pack.outputStream().use(input::copyTo) }
            assertTrue(GLMapManager.AddDataSet(navigation, GLMapBBox(), pack.absolutePath, null, null))
            val config = context.resources.openRawResource(R.raw.valhalla).use { it.readBytes().decodeToString() }
            val request = GLRouteRequest().apply {
                setAutoWithOptions(CostingOptions.Auto())
                addPoint(GLRoutePoint(MapGeoPoint(42.4413, 19.2560), Double.NaN, GLRoutePoint.Type.BREAK))
                addPoint(GLRoutePoint(MapGeoPoint(42.4300, 19.2700), Double.NaN, GLRoutePoint.Type.BREAK))
            }
            val done = CountDownLatch(1)
            var route: GLRoute? = null
            var failure: GLMapError? = null
            request.startOffline(
                config,
                object : GLRouteRequest.ResultsCallback {
                    override fun onResult(data: GLRoute) {
                        route = data
                        done.countDown()
                    }

                    override fun onError(error: GLMapError) {
                        failure = error
                        done.countDown()
                    }
                }
            )
            assertTrue("Offline route did not complete", done.await(20, TimeUnit.SECONDS))
            assertNull("Offline route failed with res/raw/valhalla.json: $failure", failure)
            assertTrue("Offline route is empty", (route?.length ?: 0.0) > 0)
        } finally {
            GLMapManager.RemoveDataSet(navigation, pack.absolutePath)
            pack.delete()
        }
    }
}
