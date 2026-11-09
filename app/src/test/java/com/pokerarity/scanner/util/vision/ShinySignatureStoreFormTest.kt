package com.pokerarity.scanner.util.vision

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class ShinySignatureStoreFormTest {
    private fun entry(species: String, key: String, formId: String) = ShinySignatureStore.ShinyEntry(
        species = species,
        key = key,
        normal = ShinySignatureStore.SignatureRef("", "", floatArrayOf(), floatArrayOf()),
        shiny = ShinySignatureStore.SignatureRef("", "", floatArrayOf(), floatArrayOf()),
        formId = formId
    )

    private val references = listOf(
        entry("Shellos", "422_11", "11"),
        entry("Shellos", "422_12", "12"),
        entry("Smoliv", "928_00", "00")
    )

    @Test
    fun groupingRetainsEveryFormForOneSpecies() {
        val grouped = ShinySignatureStore.groupEntries(references)
        val shellos = grouped.getValue("shellos")

        assertEquals(listOf("422_11", "422_12"), shellos.map { it.key })
        assertNull(ShinySignatureStore.selectEntry(shellos))
        assertTrue(ShinySignatureStore.unresolvedForms(shellos))
    }

    @Test
    fun verifiedExactFormSelectsOnlyItsOwnPair() {
        val shellos = ShinySignatureStore.groupEntries(references).getValue("shellos")

        assertEquals("422_11", ShinySignatureStore.selectEntry(shellos, "11")?.key)
        assertEquals("422_12", ShinySignatureStore.selectEntry(shellos, "12")?.key)
        assertFalse(ShinySignatureStore.unresolvedForms(shellos, "11"))
        assertFalse(ShinySignatureStore.unresolvedForms(shellos, "12"))
        assertNull(ShinySignatureStore.selectEntry(shellos, "13"))
        assertTrue(ShinySignatureStore.unresolvedForms(shellos, "13"))
        // A display label is not a source-form crosswalk.
        assertNull(ShinySignatureStore.selectEntry(shellos, "East Sea"))
    }

    @Test
    fun duplicateEntriesForOneFormStayAmbiguousEvenWithThatFormId() {
        val first = entry("Shellos", "422_11", "11")
        val duplicate = first.copy(key = "422_11_alternate")
        val entries = listOf(first, duplicate)

        assertNull(ShinySignatureStore.selectEntry(entries, "11"))
        assertTrue(ShinySignatureStore.unresolvedForms(entries, "11"))
    }

    @Test
    fun singletonReferenceWorksWithoutFormAndRejectsMismatchedForm() {
        val smoliv = ShinySignatureStore.groupEntries(references).getValue("smoliv")
        val entry = smoliv.single()

        assertSame(entry, ShinySignatureStore.selectEntry(smoliv))
        assertSame(entry, ShinySignatureStore.selectEntry(smoliv, "00"))
        assertFalse(ShinySignatureStore.unresolvedForms(smoliv))
        assertNull(ShinySignatureStore.selectEntry(smoliv, "11"))
        assertTrue(ShinySignatureStore.unresolvedForms(smoliv, "11"))
        assertFalse(ShinySignatureStore.unresolvedForms(emptyList()))
    }
}
