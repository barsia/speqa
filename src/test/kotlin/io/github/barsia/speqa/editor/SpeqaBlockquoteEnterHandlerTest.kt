package io.github.barsia.speqa.editor

import com.intellij.testFramework.fixtures.BasePlatformTestCase

class SpeqaBlockquoteEnterHandlerTest : BasePlatformTestCase() {

    fun `test continues blockquote with proper indent in realistic tc_md layout with neighbors`() {
        // Reproduces the exact scenario the user reported. The platform's
        // default Markdown handler drops the indent in this layout, producing
        // `> ` at column 0; our handler must take over and insert `   > `.
        myFixture.configureByText(
            "case.tc.md",
            """
                |---
                |id: 1
                |title: Search
                |---
                |
                |Scenario:
                |
                |1. Open the page
                |   > Page loads
                |
                |2. Click in the search field
                |   > List of users appears<caret>
                |
                |3. Type a query
                |   > Filtered list shows
            """.trimMargin(),
        )

        myFixture.type('\n')

        myFixture.checkResult(
            """
                |---
                |id: 1
                |title: Search
                |---
                |
                |Scenario:
                |
                |1. Open the page
                |   > Page loads
                |
                |2. Click in the search field
                |   > List of users appears
                |   > <caret>
                |
                |3. Type a query
                |   > Filtered list shows
            """.trimMargin(),
        )
    }

    fun `test continues blockquote with 4-space indent for a two-digit step`() {
        myFixture.configureByText(
            "case.tc.md",
            """
                |10. Click
                |    > Result<caret>
            """.trimMargin(),
        )

        myFixture.type('\n')

        myFixture.checkResult(
            """
                |10. Click
                |    > Result
                |    > <caret>
            """.trimMargin(),
        )
    }

    fun `test second Enter on empty blockquote line exits expected and parks caret at column 0`() {
        myFixture.configureByText(
            "case.tc.md",
            """
                |10. Click
                |    > <caret>
            """.trimMargin(),
        )

        myFixture.type('\n')

        myFixture.checkResult(
            """
                |10. Click
                |<caret>
            """.trimMargin(),
        )
    }

    fun `test continues blockquote inside tr_md step too`() {
        myFixture.configureByText(
            "case.tr.md",
            """
                |1. Click
                |   > Done<caret>
            """.trimMargin(),
        )

        myFixture.type('\n')

        myFixture.checkResult(
            """
                |1. Click
                |   > Done
                |   > <caret>
            """.trimMargin(),
        )
    }

    fun `test Enter on inline sub-step inserts the next number with parent-aligned indent`() {
        myFixture.configureByText(
            "case.tc.md",
            """
                |2. 1. Click in the search field<caret>
                |   > List of users appears
            """.trimMargin(),
        )

        myFixture.type('\n')

        myFixture.checkResult(
            """
                |2. 1. Click in the search field
                |   2. <caret>
                |   > List of users appears
            """.trimMargin(),
        )
    }

    fun `test Enter on empty indented sub-step pivots into a blockquote prefix`() {
        myFixture.configureByText(
            "case.tc.md",
            """
                |2. 1. Click in the search field
                |   2. <caret>
                |   > List of users appears
            """.trimMargin(),
        )

        myFixture.type('\n')

        myFixture.checkResult(
            """
                |2. 1. Click in the search field
                |   > <caret>
                |   > List of users appears
            """.trimMargin(),
        )
    }

    fun `test Enter continues numbered list inside blockquote`() {
        myFixture.configureByText(
            "case.tc.md",
            """
                |5. 1. Grant AI access
                |   2. Log in
                |   3. Use AI in IDE
                |   > 1. AI is available and responds
                |   > 2. Credits are spent<caret>
            """.trimMargin(),
        )

        myFixture.type('\n')

        myFixture.checkResult(
            """
                |5. 1. Grant AI access
                |   2. Log in
                |   3. Use AI in IDE
                |   > 1. AI is available and responds
                |   > 2. Credits are spent
                |   > 3. <caret>
            """.trimMargin(),
        )
    }

    fun `test Enter on empty numbered item in blockquote removes number keeps marker`() {
        myFixture.configureByText(
            "case.tc.md",
            """
                |5. Use AI in IDE
                |   > 3. <caret>
            """.trimMargin(),
        )

        myFixture.type('\n')

        myFixture.checkResult(
            """
                |5. Use AI in IDE
                |   > <caret>
            """.trimMargin(),
        )
    }

    fun `test plain md file is left to the platform`() {
        // In plain `.md` we must not touch input. We assert via the negative:
        // the empty blockquote line should NOT be erased by our handler.
        myFixture.configureByText(
            "notes.md",
            """
                |1. Click
                |   > <caret>
            """.trimMargin(),
        )

        myFixture.type('\n')

        val text = myFixture.editor.document.text
        assertTrue(
            "Plain .md should not have its `   > ` line erased by our handler. Got:\n$text",
            text.contains("   > "),
        )
    }
}
