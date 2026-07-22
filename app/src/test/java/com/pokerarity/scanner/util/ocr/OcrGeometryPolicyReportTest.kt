package com.pokerarity.scanner.util.ocr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.security.MessageDigest

class OcrGeometryPolicyReportTest {

    @Test
    fun generatedReportMatchesExpectedAndWritesIgnoredActual() {
        val json = generateReport()
        val actual = File(findRepoRoot(), ACTUAL_REPORT_PATH).apply {
            parentFile.mkdirs()
            writeText(json, Charsets.UTF_8)
        }
        val expected = requireNotNull(javaClass.classLoader.getResourceAsStream(EXPECTED_RESOURCE_NAME))
            .bufferedReader(Charsets.UTF_8)
            .use { it.readText().replace("\r\n", "\n") }
        assertEquals(expected, json)
        assertTrue(actual.isFile)
    }

    @Test
    fun repeatedGenerationIsByteIdenticalAndComplete() {
        val first = generateReport()
        val second = generateReport()
        assertEquals(first, second)
        assertEquals(sha256(first), sha256(second))
        assertFalse(first.contains("\r"))
        assertTrue(first.endsWith("\n"))
        assertTrue(first.contains("\"plans\": ["))
        assertEquals(24, "\"requestedPolicy\"".toRegex().findAll(first).count())
        assertEquals(24, "\"effectivePolicy\"".toRegex().findAll(first).count())
        assertTrue(first.contains("\"effectivePolicy\": \"baseline_900_width\""))
    }

    @Test
    fun reportIsPortableSyntheticOnlyAndKeepsLimitationsExplicit() {
        val json = generateReport()
        forbiddenStrings.forEach {
            assertFalse("forbidden report string: $it", json.contains(it))
        }
        assertTrue(json.contains("\"evidenceClass\": \"synthetic_geometry_only\""))
        assertTrue(json.contains("\"productionRuntimeChanged\": false"))
        assertTrue(json.contains("real 1080/1440 and shifted/scrolled fixtures remain required"))
        assertTrue(json.contains("not accuracy evidence"))
        assertTrue(json.contains("cannot authorize a production-default change"))
    }

    private fun generateReport(): String {
        val cases = listOf("1080x2340" to OcrImageSize(1080, 2340), "1440x3120" to OcrImageSize(1440, 3120))
        val policies = OcrPolicy.entries
        val fields = listOf(OcrField.NAME, OcrField.CANDY, OcrField.CP, OcrField.HP)
        val plans = cases.flatMap { (caseId, size) ->
            policies.flatMap { policy ->
                fields.map { field -> caseId to OcrImagePolicy.plan(policy, field, size) }
            }
        }
        return buildString {
            appendLine("{")
            appendLine("  \"schemaVersion\": 1,")
            appendLine("  \"evidenceClass\": \"synthetic_geometry_only\",")
            appendLine("  \"productionRuntimeChanged\": false,")
            appendLine("  \"productionDefault\": \"baseline_900_width\",")
            appendLine("  \"baselineMaximumWidth\": 900,")
            appendLine("  \"boundedPolicyRequiresExplicitConfiguration\": true,")
            appendLine("  \"limitations\": [")
            appendLine("    \"synthetic geometry and planning evidence only\",")
            appendLine("    \"contains no OCR result and is not accuracy evidence\",")
            appendLine("    \"cannot authorize a production-default change\",")
            appendLine("    \"real 1080/1440 and shifted/scrolled fixtures remain required\"")
            appendLine("  ],")
            appendLine("  \"sourceCases\": [")
            cases.forEachIndexed { index, (id, size) ->
                append("    {\"id\": \"").append(id).append("\", \"width\": ")
                    .append(size.width).append(", \"height\": ").append(size.height).append("}")
                appendLine(if (index == cases.lastIndex) "" else ",")
            }
            appendLine("  ],")
            appendLine("  \"plans\": [")
            plans.forEachIndexed { index, (caseId, plan) ->
                append("    {\"sourceCase\": \"").append(caseId)
                    .append("\", \"requestedPolicy\": \"").append(plan.requestedPolicy.code)
                    .append("\", \"effectivePolicy\": \"").append(plan.effectivePolicy.code)
                    .append("\", \"field\": \"").append(plan.field.code)
                    .append("\", \"sourceWidth\": ").append(plan.sourceFrame.width)
                    .append(", \"sourceHeight\": ").append(plan.sourceFrame.height)
                    .append(", \"effectiveFrameWidth\": ").append(plan.effectiveFrame.width)
                    .append(", \"effectiveFrameHeight\": ").append(plan.effectiveFrame.height)
                    .append(", \"cropOrder\": \"").append(plan.cropOrder.code)
                    .append("\", \"requiresExplicitUpscaleBounds\": ").append(plan.requiresExplicitUpscaleBounds)
                    .append(", \"forcedBackToBaseline\": ").append(plan.forcedBackToBaseline)
                    .append(", \"reasonCodes\": [")
                    .append(plan.reasonCodes.joinToString(", ") { "\"$it\"" })
                    .append("]}")
                appendLine(if (index == plans.lastIndex) "" else ",")
            }
            appendLine("  ],")
            appendLine(
                "  \"summary\": {\"sourceCaseCount\": ${cases.size}, " +
                    "\"planCount\": ${plans.size}, \"syntheticOnly\": true}"
            )
            appendLine("}")
        }
    }

    private fun findRepoRoot(): File {
        var directory = File(System.getProperty("user.dir") ?: ".").absoluteFile
        repeat(6) {
            if (File(directory, "settings.gradle.kts").isFile) return directory
            directory = directory.parentFile ?: return@repeat
        }
        error("Repository root not found")
    }

    private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }

    private companion object {
        const val ACTUAL_REPORT_PATH = "app/build/reports/recognition/ocr_geometry_policy_actual.json"
        const val EXPECTED_RESOURCE_NAME = "ocr_geometry_policy_expected.json"
        val forbiddenStrings = listOf(
            "C:\\", "/Users/", "/home/", "rawOcr", "timestamp", "deviceId", "serial", "pokemon",
            "acceptedWrong", "accuracy improvement", "61%", "\\\\"
        )
    }
}
