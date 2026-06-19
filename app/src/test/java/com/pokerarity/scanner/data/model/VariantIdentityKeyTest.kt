package com.pokerarity.scanner.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class VariantIdentityKeyTest {

    @Test
    fun testBaseIdentityIsConsistent() {
        val key1 = VariantIdentityKey(
            dex = 25,
            formId = null,
            variantId = null,
            isShiny = false,
            isShadow = false,
            isPurified = false,
            isLucky = false,
            isCostume = false,
            backgroundType = null,
            backgroundLabel = null
        )
        val key2 = key1.copy()

        assertEquals("25-BASE-NONE-NORMAL", key1.asStringKey())
        assertEquals(key1.asStringKey(), key2.asStringKey())
    }

    @Test
    fun testFlagsProduceDifferentKeys() {
        val base = VariantIdentityKey(25, null, null, false, false, false, false, false, null, null)
        
        val shiny = base.copy(isShiny = true)
        val shadow = base.copy(isShadow = true)
        val purified = base.copy(isPurified = true)
        val lucky = base.copy(isLucky = true)
        val costume = base.copy(isCostume = true)
        val shinyShadow = base.copy(isShiny = true, isShadow = true)

        assertNotEquals(base.asStringKey(), shiny.asStringKey())
        assertNotEquals(base.asStringKey(), shadow.asStringKey())
        assertNotEquals(base.asStringKey(), purified.asStringKey())
        assertNotEquals(base.asStringKey(), lucky.asStringKey())
        assertNotEquals(base.asStringKey(), costume.asStringKey())
        assertNotEquals(shiny.asStringKey(), shinyShadow.asStringKey())

        assertEquals("25-BASE-NONE-S", shiny.asStringKey())
        assertEquals("25-BASE-NONE-SH", shinyShadow.asStringKey())
    }

    @Test
    fun testSpecialBackgroundsProduceDifferentKeys() {
        val base = VariantIdentityKey(25, null, null, false, false, false, false, false, null, null)
        
        val specialBg = base.copy(backgroundType = "SPECIAL", backgroundLabel = "GlobalFest")
        val locationBg = base.copy(backgroundType = "LOCATION", backgroundLabel = "NewYork")

        assertNotEquals(base.asStringKey(), specialBg.asStringKey())
        assertNotEquals(base.asStringKey(), locationBg.asStringKey())
        assertNotEquals(specialBg.asStringKey(), locationBg.asStringKey())

        assertEquals("25-BASE-NONE-NORMAL-[BG:SPECIAL_GlobalFest]", specialBg.asStringKey())
    }

    @Test
    fun testFormAndVariantProduceDifferentKeys() {
        val base = VariantIdentityKey(150, null, null, false, false, false, false, false, null, null)
        val armored = base.copy(formId = "ARMORED")
        val clone = base.copy(variantId = "CLONE")
        
        assertNotEquals(base.asStringKey(), armored.asStringKey())
        assertNotEquals(base.asStringKey(), clone.asStringKey())
        assertEquals("150-ARMORED-NONE-NORMAL", armored.asStringKey())
        assertEquals("150-BASE-CLONE-NORMAL", clone.asStringKey())
    }

    @Test
    fun testXxlAndXxsDoNotAffectIdentityKey() {
        // Since XXL/XXS are tracked as separate collection dimensions in CollectionEntryEntity,
        // they should not alter the core VariantIdentityKey.
        val base = VariantIdentityKey(143, null, null, false, false, false, false, false, null, null)
        
        // Identity key doesn't even take XXL/XXS as constructor arguments by design,
        // so it intrinsically cannot produce a different key. We just assert the design intention.
        val baseKey = base.asStringKey()
        assertEquals("143-BASE-NONE-NORMAL", baseKey)
    }
}
