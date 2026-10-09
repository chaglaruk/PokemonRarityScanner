package com.pokerarity.scanner

import android.content.Context
import android.graphics.Rect
import androidx.test.core.app.ApplicationProvider
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.pokerarity.scanner.util.ocr.AnchoredScreenText
import com.pokerarity.scanner.util.ocr.ExtractionContext
import com.pokerarity.scanner.util.ocr.FieldReadStatus
import com.pokerarity.scanner.util.ocr.MLKitOcrProvider
import com.pokerarity.scanner.util.ocr.ScreenGeometryBuilder
import com.pokerarity.scanner.util.ocr.TextParser
import com.pokerarity.scanner.util.ocr.acceptedSpeciesOrNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.ConscryptMode
import java.io.File
import java.util.Locale

@RunWith(RobolectricTestRunner::class)
@ConscryptMode(ConscryptMode.Mode.OFF)
class AnchoredScreenTextTest {
    // Match the species-decision tests: Robolectric's asset fallback has only a
    // handful of species, so inject the actual repository canonical-name asset.
    private val parser = TextParser(ApplicationProvider.getApplicationContext<Context>()).also { parser ->
        val names = speciesFile().reader().use { reader ->
            Gson().fromJson<List<String>>(reader, object : TypeToken<List<String>>() {}.type)
        }
        TextParser::class.java.getDeclaredField("pokemonNames").apply { isAccessible = true }
            .set(parser, names.map { it.lowercase(Locale.ROOT) })
    }

    @Test
    fun ordinaryDetailExtractsSpatiallyRelatedFields() {
        val result = extract(detailLines(), listOf(cost("1,000")))

        assertEquals("Eevee", result.name?.acceptedSpeciesOrNull())
        assertEquals(424, result.cp)
        assertEquals(80 to 80, result.hp)
        assertEquals("Eevee", result.candy)
        assertEquals(1000, result.powerUpCost)
        assertEquals(setOf("normal"), result.types)
        assertTrue(result.detailScreen)
    }

    @Test
    fun inventoryStardustNeverReplacesThePowerUpRowCost() {
        val inventory = block("200", 610, 1070, 710, 1110)
        val result = extract(detailLines() + block("STARDUST", 560, 1120, 760, 1150),
            listOf(inventory, cost("1,000")))

        assertEquals(1000, result.powerUpCost)
        assertEquals(cost("1,000").bounds, result.costRect)
    }

    @Test
    fun inventoryValueCannotFillAnUnreadablePowerUpCost() {
        val result = extract(detailLines(), listOf(block("1,000", 610, 1070, 740, 1110)))

        assertNull(result.powerUpCost)
    }

    @Test
    fun conflictingNumbersOnThePowerUpRowRemainUncertain() {
        val result = extract(detailLines(), listOf(cost("1,000", left = 530), cost("2,000", left = 700)))

        assertNull(result.powerUpCost)
    }

    @Test
    fun conflictingPowerUpRowsCannotSelectAnArbitraryCost() {
        val secondButton = block("POWER UP", 130, 1520, 410, 1570)
        val result = extract(detailLines() + secondButton,
            listOf(cost("1,000"), cost("4,000", top = 1520)))

        assertNull(result.powerUpCost)
    }

    @Test
    fun faintedPokemonRetainsItsObservedMaximumHp() {
        val result = extract(detailLines(hp = "0 / 80 HP"))

        assertEquals(0 to 80, result.hp)
        assertTrue(result.detailScreen)
    }

    @Test
    fun distinctHpLinesDoNotEstablishDetailEvidence() {
        val result = extract(detailLines() + block("90 / 90 HP", 400, 850, 680, 890))

        assertNull(result.hp)
        assertFalse(result.detailScreen)
    }

    @Test
    fun multipleHpPairsWithinOneOcrLineRemainAmbiguous() {
        val result = extract(detailLines(hp = "80 / 80 HP 90 / 90 HP"))

        assertNull(result.hp)
        assertFalse(result.detailScreen)
    }

