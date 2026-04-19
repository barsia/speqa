package io.github.barsia.speqa.parser

import io.github.barsia.speqa.model.DescriptionBlock
import io.github.barsia.speqa.model.PreconditionsBlock
import io.github.barsia.speqa.model.Priority
import io.github.barsia.speqa.model.Status
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue

import org.junit.Test

class TestCaseParserTest {
    @Test
    fun `parse full test case`() {
        val content = """
            |---
            |title: "Login with valid credentials"
            |priority: major
            |status: draft
            |environment:
            |  - "Chrome 120, macOS 14"
            |  - "Firefox 121, Windows 11"
            |tags:
            |  - auth
            |  - smoke
            |---
            |
            |Preconditions:
            |
            |- User account exists in the system
            |- User is on the login page
            |
            |Scenario:
            |
            |1. Type "testuser@example.com" into the email field
            |   > Email field accepts input, no validation errors
            |
            |2. Type "SecureP@ss123" into the password field
            |   > Password is masked, no validation errors
            |
            |3. Click the "Login" button
            |   > User is redirected to the dashboard
        """.trimMargin()

        val testCase = TestCaseParser.parse(content)

        assertEquals("Login with valid credentials", testCase.title)
        assertEquals(Priority.MAJOR, testCase.priority)
        assertEquals(Status.DRAFT, testCase.status)
        assertEquals(listOf("Chrome 120, macOS 14", "Firefox 121, Windows 11"), testCase.environment)
        assertEquals(listOf("auth", "smoke"), testCase.tags)
        assertEquals(1, testCase.bodyBlocks.size)
        assertTrue(testCase.bodyBlocks[0] is PreconditionsBlock)
        assertTrue(testCase.bodyBlocks[0].markdown.contains("User account exists"))
        assertEquals(3, testCase.steps.size)
        assertEquals("Type \"testuser@example.com\" into the email field", testCase.steps[0].action)
        assertEquals("Email field accepts input, no validation errors", testCase.steps[0].expected)
        assertEquals("Click the \"Login\" button", testCase.steps[2].action)
    }

    @Test
    fun `parse grouped expected applies to preceding steps`() {
        val content = """
            |---
            |title: "Minimal case"
            |priority: medium
            |status: draft
            |---
            |
            |Scenario:
            |
            |1. Open the page
            |2. Click submit
            |   > Form is submitted
        """.trimMargin()

        val testCase = TestCaseParser.parse(content)

        assertEquals(2, testCase.steps.size)
        assertEquals("Open the page", testCase.steps[0].action)
        assertNull(testCase.steps[0].expected)
        assertEquals("Click submit", testCase.steps[1].action)
        assertEquals("Form is submitted", testCase.steps[1].expected)
        assertEquals(2, testCase.steps[1].expectedGroupSize)
    }

    @Test
    fun `parse empty file returns default test case`() {
        val testCase = TestCaseParser.parse("")

        assertEquals("Untitled Test Case", testCase.title)
        assertNull(testCase.priority)
        assertNull(testCase.status)
        assertNull(testCase.environment)
        assertNull(testCase.tags)
        assertTrue(testCase.steps.isEmpty())
    }

    @Test
    fun `parse frontmatter only`() {
        val content = """
            |---
            |title: "Just metadata"
            |priority: critical
            |status: deprecated
            |---
        """.trimMargin()

        val testCase = TestCaseParser.parse(content)

        assertEquals("Just metadata", testCase.title)
        assertEquals(Priority.CRITICAL, testCase.priority)
        assertEquals(Status.DEPRECATED, testCase.status)
        assertTrue(testCase.steps.isEmpty())
        assertTrue(testCase.bodyBlocks.isEmpty())
    }

