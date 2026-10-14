package com.pokerarity.scanner.util.ocr

import android.graphics.Rect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class ActionResourceRoleTest {
    private val anchor = MLKitOcrProvider.RecognizedBlock("EVOLVE", Rect(230, 1520, 400, 1560))
    private fun token(amount: String, left: Int) =
        MLKitOcrProvider.RecognizedBlock(amount, Rect(left, 1520, left + 45, 1560))
    private fun witness(token: MLKitOcrProvider.RecognizedBlock, kind: ActionResourceKind,
        role: ActionResourceRole = ActionResourceRole.COST, clear: Boolean = true) =
        ActionResourceWitness(token.bounds!!, kind, role, clear)
    private fun read(tokens: List<MLKitOcrProvider.RecognizedBlock>, witnesses: List<ActionResourceWitness>) =
        evolveEvidence(MLKitOcrProvider.Layout(listOf(anchor), tokens), listOf(anchor), 1080,
            ExtractionContext(actionResources = witnesses)).cost.read

    @Test
    fun anonymousSingleNumberCannotBecomeCandy() {
        val result = read(listOf(token("20", 790)), emptyList())
        assertNull(result.value)
        assertEquals(FieldReadStatus.VISIBLE_UNREADABLE, result.status)
    }

    @Test
    fun itemAndCandyKeepDistinctRoles() {
        val item = token("20", 590)
        val candy = token("200", 790)
        assertEquals(200, read(listOf(item, candy), listOf(witness(item, ActionResourceKind.SPECIAL_ITEM),
            witness(candy, ActionResourceKind.CANDY))).value)
    }

    @Test
    fun equalNumbersAtDifferentResourceLocationsDoNotCollapseIntoCandy() {
        val item = token("20", 590)
        val coveredCandy = token("20", 790)
        val result = read(listOf(item, coveredCandy), listOf(witness(item, ActionResourceKind.UNKNOWN),
            witness(coveredCandy, ActionResourceKind.CANDY, clear = false)))
        assertNull(result.value)
        assertEquals(FieldReadStatus.VISIBLE_UNREADABLE, result.status)
    }

    @Test
    fun nonCandyResourcesAndInventoryCannotProvideOrdinaryCandyCost() {
        val amount = token("20", 790)
        for (kind in listOf(ActionResourceKind.CANDY_XL, ActionResourceKind.SPECIAL_ITEM,
            ActionResourceKind.MEGA_ENERGY, ActionResourceKind.STARDUST)) {
            assertEquals(FieldReadStatus.UNSUPPORTED, read(listOf(amount), listOf(witness(amount, kind))).status)
        }
        assertNull(read(listOf(amount), listOf(witness(amount, ActionResourceKind.CANDY,
            ActionResourceRole.INVENTORY))).value)
    }

    @Test
    fun conflictingValuesOfTheSameCandyResourceRemainBlocking() {
        val first = token("25", 590)
        val second = token("50", 790)
        assertEquals(FieldReadStatus.CONFLICT, read(listOf(first, second),
            listOf(witness(first, ActionResourceKind.CANDY), witness(second, ActionResourceKind.CANDY))).status)
    }
}