    @Test
    fun malformedHpCannotBecomeAuthorityThroughDigitRepair() {
        for (text in listOf("7227 / 227 HP", "80 / 8000 HP", "81 / 80 HP", "80 / 8 HP")) {
            val result = extract(detailLines(hp = text))

            assertNull(text, result.hp)
            assertFalse(text, result.detailScreen)
        }
    }

    @Test
    fun duplicateMatchingHpReadingsDoNotCreateAConflict() {
        val result = extract(detailLines() + block("80 / 80 HP", 400, 791, 680, 831))

        assertEquals(80 to 80, result.hp)
    }

    @Test
    fun repeatedCandyAndCandyXlLabelsAgreeOnOneFamily() {
        val result = extract(detailLines() + block("EEVEE CANDY XL", 720, 1140, 1040, 1180))

        assertEquals("Eevee", result.candy)
    }

    @Test
    fun twoDifferentCandyFamiliesRemainUncertain() {
        val result = extract(detailLines() + block("PIKACHU CANDY XL", 700, 1140, 1060, 1180))

        assertNull(result.candy)
        assertFalse(result.detailScreen)
    }

    @Test
    fun verticallySplitCandyLabelUsesItsAlignedSpeciesLine() {
        val lines = detailLines(candy = null) + listOf(
            block("PIKACHU", 120, 1100, 320, 1135),
            block("EEVEE", 580, 1100, 790, 1135),
            block("CANDY", 580, 1140, 790, 1180)
        )

        assertEquals("Eevee", extract(lines).candy)
    }

    @Test
    fun pencilSuffixCleanupIsConfinedToTheTitle() {
        val result = extract(detailLines(title = "Eevee /", candy = "Eevee/ CANDY"))

        assertEquals("Eevee", result.name?.acceptedSpeciesOrNull())
        assertEquals("Eevee /", result.nameRaw)
        assertNull(result.candy)
        assertNull(parser.decideSpeciesName("Eevee /").acceptedSpeciesOrNull())
    }

    @Test
    fun unrelatedStandaloneTypeBelowCandyDoesNotBecomeSpeciesEvidence() {
        val lines = detailLines(type = null) + block("WATER", 430, 1850, 650, 1890)

        assertNull(extract(lines).types)
    }

    @Test
    fun unrelatedMoveTypeCannotOverrideTheAnchoredTypeRow() {
        val result = extract(detailLines() + block("WATER", 430, 1850, 650, 1890))

        assertEquals(setOf("normal"), result.types)
    }

    @Test
    fun incompleteDualTypeLabelCannotEliminateTheTrueSpecies() {
        for (type in listOf("NORMAL /", "/ FLYING", "NORMAL / ?", "NORMAL NORMAL")) {
            assertNull(type, extract(detailLines(type = type)).types)
        }
        assertEquals(setOf("normal", "flying"), extract(detailLines(type = "NORMAL / FLYING")).types)
    }

    @Test
    fun scrolledDetailCanUseTheTypeAndMeasurementsRowWithoutInventingHp() {
        val lines = listOf(
            block("FIRE", 450, 210, 630, 250),
            block("WEIGHT", 100, 265, 290, 300),
            block("HEIGHT", 790, 265, 980, 300),
            block("TORCHIC CANDY", 490, 420, 800, 460),
            block("POWER UP", 130, 620, 410, 670)
        )
        val result = extract(lines, listOf(cost("2,500", top = 620)), bar = null)

        assertNull(result.hp)
        assertNull(result.cp)
        assertEquals(setOf("fire"), result.types)
        assertEquals("Torchic", result.candy)
        assertTrue(result.detailScreen)
    }

    @Test
    fun unrelatedScreenWithPokemonAndCandyWordsIsNotADetailScreen() {
        val lines = listOf(
            block("EEVEE", 300, 160, 780, 230),
            block("NORMAL", 430, 270, 650, 310),
            block("EEVEE CANDY", 440, 500, 800, 540),
            block("POWER UP", 130, 800, 410, 850)
        )
        val result = extract(lines, bar = null)

        assertFalse(result.detailScreen)
        assertNull(result.types)
    }

