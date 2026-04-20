package io.github.barsia.speqa.editor.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StepMetaRowLayoutTest {

    @Test
    fun `narrow step metadata uses equal-width single-line add actions`() {
        val contract = stepMetaRowVisualContract(narrow = true)

        assertEquals(24, contract.addActionMinHeightDp)
        assertTrue(contract.equalWidthAddActions)
        assertTrue(contract.singleLineAddActions)
        assertTrue(contract.ellipsisOverflow)
    }

    @Test
    fun `wide step metadata keeps shared add-action height without equal-width narrow rule`() {
        val contract = stepMetaRowVisualContract(narrow = false)

        assertEquals(24, contract.addActionMinHeightDp)
        assertFalse(contract.equalWidthAddActions)
        assertTrue(contract.singleLineAddActions)
        assertTrue(contract.ellipsisOverflow)
    }
}