    @Test
    fun `parse crlf line endings`() {
        val content = "---\r\ntitle: \"CRLF\"\r\npriority: major\r\nstatus: ready\r\n---\r\n\r\nScenario:\r\n\r\n1. Do thing\r\n   > Done\r\n"

        val testCase = TestCaseParser.parse(content)

        assertEquals("CRLF", testCase.title)
        assertEquals(1, testCase.steps.size)
        assertEquals("Do thing", testCase.steps[0].action)
        assertEquals("Done", testCase.steps[0].expected)
    }

    @Test
    fun `malformed yaml does not throw and preserves valid fields`() {
        val content = """
            |---
            |title: "Broken
            |priority: major
            |---
        """.trimMargin()

        val testCase = TestCaseParser.parse(content)
        assertEquals(Priority.MAJOR, testCase.priority)
    }

    @Test
    fun `parse does not throw on tags colon no space`() {
        val content = """
            |---
            |id: 12
            |title: "Test"
            |priority: low
            |tags:-
            |---
        """.trimMargin()

        val testCase = TestCaseParser.parse(content)
        assertEquals(12, testCase.id)
        assertEquals("Test", testCase.title)
        assertEquals(Priority.LOW, testCase.priority)
    }

    @Test
    fun `parse does not throw on missing closing delimiter`() {
        val content = "---\ntitle: \"Test\"\npriority: major"
        val testCase = TestCaseParser.parse(content)
        assertEquals("Untitled Test Case", testCase.title)
    }

    @Test
    fun `parse returns partial data when one field is broken`() {
        val content = """
            |---
            |id: 5
            |title: "Valid title"
            |priority: medium
            |status: draft
            |environment:
            |  - "Chrome"
            |tags:-broken
            |---
            |
            |Scenario:
            |
            |1. Do something
            |   > Expected result
        """.trimMargin()

        val testCase = TestCaseParser.parse(content)
        assertEquals(5, testCase.id)
        assertEquals("Valid title", testCase.title)
        assertEquals(Priority.NORMAL, testCase.priority)
        assertEquals(Status.DRAFT, testCase.status)
        assertEquals(listOf("Chrome"), testCase.environment)
        assertEquals(1, testCase.steps.size)
    }

    @Test
    fun `parse empty frontmatter delimiters`() {
        val testCase = TestCaseParser.parse("---\n---")
        assertEquals("Untitled Test Case", testCase.title)
        assertNull(testCase.priority)
    }

    @Test
    fun `parse preconditions body block`() {
        val content = """
            |---
            |title: "With preconditions"
            |priority: medium
            |status: draft
            |---
            |
            |Preconditions:
            |
            |- Account exists
            |- Logged in
            |
            |Scenario:
            |
            |1. Click button
            |   > Button clicked
        """.trimMargin()

        val testCase = TestCaseParser.parse(content)

        assertEquals(1, testCase.bodyBlocks.size)
        assertTrue(testCase.bodyBlocks[0] is PreconditionsBlock)
        assertTrue(testCase.bodyBlocks[0].markdown.contains("Account exists"))
        assertTrue(testCase.bodyBlocks[0].markdown.contains("Logged in"))
        assertEquals(1, testCase.steps.size)
    }

    @Test
    fun `parse multiline action with sub-steps`() {
        val content = """
            |---
            |title: "Sub-steps"
            |---
            |
            |Scenario:
            |
            |1. Open the page
            |   - Click field 1
            |   - Click field 2
            |   > All fields are filled
            |
            |2. Submit the form
            |   > Form is saved
        """.trimMargin()

        val testCase = TestCaseParser.parse(content)

        assertEquals(2, testCase.steps.size)
        assertTrue(testCase.steps[0].action.contains("Open the page"))
        assertTrue(testCase.steps[0].action.contains("- Click field 1"))
        assertTrue(testCase.steps[0].action.contains("- Click field 2"))
        assertEquals("All fields are filled", testCase.steps[0].expected)
        assertEquals("Submit the form", testCase.steps[1].action)
        assertEquals("Form is saved", testCase.steps[1].expected)
    }