    @Test
    fun evolutionCostRequiresItsOwnAnchoredOrdinaryActionRow() {
        val evolve = block("EVOLVE", 230, 1520, 400, 1560)
        val candyCost = block("50", 790, 1520, 835, 1560)
        assertEquals(50, extract(detailLines() + evolve, listOf(candyCost)).evolutionCandyCost)
        assertNull(extract(detailLines() + evolve, listOf(candyCost.copy(text = "50O"))).evolutionCandyCost)
        assertNull(extract(detailLines() + evolve, listOf(candyCost, candyCost.copy(text = "100"))).evolutionCandyCost)
        assertNull(extract(detailLines() + evolve.copy(text = "MEGA EVOLVE"), listOf(candyCost)).evolutionCandyCost)
        assertNull(extract(detailLines(title = "EVOLVE"), listOf(candyCost)).evolutionCandyCost)
        assertNull(
            extract(
                detailLines() + evolve.copy(text = "Adventure together to evolve"),
                listOf(candyCost)
            ).evolutionCandyCost
        )
    }

    @Test
    fun evolveIsAnchoredIndependentlyOfThePowerUpAction() {
        // Phase 2C: a split/unreadable POWER UP label must not hide a visible EVOLVE row.
        val evolve = block("EVOLVE", 230, 1520, 400, 1560)
        val candyCost = block("50", 790, 1520, 835, 1560)
        val result = extract(
            detailLines().filterNot { it.text == "POWER UP" } + evolve,
            listOf(candyCost)
        )

        assertEquals(50, result.evolutionCandyCost)
        assertEquals(FieldReadStatus.READ, result.evolveRead.status)
        assertEquals(FieldReadStatus.MISSING_NOT_VISIBLE, result.powerUpRead.status)
        assertNull(result.powerUpCost)
    }

    @Test
    fun mergedEvolveLineDoesNotInventAnUnobservedResourceRole() {
        val merged = block("EVOLVE 25", 230, 1520, 430, 1560)
        val result = extract(detailLines() + merged, listOf(block("X", 700, 1520, 730, 1560)))

        assertNull(result.evolutionCandyCost)
        assertEquals(FieldReadStatus.VISIBLE_UNREADABLE, result.evolveRead.status)
    }

    @Test
    fun evolveVisibleWithoutReadableCostIsVisibleUnreadable() {
        val evolve = block("EVOLVE", 230, 1520, 400, 1560)
        val result = extract(detailLines() + evolve, listOf(block("1O0", 790, 1520, 835, 1560)))

        assertNull(result.evolutionCandyCost)
        assertEquals(FieldReadStatus.VISIBLE_UNREADABLE, result.evolveRead.status)
        assertEquals("cost_token_unreadable", result.evolveRead.reasonCode)
    }

    @Test
    fun evolveCostOutsideTheSupportedDomainIsVisibleUnreadable() {
        val evolve = block("EVOLVE", 230, 1520, 400, 1560)
        val result = extract(detailLines() + evolve, listOf(block("50000", 790, 1520, 835, 1560)))

        assertNull(result.evolutionCandyCost)
        assertEquals(FieldReadStatus.VISIBLE_UNREADABLE, result.evolveRead.status)
        assertEquals("cost_out_of_supported_domain", result.evolveRead.reasonCode)
    }

    @Test
    fun titleBandMegaEvolveIsNotAnActionMechanic() {
        // "MEGA EVOLVE" inside the title/name band is text, not an action-row mechanic:
        // the mega detection carries the same geometry constraints as the ordinary anchor.
        val geometry = ExtractionContext(
            bar = defaultBar(),
            nameBand = requireNotNull(ScreenGeometryBuilder.deriveNameBand(defaultBar(), 1080, 2340)),
            detailCardTop = 650
        )
        val titleMega = block("MEGA EVOLVE", 140, 660, 340, 720)
        val result = extract(
            detailLines(title = "MEGA EVOLVE") + titleMega,
            listOf(block("50", 790, 1520, 835, 1560)),
            geometry = geometry
        )

        assertNull(result.evolutionCandyCost)
        assertEquals(FieldReadStatus.MISSING_NOT_VISIBLE, result.evolveRead.status)
    }

