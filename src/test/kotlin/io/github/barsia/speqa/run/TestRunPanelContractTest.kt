package io.github.barsia.speqa.run

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Test

class TestRunPanelContractTest {

    @Test
    fun `run step content stays editable after creation`() {
        val contract = runStepEditabilityContract()

        assertTrue(contract.actionEditable)
        assertTrue(contract.expectedEditable)
        assertTrue(contract.bodyBlocksEditable)
    }

    @Test
    fun `expected enter moves to next action when another step exists`() {
        assertEquals(
            RunExpectedEnterTarget.NEXT_STEP_ACTION,
            runExpectedEnterTarget(hasNextStep = true),
        )
    }

    @Test
    fun `expected enter falls through to run comment on last step`() {
        assertEquals(
            RunExpectedEnterTarget.RUN_COMMENT,
            runExpectedEnterTarget(hasNextStep = false),
        )
    }

    @Test
    fun `run sticky trail reuses shared focus trail behavior`() {
        val contract = runHeaderLayoutContract()

        assertTrue(contract.usesSharedStickyTrail)
    }

    @Test
    fun `run sticky trail progress counts completed steps`() {
        assertEquals(
            "Progress: 2/3",
            runStickyProgressLabel(completedSteps = 2, totalSteps = 3),
        )
    }

    @Test
    fun `run panel consumes wheel scroll like test case preview`() {
        val contract = runScrollConsumptionContract()

        assertTrue(contract.consumeScrollAtViewportBoundary)
    }

    @Test
    fun `step comment block uses looser spacing than compact item gap`() {
        val contract = runStepCommentLayoutContract()

        assertTrue(contract.usesRelaxedLabelGap)
    }

    @Test
    fun `run header keeps runner inline with progress and result`() {
        val contract = runHeaderLayoutContract()

        assertTrue(contract.runnerInlineWithProgress)
        assertTrue(contract.environmentTagsTwoColumns)
        assertTrue(contract.referencesMatchTestCaseHeaderPattern)
        assertTrue(contract.titleUsesInlineEditablePattern)
        assertTrue(contract.metadataUsesSharedSuggestions)
        assertTrue(contract.tagsUseTestCaseEmptyState)
        assertTrue(contract.headerPairsShareColumnGrid)
        assertTrue(contract.usesSharedStickyTrail)
    }

    @Test
    fun `empty run tags use compact add affordance instead of standalone strip`() {
        val contract = runHeaderTagsLayoutContract(tags = emptyList())

        assertFalse(contract.showStandaloneTagStrip)
        assertTrue(contract.showCompactAddAffordance)
    }

    @Test
    fun `existing run tags keep metadata row without orphan divider state`() {
        val contract = runHeaderTagsLayoutContract(tags = listOf("smoke"))

        assertFalse(contract.showStandaloneTagStrip)
        assertTrue(contract.showCompactAddAffordance)
    }

    @Test
    fun `top level run references use icon only add affordances`() {
        val contract = runTopLevelReferencesVisualContract()

        assertTrue(contract.iconOnlyAddAffordances)
    }
}
