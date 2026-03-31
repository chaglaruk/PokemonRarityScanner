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
import com.pokerarity.scanner.databinding.ItemScanHistoryBinding
import java.text.SimpleDateFormat
import java.util.Locale

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

        private val dateFormat = SimpleDateFormat("MMM dd, yyyy", Locale.US)

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
                if (scan.isShiny) append("✨")
                if (scan.isShadow) append("👤")
                if (scan.isLucky) append("🍀")
                if (scan.hasCostume) append("🎩")
            }
            binding.tvItemAttributes.text = attrs

            // Date
            binding.tvItemDate.text = dateFormat.format(scan.timestamp)

            // CP
            binding.tvItemCP.text = if (scan.cp != null && scan.cp > 0) "CP ${scan.cp}" else ""

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
