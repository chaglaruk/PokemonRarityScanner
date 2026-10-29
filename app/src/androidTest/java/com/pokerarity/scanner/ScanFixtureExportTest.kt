package com.pokerarity.scanner

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.pokerarity.scanner.ui.debug.ScanFixtureExportReceiver
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
    fun qaFixtureExportReceiverIsEnabledAndPrivate() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val receiver = context.packageManager.getReceiverInfo(
            ComponentName(context, ScanFixtureExportReceiver::class.java),
            PackageManager.MATCH_DISABLED_COMPONENTS,
        )

        assertTrue("The QA build must enable fixture export for its opt-in test", receiver.enabled)
        assertFalse("Fixture export must remain private to the QA app", receiver.exported)
    }

    @Test
    fun packageScopedBroadcastExportsFixtureThroughQaManifest() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val existingScans = context.cacheDir.listFiles { file ->
            file.isFile && file.name.startsWith("scan_") && file.name.endsWith(".png")
        }.orEmpty()
        assumeTrue(
            "The synthetic broadcast test must not copy real QA scans",
            existingScans.isEmpty(),
        )
        val caseId = "broadcast-${UUID.randomUUID()}"
        val source = File(context.cacheDir, "scan_$caseId.png")
        val exportDirectory = File(context.getExternalFilesDir(null), "fixtures/$caseId")
        assertTrue("The test source path must be unique", !source.exists())
        assertTrue("The test export path must be unique", !exportDirectory.exists())
        val bitmap = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)

        try {
            FileOutputStream(source).use { output ->
                assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output))
            }
            val intent = Intent(ACTION_EXPORT_SCAN_FIXTURES)
                .setPackage(context.packageName)
                .putExtra(ScanFixtureExportReceiver.EXTRA_CASE_ID, caseId)
            sendFixtureExportAndWait(context, intent)

            val exportedImage = File(exportDirectory, source.name)
            assertTrue("The QA manifest receiver must export the fixture", exportedImage.isFile)
            assertArrayEquals(source.readBytes(), exportedImage.readBytes())
        } finally {
            bitmap.recycle()
            source.delete()
            exportDirectory.deleteRecursively()
        }
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

    /**
     * Opt-in export for the real QA app cache. Unlike [exportCase], this method neither
     * seeds synthetic images nor deletes its persistent output. The host script supplies
     * a unique case ID, then retrieves the exported files after instrumentation finishes.
     */
    @Test
    fun exportExistingCase() {
        val caseId = InstrumentationRegistry.getArguments().getString("case_id")
        assumeTrue("case_id is required for opt-in real fixture export", !caseId.isNullOrBlank())
        val confirmedCaseId = requireNotNull(caseId)
        require(confirmedCaseId.matches(Regex("[A-Za-z0-9_-]{1,80}")))
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val sourceScans = context.cacheDir.listFiles { file ->
            file.isFile && file.name.startsWith("scan_") && file.name.endsWith(".png")
        }.orEmpty()
        assertTrue("No real scan_*.png files exist in the QA app cache; capture a scan first", sourceScans.isNotEmpty())
        val exportDirectory = File(context.getExternalFilesDir(null), "fixtures/$confirmedCaseId")
        assertTrue("Refusing to replace an existing export case", !exportDirectory.exists())
        val intent = Intent(ACTION_EXPORT_SCAN_FIXTURES)
            .setPackage(context.packageName)
            .putExtra(ScanFixtureExportReceiver.EXTRA_CASE_ID, confirmedCaseId)
        sendFixtureExportAndWait(context, intent)

        assertTrue(File(exportDirectory, "manifest_template.json").isFile)
        val exportedPngs = exportDirectory.listFiles { file ->
            file.isFile && file.name.endsWith(".png")
        }.orEmpty()
        assertEquals(sourceScans.map { it.name }.toSet(), exportedPngs.map { it.name }.toSet())
        sourceScans.forEach { source ->
            assertTrue("The source QA scan must remain available", source.isFile)
            assertArrayEquals("Exported scan bytes must match the QA source", source.readBytes(),
                File(exportDirectory, source.name).readBytes())
        }
    }

    private fun sendFixtureExportAndWait(context: Context, intent: Intent) {
        val finished = CountDownLatch(1)
        context.sendOrderedBroadcast(
            intent,
            null,
            object : BroadcastReceiver() {
                override fun onReceive(context: Context, intent: Intent) {
                    finished.countDown()
                }
            },
            Handler(Looper.getMainLooper()),
            0,
            null,
            null,
        )
        assertTrue("The fixture export broadcast did not finish", finished.await(5, TimeUnit.SECONDS))
    }

    private companion object {
        const val ACTION_EXPORT_SCAN_FIXTURES = "com.pokerarity.scanner.EXPORT_SCAN_FIXTURES"
    }
}
