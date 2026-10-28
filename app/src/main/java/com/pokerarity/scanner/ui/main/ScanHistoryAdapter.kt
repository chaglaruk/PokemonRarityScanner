// Amaç: ScanHistoryEntity verilerini RecyclerView listesinde göstermek için adaptör mantığını tanımlamak.
package com.pokerarity.scanner.ui.main

import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.pokerarity.scanner.R
import com.pokerarity.scanner.data.local.db.ScanHistoryEntity
import com.pokerarity.scanner.data.model.RarityTier
import com.pokerarity.scanner.data.model.RecognitionIdentityHistoryCodec
import com.pokerarity.scanner.data.model.RecognitionTruth
import com.pokerarity.scanner.data.model.recognizedShiny
import com.pokerarity.scanner.data.model.displayLabel
import com.pokerarity.scanner.data.model.cpDisplayText
import com.pokerarity.scanner.data.model.formatIvEstimateText
import com.pokerarity.scanner.data.model.formatIvLevelSummary
import com.pokerarity.scanner.data.model.decodeIvLevelAlternatives
import com.pokerarity.scanner.data.model.parseIvSolveMode
import com.pokerarity.scanner.databinding.ItemScanHistoryBinding
import com.pokerarity.scanner.util.DateParseUtils
import com.pokerarity.scanner.util.DateParseUtils.formatDate

class ScanHistoryAdapter(
    private val onItemClick: (ScanHistoryEntity) -> Unit
) : ListAdapter<ScanHistoryEntity, ScanHistoryAdapter.ViewHolder>(DiffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemScanHistoryBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ViewHolder(
        private val binding: ItemScanHistoryBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(scan: ScanHistoryEntity) {
            val context = binding.root.context
            val tier = try { RarityTier.valueOf(scan.rarityTier) } catch (e: Exception) { RarityTier.COMMON }
            val tierColor = ContextCompat.getColor(context, getTierColorRes(tier))

            // Score
            binding.tvItemScore.text = scan.rarityScore.toString()
            binding.tvItemScore.setTextColor(tierColor)
            (binding.viewScoreBg.background as? GradientDrawable)?.setStroke(
                (2 * context.resources.displayMetrics.density).toInt(),
                tierColor
            )

            // Name
            binding.tvItemName.text = scan.pokemonName ?: "Unknown"

            // Tier
            binding.tvItemTier.text = tier.label
            binding.tvItemTier.setTextColor(tierColor)

            // Attributes
            val attrs = buildString {
                if (scan.recognizedShiny == RecognitionTruth.TRUE) append("✨")
                if (scan.isShadow) append("👤")
                if (scan.isLucky) append("🍀")
                if (scan.hasCostume) append("🎩")
            }
            val formText = RecognitionIdentityHistoryCodec.formPresentation(
                RecognitionIdentityHistoryCodec.decode(scan.recognitionIdentityJson)
            )
            binding.tvItemAttributes.text = listOf(attrs, scan.recognizedShiny.displayLabel, formText)
                .filterNotNull().filter { it.isNotBlank() }.joinToString(" ")

            // Date
            binding.tvItemDate.text = formatDate(scan.timestamp, DateParseUtils.MMM_DD_YYYY_FORMATTER)

            // CP
            val ivLevelText = historyIvLevelSummary(scan)
            binding.tvItemCP.text = listOf("CP ${cpDisplayText(scan.cp)}", ivLevelText)
                .filterNotNull().joinToString(" | ")

            // Click
            binding.root.setOnClickListener { onItemClick(scan) }
        }

        private fun getTierColorRes(tier: RarityTier): Int {
            return when (tier) {
                RarityTier.COMMON -> R.color.tier_common
                RarityTier.UNCOMMON -> R.color.tier_uncommon
                RarityTier.RARE -> R.color.tier_rare
                RarityTier.EPIC -> R.color.tier_epic
                RarityTier.LEGENDARY -> R.color.tier_legendary
                RarityTier.MYTHICAL -> R.color.tier_mythical
                RarityTier.GOD_TIER -> R.color.tier_god_tier
            }
        }
    }

    object DiffCallback : DiffUtil.ItemCallback<ScanHistoryEntity>() {
        override fun areItemsTheSame(a: ScanHistoryEntity, b: ScanHistoryEntity) = a.id == b.id
        override fun areContentsTheSame(a: ScanHistoryEntity, b: ScanHistoryEntity) = a == b
    }
}

internal fun historyIvLevelSummary(scan: ScanHistoryEntity): String? = formatIvLevelSummary(
    ivText = formatIvEstimateText(scan.ivSolveMode, scan.ivMin, scan.ivMax),
    ivExact = null,
    solveMode = parseIvSolveMode(scan.ivSolveMode),
    levelMin = scan.ivLevelMin,
    levelMax = scan.ivLevelMax,
    levelAlternatives = decodeIvLevelAlternatives(scan.ivLevelAlternatives)
)
