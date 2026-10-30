package com.pokerarity.scanner.data.repository

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.pokerarity.scanner.BuildConfig
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

@RunWith(RobolectricTestRunner::class)
class RemoteMetadataSyncPolicyTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun readTextIfExists_ignoresExistingRemoteOverrideWhenBuildDisablesSync() {
        withPreservedFiles("metadata_policy_test.json") {
            RemoteMetadataStore.writeTextAtomically(context, "metadata_policy_test.json", "old-remote-data")

            val resolved = RemoteMetadataStore.readTextIfExists(context, "metadata_policy_test.json")

            if (BuildConfig.REMOTE_METADATA_SYNC_ENABLED) {
                assertEquals("old-remote-data", resolved)
            } else {
                assertNull(resolved)
            }
        }
    }

    @Test
    fun refresh_disabledDoesNotFetchOrChangeExistingMetadata() {
        withPreservedFiles("metadata_policy_test.json", "version.txt") {
            RemoteMetadataStore.writeTextAtomically(context, "metadata_policy_test.json", "old-remote-data")
            RemoteMetadataStore.writeVersionAtomically(context, "old-revision")
            var fetchCount = 0
            val manager = RemoteMetadataSyncManager(
                context = context,
                remoteMetadataSyncEnabled = false,
                fetchTextOverride = {
                    fetchCount++
                    error("Disabled metadata sync must not fetch")
                },
            )

            runBlocking { manager.refresh() }

            assertEquals(0, fetchCount)
            assertEquals("old-revision", RemoteMetadataStore.versionFile(context).readText())
            assertEquals(
                if (BuildConfig.REMOTE_METADATA_SYNC_ENABLED) "old-revision" else null,
                RemoteMetadataSyncManager.currentVersion(context),
            )
            assertEquals(
                "old-remote-data",
                RemoteMetadataStore.readTextIfExists(
                    context,
                    "metadata_policy_test.json",
                    remoteMetadataEnabled = true,
                ),
            )
        }
    }

    @Test
    fun refresh_enabledStillPublishesVerifiedRemoteMetadata() {
        val targetName = "metadata_policy_test.json"
        val manifestUrl = "https://raw.githubusercontent.com/chaglaruk/PokemonRarityScanner/main/metadata_manifest.json"
        val payloadUrl =
            "https://raw.githubusercontent.com/chaglaruk/PokemonRarityScanner/main/" +
                "app/src/main/assets/data/variant_catalog.json"
        val payload = "{\"revision\":\"production-test\"}"
        val checksum = RemoteMetadataSyncManager.sha256(payload)
        val manifest = """
            {"version":"production-test-revision","files":{"$targetName":{"url":"$payloadUrl","sha256":"$checksum"}}}
        """.trimIndent()
        val responses = mapOf(manifestUrl to manifest, payloadUrl to payload)

        withPreservedFiles(targetName, "version.txt") {
            var fetchCount = 0
            val manager = RemoteMetadataSyncManager(
                context = context,
                remoteMetadataSyncEnabled = true,
                fetchTextOverride = { url ->
                    fetchCount++
                    responses[url] ?: error("Unexpected metadata URL")
                },
            )

            runBlocking { manager.refresh() }

            assertEquals(2, fetchCount)
            assertEquals("production-test-revision", RemoteMetadataStore.versionFile(context).readText())
            assertEquals(
                payload,
                RemoteMetadataStore.readTextIfExists(context, targetName, remoteMetadataEnabled = true),
            )
        }
    }

    private fun withPreservedFiles(vararg names: String, block: () -> Unit) {
        val files = names.map { RemoteMetadataStore.fileFor(context, it) }
        val previous = files.associateWith { file -> file.takeIf(File::exists)?.readBytes() }
        try {
            block()
        } finally {
            previous.forEach { (file, bytes) ->
                if (bytes == null) {
                    file.delete()
                } else {
                    file.parentFile?.mkdirs()
                    file.writeBytes(bytes)
                }
            }
        }
    }
}