    @Test
    fun megaEvolveIsUnsupportedNotMissing() {
        val mega = block("MEGA EVOLVE", 230, 1520, 430, 1560)
        val result = extract(detailLines() + mega, listOf(block("50", 790, 1520, 835, 1560)))

        assertNull(result.evolutionCandyCost)
        assertEquals(FieldReadStatus.UNSUPPORTED, result.evolveRead.status)
    }

    @Test
    fun evolveAbsentIsMissingNotVisible() {
        val result = extract(detailLines(), listOf(block("50", 790, 1520, 835, 1560)))

        assertNull(result.evolutionCandyCost)
        assertEquals(FieldReadStatus.MISSING_NOT_VISIBLE, result.evolveRead.status)
    }

    @Test
    fun multipleDistinctEvolveCostsReportConflict() {
        val evolve = block("EVOLVE", 230, 1520, 400, 1560)
        val result = extract(
            detailLines() + evolve,
            listOf(block("50", 790, 1520, 835, 1560), block("100", 640, 1520, 680, 1560))
        )

        assertNull(result.evolutionCandyCost)
        assertEquals(FieldReadStatus.CONFLICT, result.evolveRead.status)
    }

    @Test
    fun leftPositionedEvolveTextInsideTheNameBandIsNotTheAction() {
        // A title/nickname "EVOLVE" on the left is rejected by the geometry name-band guard.
        val geometry = ExtractionContext(
            bar = defaultBar(),
            nameBand = requireNotNull(ScreenGeometryBuilder.deriveNameBand(defaultBar(), 1080, 2340)),
            detailCardTop = 650
        )
        val nickname = block("EVOLVE", 140, 660, 300, 720)
        val result = extract(
            detailLines(title = "EVOLVE") + nickname,
            emptyList(),
            geometry = geometry
        )

        assertNull(result.evolutionCandyCost)
        assertEquals(FieldReadStatus.MISSING_NOT_VISIBLE, result.evolveRead.status)
    }

    @Test
    fun shiftedPowerUpCostWithinTheButtonSpanIsRead() {
        // Label and cost both shifted left (mid-swipe style): the label-anchored span
        // rule replaces the historical absolute left window.
        val shiftedLabel = block("POWER UP", 40, 1370, 300, 1420)
        val shiftedCost = block("2,200", 350, 1370, 470, 1420)
        val result = extract(
            detailLines().filterNot { it.text == "POWER UP" } + shiftedLabel,
            listOf(shiftedCost)
        )

        assertEquals(2200, result.powerUpCost)
        assertEquals(FieldReadStatus.READ, result.powerUpRead.status)
    }

    @Test
    fun inventoryColumnOnThePowerUpRowIsExcludedByTheAbsoluteGuard() {
        // Candy-XL count sits at x >= 0.80 on the same row; the absolute left limit keeps
        // it out even when it is large enough to pass the numeric domain.
        val inventoryCount = block("1,200", 900, 1370, 1020, 1420)
        val result = extract(detailLines(), listOf(cost("1,000"), inventoryCount))

        assertEquals(1000, result.powerUpCost)
        assertEquals(FieldReadStatus.READ, result.powerUpRead.status)
    }

    @Test
    fun numberBeyondTheButtonSpanNeverBecomesCost() {
        val beyond = block("5,000", 860, 1370, 990, 1420)
        val result = extract(detailLines(), listOf(beyond))

        assertNull(result.powerUpCost)
        assertEquals(FieldReadStatus.VISIBLE_UNREADABLE, result.powerUpRead.status)
    }

    @Test
    fun twoDifferentCandyFamiliesReportConflictStatus() {
        val result = extract(
            detailLines() + block("SEEDOT CANDY", 490, 1200, 800, 1240),
            listOf(cost("1,000"))
        )

        assertNull(result.candy)
        assertEquals(FieldReadStatus.CONFLICT, result.candyRead.status)
    }

