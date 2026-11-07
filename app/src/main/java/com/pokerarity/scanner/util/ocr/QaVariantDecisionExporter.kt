package com.pokerarity.scanner.util.ocr

import android.content.Context
import com.google.gson.Gson
import com.pokerarity.scanner.BuildConfig
import com.pokerarity.scanner.data.model.PokemonData
import java.io.File

/** Writes a small, app-private variant trace for QA scans only. */
internal object QaVariantDecisionExporter {
    private const val DIRECTORY = "qa_variant_decisions"
    private const val MAX_JSON_CHARS = 8_192
    private const val MAX_SCAN_ID_LENGTH = 64
    private const val MAX_SOURCE_SHA_LENGTH = 40
    private const val MAX_APP_VERSION_LENGTH = 40
    private const val MAX_METADATA_REVISION_LENGTH = 64
    private const val MAX_SPECIES_LENGTH = 40
    private const val MAX_FORM_LENGTH = 64
    private val gson = Gson()

    internal data class Report(
        val schemaVersion: Int = 2,
        val scanId: String,
        val sourceSha: String,
        val appVersion: String,
        val metadataRevision: String?,
        val species: String?,
        val cp: Int?,
        val hp: Int?,
        val maxHp: Int?,
        val shiny: String?,
        val costume: String?,
        val formStatus: String?,
        val form: String?,
        val variantSummary: VariantVisualSummary
    )

    fun export(
        context: Context,
        scanId: String,
        pokemon: PokemonData,
        variantSummary: VariantVisualSummary?,
        metadataRevision: String?
    ): File? {
        val summary = variantSummary?.takeIf {
            it.costumeDecisionDiagnostics != null || it.qaShinyDiagnostics != null
        }
        if (!QaVariantDiagnosticsPolicy.isEnabled(BuildConfig.BUILD_TYPE) || summary == null) return null

        val safeId = scanId.filter { it.isLetterOrDigit() || it == '-' || it == '_' }
            .take(MAX_SCAN_ID_LENGTH)
            .ifBlank { "qa-${System.currentTimeMillis()}" }
        val report = Report(
            scanId = safeId,
            sourceSha = BuildConfig.QA_SOURCE_SHA.take(MAX_SOURCE_SHA_LENGTH),
            appVersion = BuildConfig.VERSION_NAME.take(MAX_APP_VERSION_LENGTH),
            metadataRevision = metadataRevision?.take(MAX_METADATA_REVISION_LENGTH),
            species = (pokemon.realName ?: pokemon.name)?.take(MAX_SPECIES_LENGTH),
            cp = pokemon.cp,
            hp = pokemon.hp,
            maxHp = pokemon.maxHp,
            shiny = pokemon.recognitionIdentity?.shiny?.name,
            costume = pokemon.recognitionIdentity?.costume?.name,
            formStatus = pokemon.recognitionIdentity?.formStatus?.name,
            form = pokemon.recognitionIdentity?.knownForm?.take(MAX_FORM_LENGTH),
            variantSummary = summary
        )
        val json = gson.toJson(report)
        return if (json.length <= MAX_JSON_CHARS) {
            runCatching {
                val directory = File(context.filesDir, DIRECTORY).apply { mkdirs() }
                File(directory, "$safeId.json").apply { writeText(json) }
            }.getOrNull()
        } else {
            null
        }
    }

    internal fun buildJsonForTest(report: Report): String = gson.toJson(report)
}