    @Test
    fun `multiline action round trip`() {
        val content = """
            |---
            |title: "Round trip"
            |---
            |
            |Scenario:
            |
            |1. Open the page
            |   - Click field 1
            |   - Click field 2
            |   > All fields are filled
        """.trimMargin()

        val parsed = TestCaseParser.parse(content)
        val serialized = TestCaseSerializer.serialize(parsed)
        val reparsed = TestCaseParser.parse(serialized)

        assertEquals(parsed.steps.size, reparsed.steps.size)
        assertEquals(parsed.steps[0].action, reparsed.steps[0].action)
        assertEquals(parsed.steps[0].expected, reparsed.steps[0].expected)
    }

    @Test
    fun `parse id from frontmatter`() {
        val content = """
            |---
            |id: 42
            |title: "With ID"
            |---
            |
            |Scenario:
            |
            |1. Do thing
            |   > Done
        """.trimMargin()

        val testCase = TestCaseParser.parse(content)
        assertEquals(42, testCase.id)
    }

    @Test
    fun `parse missing id returns null`() {
        val content = """
            |---
            |title: "No ID"
            |---
            |
            |Scenario:
            |
            |1. Do thing
        """.trimMargin()

        val testCase = TestCaseParser.parse(content)
        assertNull(testCase.id)
    }

    @Test
    fun `parse description body block`() {
        val content = """
            |---
            |title: "With description"
            |priority: medium
            |status: draft
            |---
            |
            |This test validates the login flow end-to-end.
            |
            |Scenario:
            |
            |1. Open the login page
            |   > Login page is displayed
        """.trimMargin()

        val testCase = TestCaseParser.parse(content)

        assertEquals(1, testCase.bodyBlocks.size)
        assertTrue(testCase.bodyBlocks[0] is DescriptionBlock)
        assertTrue(testCase.bodyBlocks[0].markdown.contains("login flow"))
        assertEquals(1, testCase.steps.size)
    }

    @Test
    fun `parse general attachments section`() {
        val content = """
            |---
            |title: "With attachments"
            |---
            |
            |Attachments:
            |
            |[attachments/with-attachments/spec.pdf]
            |[attachments/with-attachments/screenshot.png]
            |
            |Scenario:
            |
            |1. Open page
            |   > Page loads
        """.trimMargin()

        val testCase = TestCaseParser.parse(content)

        assertEquals(2, testCase.attachments.size)
        assertEquals("attachments/with-attachments/spec.pdf", testCase.attachments[0].path)
        assertEquals("attachments/with-attachments/screenshot.png", testCase.attachments[1].path)
        assertEquals(1, testCase.steps.size)
    }

    @Test
    fun `parse step attachments after expected block`() {
        val content = """
            |---
            |title: "Step attachments"
            |---
            |
            |Scenario:
            |
            |1. Click the login button
            |   > User is redirected
            |   [attachments/step-attachments/result.png]
        """.trimMargin()

        val testCase = TestCaseParser.parse(content)

        assertEquals(1, testCase.steps.size)
        assertTrue(testCase.steps[0].actionAttachments.isEmpty())
        assertEquals(1, testCase.steps[0].expectedAttachments.size)
        assertEquals("attachments/step-attachments/result.png", testCase.steps[0].expectedAttachments[0].path)
        assertEquals("User is redirected", testCase.steps[0].expected)
    }

    @Test
    fun `parse expected result attachments`() {
        val content = """
            |---
            |title: "Expected attachments"
            |---
            |
            |Scenario:
            |
            |1. Click login
            |   > User is redirected to dashboard
            |   [attachments/expected-attachments/expected.png]
        """.trimMargin()

        val testCase = TestCaseParser.parse(content)

        assertEquals(1, testCase.steps.size)
        assertEquals("User is redirected to dashboard", testCase.steps[0].expected)
        assertEquals(1, testCase.steps[0].expectedAttachments.size)
        assertEquals("attachments/expected-attachments/expected.png", testCase.steps[0].expectedAttachments[0].path)
    }

