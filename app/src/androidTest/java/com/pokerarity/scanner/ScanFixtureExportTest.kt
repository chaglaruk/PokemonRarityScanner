package com.pokerarity.scanner

import android.content.ContextWrapper
import android.content.Intent
import android.graphics.Bitmap
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.pokerarity.scanner.ui.debug.ScanFixtureExportReceiver
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ScanFixtureExportTest {
    @Test
    fun internalBroadcastPermissionUsesTheQaApplicationIdentity() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val permissionName = "${BuildConfig.APPLICATION_ID}.permission.INTERNAL_BROADCAST"
        val permission = context.packageManager.getPermissionInfo(permissionName, 0)

        assertEquals(permissionName, permission.name)
        assertEquals(BuildConfig.APPLICATION_ID, permission.packageName)
    }

    @Test
    fun exportCase() {
        val arguments = InstrumentationRegistry.getArguments()
        val caseIdArgument = arguments.getString("case_id")
        assumeTrue(
            "case_id is required for the opt-in fixture export test",
            !caseIdArgument.isNullOrBlank(),
        )
        val caseId = requireNotNull(caseIdArgument)
        require(caseId.matches(Regex("[A-Za-z0-9_-]{1,80}")))

        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val isolatedRoot = Files.createTempDirectory(context.cacheDir.toPath(), "fixture-export-test-").toFile()
        var bitmap: Bitmap? = null

        try {
            val isolatedCache = File(isolatedRoot, "cache").apply { mkdirs() }
            val isolatedExternalFiles = File(isolatedRoot, "external").apply { mkdirs() }
            val isolatedContext = object : ContextWrapper(context) {
                override fun getCacheDir(): File = isolatedCache

                override fun getExternalFilesDir(type: String?): File = isolatedExternalFiles
            }
            val sourceImage = File(isolatedCache, "scan_fixture_export_test.png")
            val createdBitmap = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
            bitmap = createdBitmap
            val intent = Intent(ACTION_EXPORT_SCAN_FIXTURES)
                .putExtra(ScanFixtureExportReceiver.EXTRA_CASE_ID, caseId)

            FileOutputStream(sourceImage).use { output ->
                assertTrue(createdBitmap.compress(Bitmap.CompressFormat.PNG, 100, output))
            }
            ScanFixtureExportReceiver().onReceive(isolatedContext, intent)

            val exportDirectory = File(isolatedExternalFiles, "fixtures/$caseId")
            val exportedImage = File(exportDirectory, sourceImage.name)
            assertTrue(File(exportDirectory, "manifest_template.json").isFile)
            assertTrue(exportedImage.isFile)
            assertArrayEquals(sourceImage.readBytes(), exportedImage.readBytes())
        } finally {
            bitmap?.recycle()
            isolatedRoot.deleteRecursively()
        }
    }

    private companion object {
        const val ACTION_EXPORT_SCAN_FIXTURES = "com.pokerarity.scanner.EXPORT_SCAN_FIXTURES"
    }
}
