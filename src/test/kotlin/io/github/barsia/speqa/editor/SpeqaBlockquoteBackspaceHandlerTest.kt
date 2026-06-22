package io.github.barsia.speqa.editor

import com.intellij.testFramework.fixtures.BasePlatformTestCase

class SpeqaBlockquoteBackspaceHandlerTest : BasePlatformTestCase() {

    fun `test Backspace at start of expected content removes the full blockquote prefix`() {
        myFixture.configureByText(
            "case.tc.md",
            """
                |5. 1. Grant AI access
                |   2. Log in as test user
                |   3. Use AI in IDE
                |   > <caret>1. AI is available and responds
                |   > 2. Credits are spent
            """.trimMargin(),
        )

        myFixture.type('\b')

        myFixture.checkResult(
            """
                |5. 1. Grant AI access
                |   2. Log in as test user
                |   3. Use AI in IDE
                |   <caret>1. AI is available and responds
                |   > 2. Credits are spent
            """.trimMargin(),
        )
    }

    fun `test Backspace at start of expected content after a fenced code block`() {
        // Reproduces the exact file structure from line 66 of the user's tc.md:
        // step 5 sub-step 2 has a fenced code block, and the blockquote expected
        // result follows after sub-step 3. Without the code block, the handler
        // fires correctly; this test confirms it also fires when a fence is present.
        myFixture.configureByText(
            "case.tc.md",
            """
                |5. 1. Grant AI access
                |   2. Log in as test user with VM options:
                |      ```
                |      -Deap.require.license=release
                |      -Dfus.internal.test.mode=true
                |      ```
                |   3. Use AI in IDE
                |   > <caret>1. AI is available and responds
                |   > 2. Credits are spent
            """.trimMargin(),
        )

        myFixture.type('\b')

        myFixture.checkResult(
            """
                |5. 1. Grant AI access
                |   2. Log in as test user with VM options:
                |      ```
                |      -Deap.require.license=release
                |      -Dfus.internal.test.mode=true
                |      ```
                |   3. Use AI in IDE
                |   <caret>1. AI is available and responds
                |   > 2. Credits are spent
            """.trimMargin(),
        )
    }

    fun `test Backspace at start of single-line expected result removes the prefix`() {
        myFixture.configureByText(
            "case.tc.md",
            """
                |1. Click
                |   > <caret>Page loads
            """.trimMargin(),
        )

        myFixture.type('\b')

        myFixture.checkResult(
            """
                |1. Click
                |   <caret>Page loads
            """.trimMargin(),
        )
    }

    fun `test Backspace on empty blockquote line removes the arrow leaving only indent`() {
        myFixture.configureByText(
            "case.tc.md",
            """
                |1. Click
                |   > <caret>
            """.trimMargin(),
        )

        myFixture.type('\b')

        myFixture.checkResult(
            """
                |1. Click
                |   <caret>
            """.trimMargin(),
        )
    }

    fun `test Backspace inside content does not remove the blockquote prefix`() {
        myFixture.configureByText(
            "case.tc.md",
            """
                |1. Click
                |   > Page load<caret>s
            """.trimMargin(),
        )

        myFixture.type('\b')

        myFixture.checkResult(
            """
                |1. Click
                |   > Page loa<caret>s
            """.trimMargin(),
        )
    }

    fun `test Backspace works for two-digit step with 4-space indent`() {
        myFixture.configureByText(
            "case.tc.md",
            """
                |10. Click
                |    > <caret>Result
            """.trimMargin(),
        )

        myFixture.type('\b')

        myFixture.checkResult(
            """
                |10. Click
                |    <caret>Result
            """.trimMargin(),
        )
    }

    fun `test Backspace with cursor before the blockquote arrow removes the prefix`() {
        // Exact scenario from the logs: cursor at `   <caret>> 1. AI...` (before `>`).
        // The platform deletes the last indent space, giving `  <caret>> 1.`.
        // Our Case 2 handler then removes `> ` forward, producing `  <caret>1.`.
        myFixture.configureByText(
            "case.tc.md",
            """
                |1. Navigate to AI governance > Settings
                |   <caret>> 1. AI is shown as enabled for the organization
                |   > 2. Credits are visible
            """.trimMargin(),
        )

        myFixture.type('\b')

        myFixture.checkResult(
            """
                |1. Navigate to AI governance > Settings
                |  <caret>1. AI is shown as enabled for the organization
                |   > 2. Credits are visible
            """.trimMargin(),
        )
    }

    fun `test Backspace at start of numbered list item in expected result`() {
        // Reproduces the failing case: expected result is a numbered list.
        // Cursor at `   > <caret>1. AI is shown...` — Backspace must remove `> `.
        myFixture.configureByText(
            "case.tc.md",
            """
                |1. Navigate to AI governance > Settings
                |   > <caret>1. AI is shown as enabled for the organization
                |   > 2. Credits are visible
            """.trimMargin(),
        )

        myFixture.type('\b')

        myFixture.checkResult(
            """
                |1. Navigate to AI governance > Settings
                |   <caret>1. AI is shown as enabled for the organization
                |   > 2. Credits are visible
            """.trimMargin(),
        )
    }

    fun `test Backspace at start of second numbered list item in expected result`() {
        // Second line of the expected numbered list.
        myFixture.configureByText(
            "case.tc.md",
            """
                |1. Navigate to AI governance > Settings
                |   > 1. AI is shown as enabled for the organization
                |   > <caret>2. Credits are visible
            """.trimMargin(),
        )

        myFixture.type('\b')

        myFixture.checkResult(
            """
                |1. Navigate to AI governance > Settings
                |   > 1. AI is shown as enabled for the organization
                |   <caret>2. Credits are visible
            """.trimMargin(),
        )
    }

    fun `test tr_md files are handled the same way as tc_md`() {
        myFixture.configureByText(
            "case.tr.md",
            """
                |1. Click
                |   > <caret>Done
            """.trimMargin(),
        )

        myFixture.type('\b')

        myFixture.checkResult(
            """
                |1. Click
                |   <caret>Done
            """.trimMargin(),
        )
    }
}
