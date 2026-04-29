package com.pokerarity.scanner

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.pokerarity.scanner.data.model.PokemonData
import com.pokerarity.scanner.data.repository.RarityCalculator
import com.pokerarity.scanner.util.ocr.SpeciesRefiner
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SpeciesRefinerTest {

    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val refiner = SpeciesRefiner(context, RarityCalculator(context))

    @Test
    fun exactParsedSpeciesDoesNotDriftWithinFamilyWithoutContradictorySignals() {
        val pokemon = PokemonData(
            cp = 381,
            hp = null,
            maxHp = null,
            name = "Squirtle",
            realName = "Squirtle",
            candyName = null,
            megaEnergy = null,
            weight = null,
            height = null,
            gender = null,
            stardust = null,
            arcLevel = 1.0f,
            caughtDate = null,
            rawOcrText = "Name:sauirtlefs|NameHC:|Bottom:"
        )

        val refined = refiner.refine(pokemon)

        assertEquals("Squirtle", refined.name)
        assertEquals("Squirtle", refined.realName)
    }

    @Test
    fun exactParsedSpeciesDoesNotDriftToEvolutionWhenCandyIsBaseSpecies() {
        val pokemon = PokemonData(
            cp = 543,
            hp = 116,
            maxHp = 116,
            name = "Slowpoke",
            realName = "Slowpoke",
            candyName = "Slowpoke",
            megaEnergy = null,
            weight = null,
            height = null,
            gender = null,
            stardust = null,
            arcLevel = 1.0f,
            caughtDate = null,
            rawOcrText = "Name:Slowpoke100|NameHC:|Bottom:|CandyBlock:SLOWPOKE CANDY"
        )

        val refined = refiner.refine(pokemon)

        assertEquals("Slowpoke", refined.name)
        assertEquals("Slowpoke", refined.realName)
    }

    @Test
    fun candyFamilyAuthorityBlocksCrossFamilyRefine() {
        val pokemon = PokemonData(
            cp = 597,
            hp = 90,
            maxHp = 90,
            name = "Mankey",
            realName = "Mankey",
            candyName = "Squirtle",
            megaEnergy = null,
            weight = 13.27f,
            height = 0.56f,
            gender = null,
            stardust = null,
            arcLevel = 1.0f,
            caughtDate = null,
            rawOcrText = "Name:SauirtIeSb|NameHC:SaukHeS|Bottom:|CandyBlock:MST SQUIRTLE CANDY"
        )

        val refined = refiner.refine(pokemon)

        assertEquals("Squirtle", refined.name)
        assertEquals("Squirtle", refined.realName)
    }
}
