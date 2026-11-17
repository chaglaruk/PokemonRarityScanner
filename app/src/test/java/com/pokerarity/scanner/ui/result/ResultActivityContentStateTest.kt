package com.pokerarity.scanner.ui.result

import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ResultActivityContentStateTest {
    @Test
    fun updateReplacesThePreviouslyDisplayedHistoryResult() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val initial = Intent(context, ResultActivity::class.java)
            .putExtra(ResultActivity.EXTRA_POKEMON_NAME, "Xerneas")
            .putExtra(ResultActivity.EXTRA_CP, 2122)
        val replacement = Intent(context, ResultActivity::class.java)
            .putExtra(ResultActivity.EXTRA_POKEMON_NAME, "Azumarill")
            .putExtra(ResultActivity.EXTRA_CP, 1669)
            .putExtra(ResultActivity.EXTRA_HP, 194)

        val state = ResultActivityContentState(initial)
        state.update(replacement)

        assertEquals("Azumarill", state.pokemon.value.name)
        assertEquals(1669, state.pokemon.value.cp)
        assertEquals(194, state.pokemon.value.hp)
    }
}
