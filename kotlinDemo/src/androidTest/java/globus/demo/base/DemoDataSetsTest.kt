package globus.demo.base

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import globus.glmap.GLMapBBox
import globus.glmap.GLMapInfo
import globus.glmap.GLMapManager
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DemoDataSetsTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun repeatedAssetRegistrationSucceeds() {
        repeat(3) { assertTrue(DemoDataSets.registerAsset(GLMapInfo.DataSet.MAP, "Montenegro.vm", context.assets)) }
    }

    @Test fun repeatedFileRegistrationNeverDeletesDownloadedData() {
        val file = File.createTempFile("demo-dataset-test-", ".vm", context.cacheDir)
        try {
            context.assets.open("Montenegro.vm").use { input -> file.outputStream().use(input::copyTo) }
            val size = file.length()
            repeat(3) {
                assertTrue(DemoDataSets.registerFile(GLMapInfo.DataSet.MAP, GLMapBBox(), file))
                assertTrue(file.exists())
                assertEquals(size, file.length())
            }
        } finally {
            GLMapManager.RemoveDataSet(GLMapInfo.DataSet.MAP, file.absolutePath)
            file.delete()
        }
    }

    @Test fun lateDownloadCannotOverwriteExistingRegisteredFile() {
        val destination = File.createTempFile("demo-installed-", ".vm", context.cacheDir)
        val temporary = File.createTempFile("demo-late-", ".part", context.cacheDir)
        try {
            context.assets.open("Montenegro.vm").use { input -> destination.outputStream().use(input::copyTo) }
            assertTrue(DemoDataSets.registerFile(GLMapInfo.DataSet.MAP, GLMapBBox(), destination))
            val length = destination.length()
            temporary.writeText("This must not replace a registered dataset")
            assertTrue(DemoDataSets.install(GLMapInfo.DataSet.MAP, GLMapBBox(), temporary, destination))
            assertEquals(length, destination.length())
        } finally {
            GLMapManager.RemoveDataSet(GLMapInfo.DataSet.MAP, destination.absolutePath)
            destination.delete()
            temporary.delete()
        }
    }

    @Test fun emptyFileIsPreservedButNotRegistered() {
        val file = File.createTempFile("demo-empty-", ".vm", context.cacheDir)
        try {
            assertFalse(DemoDataSets.registerFile(GLMapInfo.DataSet.MAP, GLMapBBox(), file))
            assertTrue(file.exists())
        } finally {
            file.delete()
        }
    }
}
