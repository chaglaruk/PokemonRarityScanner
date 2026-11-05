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
    private val gson = Gson()

    internal data class Report(
        val schemaVersion: Int = 1,
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
        if (!QaVariantDiagnosticsPolicy.isEnabled(BuildConfig.BUILD_TYPE)) return null
        val summary = variantSummary ?: return null
        if (summary.costumeDecisionDiagnostics == null) return null

        val safeId = scanId.filter { it.isLetterOrDigit() || it == '-' || it == '_' }
            .take(64)
            .ifBlank { "qa-${System.currentTimeMillis()}" }
        val report = Report(
            scanId = safeId,
            sourceSha = BuildConfig.QA_SOURCE_SHA.take(40),
            appVersion = BuildConfig.VERSION_NAME.take(40),
            metadataRevision = metadataRevision?.take(64),
            species = (pokemon.realName ?: pokemon.name)?.take(40),
            cp = pokemon.cp,
            hp = pokemon.hp,
            maxHp = pokemon.maxHp,
            shiny = pokemon.recognitionIdentity?.shiny?.name,
            costume = pokemon.recognitionIdentity?.costume?.name,
            formStatus = pokemon.recognitionIdentity?.formStatus?.name,
            form = pokemon.recognitionIdentity?.knownForm?.take(64),
            variantSummary = summary
        )
        val json = gson.toJson(report)
        if (json.length > MAX_JSON_CHARS) return null

        return runCatching {
            val directory = File(context.filesDir, DIRECTORY).apply { mkdirs() }
            File(directory, "$safeId.json").apply { writeText(json) }
        }.getOrNull()
    }

    internal fun buildJsonForTest(report: Report): String = gson.toJson(report)
}
