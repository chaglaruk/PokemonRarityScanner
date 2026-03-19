package com.pokerarity.scanner

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.pokerarity.scanner.data.model.PokemonData
import com.pokerarity.scanner.service.ScanManager
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Date

@RunWith(AndroidJUnit4::class)
class ScanManagerPolicyTest {

    @Test
    fun skipsDetailedPassForReliableQuickScan() {
        val pokemon = PokemonData(
            cp = 1764,
            hp = null,
            maxHp = null,
            name = "Raichu",
            realName = "Raichu",
            candyName = null,
            megaEnergy = null,
            weight = null,
            height = null,
            gender = null,
            stardust = null,
            arcLevel = 1.0f,
            caughtDate = Date(),
            rawOcrText = "Name:Raichu|NameHC:"
        )

        assertFalse(
            ScanManager.shouldRunDetailedPassForAuthoritative(
                pokemon = pokemon,
                cpQuality = 0.82,
                topTextConfidence = 0.97
            )
        )
    }

    @Test
    fun keepsDetailedPassWhenDateIsMissing() {
        val pokemon = PokemonData(
            cp = 1764,
            hp = null,
            maxHp = null,
            name = "Raichu",
            realName = "Raichu",
            candyName = null,
            megaEnergy = null,
            weight = null,
            height = null,
            gender = null,
            stardust = null,
            arcLevel = 1.0f,
            caughtDate = null,
            rawOcrText = "Name:Raichu|NameHC:"
        )

        assertTrue(
            ScanManager.shouldRunDetailedPassForAuthoritative(
                pokemon = pokemon,
                cpQuality = 0.82,
                topTextConfidence = 0.97
            )
        )
    }

    @Test
    fun keepsDetailedPassWhenNameConfidenceIsWeak() {
        val pokemon = PokemonData(
            cp = 470,
            hp = null,
            maxHp = null,
            name = "Eelektrik",
            realName = "Eelektrik",
            candyName = null,
            megaEnergy = null,
            weight = null,
            height = null,
            gender = null,
            stardust = null,
            arcLevel = 1.0f,
            caughtDate = Date(),
            rawOcrText = "Name:EeveeZQY|NameHC:"
        )

        assertTrue(
            ScanManager.shouldRunDetailedPassForAuthoritative(
                pokemon = pokemon,
                cpQuality = 0.81,
                topTextConfidence = 0.54
            )
        )
    }
}
