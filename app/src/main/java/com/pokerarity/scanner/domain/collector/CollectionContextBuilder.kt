package com.pokerarity.scanner.domain.collector

import com.pokerarity.scanner.data.model.VariantIdentityKey
import com.pokerarity.scanner.data.model.VisualFeatures

class CollectionContextBuilder(
    private val lookup: CollectionContextLookup
) {
    suspend fun buildContext(
        variantKey: VariantIdentityKey?,
        features: VisualFeatures?
    ): CollectionContext {
        if (variantKey == null) {
            return CollectionContext()
        }

        val keyStr = variantKey.asStringKey()
        val duplicateCount = lookup.countDuplicates(keyStr)
        val isFirstOfKind = duplicateCount == 0

        var hasSameXXL = false
        var hasSameXXS = false

        if (duplicateCount > 0 && (features?.isXXL == true || features?.isXXS == true)) {
            val entries = lookup.getEntriesByVariantKey(keyStr)
            hasSameXXL = features.isXXL && entries.any { it.isXXL }
            hasSameXXS = features.isXXS && entries.any { it.isXXS }
        }

        return CollectionContext(
            duplicateCountForVariantKey = duplicateCount,
            isFirstOfKind = isFirstOfKind,
            hasSameXXL = hasSameXXL,
            hasSameXXS = hasSameXXS
        )
    }
}