    @Test
    fun `parse standard markdown link as attachment`() {
        val content = """
            |---
            |title: "Markdown links"
            |---
            |
            |Attachments:
            |
            |[design spec](attachments/markdown-links/spec.pdf)
            |![screenshot](attachments/markdown-links/shot.png)
            |
            |Scenario:
            |
            |1. Do thing
        """.trimMargin()

        val testCase = TestCaseParser.parse(content)

        assertEquals(2, testCase.attachments.size)
        assertEquals("attachments/markdown-links/spec.pdf", testCase.attachments[0].path)
        assertEquals("attachments/markdown-links/shot.png", testCase.attachments[1].path)
    }

    @Test
    fun `text after preconditions belongs to preconditions block`() {
        val content = """
            |---
            |title: "Login with valid credentials"
            |---
            |
            |This test case verifies the standard login flow for active users.
            |
            |Preconditions:
            |
            |1. User account exists in the system
            |2. User is on the login page
            |
            |Extra context for the tester.
            |
            |Scenario:
            |
            |1. Type username
            |   > Field accepts input
        """.trimMargin()

        val testCase = TestCaseParser.parse(content)

        assertEquals(2, testCase.bodyBlocks.size)
        assertTrue(testCase.bodyBlocks[0] is DescriptionBlock)
        assertTrue(testCase.bodyBlocks[1] is PreconditionsBlock)
        assertTrue(
            "Preconditions should contain 'Extra context', but was: ${testCase.bodyBlocks[1].markdown}",
            testCase.bodyBlocks[1].markdown.contains("Extra context"),
        )
        assertEquals(1, testCase.steps.size)
    }

    @Test
    fun `parse links section`() {
        val content = """
            |---
            |title: "With links"
            |---
            |
            |Links:
            |
            |[Jira ticket](https://jira.example.com/TC-123)
            |[Design doc](https://figma.com/file/abc123)
            |
            |Scenario:
            |
            |1. Open page
            |   > Page loads
        """.trimMargin()

        val testCase = TestCaseParser.parse(content)

        assertEquals(2, testCase.links.size)
        assertEquals("Jira ticket", testCase.links[0].title)
        assertEquals("https://jira.example.com/TC-123", testCase.links[0].url)
        assertEquals("Design doc", testCase.links[1].title)
        assertEquals("https://figma.com/file/abc123", testCase.links[1].url)
        assertEquals(1, testCase.steps.size)
    }

    @Test
    fun `parse step with ticket line`() {
        val content = """
            |---
            |title: Test
            |---
            |
            |Scenario:
            |
            |1. Do something
            |   > Expected outcome
            |
            |   Ticket: PROJ-123, PROJ-456
        """.trimMargin()
        val tc = TestCaseParser.parse(content)
        assertEquals("PROJ-123, PROJ-456", tc.steps[0].ticket)
    }

    @Test
    fun `parse step without ticket has null ticket`() {
        val content = """
            |---
            |title: Test
            |---
            |
            |Scenario:
            |
            |1. Do something
            |   > Expected outcome
        """.trimMargin()
        val tc = TestCaseParser.parse(content)
        assertNull(tc.steps[0].ticket)
    }

    @Test
    fun `parse multiple steps with tickets on some`() {
        val content = """
            |---
            |title: Test
            |---
            |
            |Scenario:
            |
            |1. First step
            |   > Result one
            |
            |   Ticket: BUG-1
            |
            |2. Second step
            |   > Result two
            |
            |3. Third step
            |   > Result three
            |
            |   Ticket: BUG-2, BUG-3
        """.trimMargin()
        val tc = TestCaseParser.parse(content)
        assertEquals(3, tc.steps.size)
        assertEquals("BUG-1", tc.steps[0].ticket)
        assertNull(tc.steps[1].ticket)
        assertEquals("BUG-2, BUG-3", tc.steps[2].ticket)
    }
}
