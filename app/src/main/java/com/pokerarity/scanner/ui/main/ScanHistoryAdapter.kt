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
import com.pokerarity.scanner.databinding.ItemScanHistoryBinding
import com.pokerarity.scanner.util.DateParseUtils
import com.pokerarity.scanner.util.DateParseUtils.formatDate

class ScanHistoryAdapter(
    private val onItemClick: (ScanHistoryEntity) -> Unit
) : ListAdapter<ScanHistoryEntity, ScanHistoryAdapter.ViewHolder>(DiffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemScanHistoryBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
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
            val tierCode = scan.collectionTier.ifBlank { scan.rarityTier.ifBlank { "COMMON" } }.uppercase()
            val tierColor = ContextCompat.getColor(context, getTierColorRes(tierCode))
            val score = scan.collectionScore.takeIf { it > 0 } ?: scan.rarityScore

            binding.tvItemScore.text = score.toString()
            binding.tvItemScore.setTextColor(tierColor)
            (binding.viewScoreBg.background as? GradientDrawable)?.setStroke(
                (2 * context.resources.displayMetrics.density).toInt(),
                tierColor
            )

            binding.tvItemName.text = scan.pokemonName ?: "Unknown"

            binding.tvItemTier.text = tierLabel(tierCode)
            binding.tvItemTier.setTextColor(tierColor)

            val attrs = buildString {
                if (scan.isShiny) append("Shiny ")
                if (scan.isShadow) append("Shadow ")
                if (scan.isLucky) append("Lucky ")
                if (scan.hasCostume) append("Costume ")
                if (scan.isEdited) append("Edited ")
            }.trim()
            binding.tvItemAttributes.text = attrs

            binding.tvItemDate.text = formatDate(scan.timestamp, DateParseUtils.MMM_DD_YYYY_FORMATTER)
            binding.tvItemCP.text = if (scan.cp != null && scan.cp > 0) "CP ${scan.cp}" else ""
            binding.root.setOnClickListener { onItemClick(scan) }
        }

        private fun getTierColorRes(tierCode: String): Int {
            return when (tierCode) {
                "COMMON" -> R.color.tier_common
                "UNCOMMON" -> R.color.tier_uncommon
                "NOTABLE", "RARE" -> R.color.tier_rare
                "VERY_RARE", "EPIC" -> R.color.tier_epic
                "ULTRA_RARE", "LEGENDARY", "MYTHICAL" -> R.color.tier_legendary
                "TROPHY", "GOD_TIER" -> R.color.tier_god_tier
                else -> R.color.tier_common
            }
        }

        private fun tierLabel(tierCode: String): String {
            return when (tierCode) {
                "VERY_RARE" -> "Very Rare"
                "ULTRA_RARE" -> "Ultra Rare"
                "GOD_TIER" -> "Trophy"
                else -> tierCode.lowercase()
                    .split("_")
                    .joinToString(" ") { part -> part.replaceFirstChar { it.uppercase() } }
            }
        }
    }

    object DiffCallback : DiffUtil.ItemCallback<ScanHistoryEntity>() {
        override fun areItemsTheSame(a: ScanHistoryEntity, b: ScanHistoryEntity) = a.id == b.id
        override fun areContentsTheSame(a: ScanHistoryEntity, b: ScanHistoryEntity) = a == b
    }
}