    @Test
    fun partiallyCoveredCandyLabelIsVisibleUnreadable() {
        val result = extract(
            detailLines(candy = null) + block("ANDY", 900, 1140, 1010, 1180),
            emptyList()
        )

        assertNull(result.candy)
        assertEquals(FieldReadStatus.VISIBLE_UNREADABLE, result.candyRead.status)
    }

    @Test
    fun absentCandyLabelIsMissingNotVisible() {
        val result = extract(detailLines(candy = null), emptyList())

        assertNull(result.candy)
        assertEquals(FieldReadStatus.MISSING_NOT_VISIBLE, result.candyRead.status)
    }

    @Test
    fun cpStructuredReadCoversTheLowDomain() {
        for (cp in listOf(10, 25, 99, 100)) {
            val lines = detailLines().map {
                if (it.text.startsWith("CP ")) block("CP $cp", 430, 210, 650, 260) else it
            }
            val result = extract(lines, emptyList())
            assertEquals(cp, result.cp)
            assertEquals(FieldReadStatus.READ, result.cpRead.status)
        }
    }

    @Test
    fun conflictingHpPairsReportConflictStatus() {
        val result = extract(
            detailLines(hp = "80 / 80 HP") + block("60 / 60 HP", 400, 790, 680, 830),
            emptyList()
        )

        assertNull(result.hp)
        assertEquals(FieldReadStatus.CONFLICT, result.hpRead.status)
    }

    private fun defaultBar() = Rect(310, 745, 770, 758)

    private fun extract(
        lines: List<MLKitOcrProvider.RecognizedBlock>,
        elements: List<MLKitOcrProvider.RecognizedBlock> = emptyList(),
        bar: Rect? = defaultBar(),
        geometry: ExtractionContext? = null
    ) = AnchoredScreenText.extract(
        MLKitOcrProvider.Layout(lines, elements),
        parser,
        1080,
        2340,
        geometry ?: ExtractionContext(bar = bar, actionResources = syntheticCandyWitnesses(lines, elements))
    )

    // These constructed ordinary-EVOLVE fixtures explicitly define candy glyphs.
    // Untyped, item, XL, inventory and obscured witnesses are tested separately.
    private fun syntheticCandyWitnesses(
        lines: List<MLKitOcrProvider.RecognizedBlock>,
        elements: List<MLKitOcrProvider.RecognizedBlock>
    ) = elements.filter { element -> lines.any { line ->
        line.text == "EVOLVE" && element.bounds?.centerY() == line.bounds?.centerY()
    }}.map { element -> com.pokerarity.scanner.util.ocr.ActionResourceWitness(
        element.bounds!!, com.pokerarity.scanner.util.ocr.ActionResourceKind.CANDY,
        com.pokerarity.scanner.util.ocr.ActionResourceRole.COST, true)
    }.distinctBy { it.bounds }

    private fun detailLines(
        title: String = "Eevee",
        hp: String = "80 / 80 HP",
        candy: String? = "EEVEE CANDY",
        type: String? = "NORMAL"
    ) = listOfNotNull(
        block("CP 424", 430, 210, 650, 260),
        block(title, 320, 660, 760, 725),
        block(hp, 400, 790, 680, 830),
        type?.let { block(it, 435, 935, 645, 965) },
        block("WEIGHT", 100, 985, 290, 1020),
        block("HEIGHT", 790, 985, 980, 1020),
        candy?.let { block(it, 490, 1140, 800, 1180) },
        block("POWER UP", 130, 1370, 410, 1420)
    )

    private fun cost(value: String, left: Int = 590, top: Int = 1370) = block(value, left, top, left + 130, top + 50)

    private fun block(text: String, left: Int, top: Int, right: Int, bottom: Int) =
        MLKitOcrProvider.RecognizedBlock(text, Rect(left, top, right, bottom))

    private fun speciesFile(): File {
        var directory = File(System.getProperty("user.dir") ?: ".").absoluteFile
        repeat(6) {
            File(directory, "app/src/main/assets/data/pokemon_names.json").takeIf(File::isFile)?.let { return it }
            directory = directory.parentFile ?: return@repeat
        }
        error("pokemon_names.json not found")
    }
}
