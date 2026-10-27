package com.pokerarity.scanner

import android.content.Intent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.pokerarity.scanner.ui.debug.ScanFixtureExportReceiver
import org.junit.Assume.assumeTrue
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class ScanFixtureExportTest {
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
        val intent = Intent(ACTION_EXPORT_SCAN_FIXTURES)
            .putExtra(ScanFixtureExportReceiver.EXTRA_CASE_ID, caseId)
        ScanFixtureExportReceiver().onReceive(context, intent)

        val externalFiles = requireNotNull(context.getExternalFilesDir(null))
        val exportDirectory = File(externalFiles, "fixtures/$caseId")
        assertTrue(File(exportDirectory, "manifest_template.json").isFile)
        assertTrue(exportDirectory.listFiles()?.any { it.isFile && it.extension == "png" } == true)
    }

    private companion object {
        const val ACTION_EXPORT_SCAN_FIXTURES = "com.pokerarity.scanner.EXPORT_SCAN_FIXTURES"
    }
}
