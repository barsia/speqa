---
title: SpeQA TMS
seo-description: Manual test case management for JetBrains IDEs. Write and execute test cases as plain Markdown files, versioned with Git.
layout: full
sections:
  - hero:
      badge: Free & open source · Apache 2.0
      heading: Test management for manual testers, right in your IDE.
      subtitle: SpeQA is a manual test case management system for JetBrains IDEs. Write and execute test cases as plain Markdown files stored in your project - versioned with Git, no external services, no vendor lock-in.
      buttons:
        - label: Install from JetBrains Marketplace
          href: https://plugins.jetbrains.com/plugin/31268-speqa--test-management-system
          primary: true
        - label: Get Started
          href: /speqa/getting-started/
        - label: Report a bug
          href: https://github.com/barsia/speqa/issues/new?template=bug_report.yml
        - label: Request a feature
          href: https://github.com/barsia/speqa/issues/new?template=feature_request.yml
  - steps:
      label: How it works
      heading: Three steps to your first test run
      items:
        - title: Generate test cases with Claude Code
          text: Use the bundled Claude Code skill to generate structured test cases directly from specs, user stories, Jira tickets, or PR descriptions. SpeQA writes Markdown - no manual formatting needed.
          icon: /images/ai.svg
        - title: Create a test case (.tc.md)
          text: Right-click a directory, choose New > SpeQA Test Case, or run the action from the palette. A Markdown file with YAML frontmatter appears - split editor shows live preview as you type.
        - title: Describe steps in the interactive preview
          text: Edit actions, expected results, tags, environment, and step-linked YouTrack tickets directly in the panel. Drag-and-drop to reorder everything - changes write back to Markdown in real time.
        - title: Run your test
          text: Hit Run on any test case or use the context menu. SpeQA creates a .tr.md test run - per-step verdicts (Passed / Failed / Skipped), comments, and an overall status. Every result is versioned in Git.
  - features:
      label: Features
      heading: Everything your testing workflow needs, nothing you don't
      items:
        - title: Split editor
          text: Native IntelliJ editor on the left, interactive panel on the right. Scroll-sync toggleable in the bar.
          icon: /images/split-editor.svg
        - title: Plain Markdown format
          text: Test cases and runs stored as Markdown. Test runs recorded with verdicts, timestamps, and comments.
          icon: /images/markdown.svg
        - title: Test case writer Claude Code skill
          text: Bundled Claude Code skill generates test cases directly from specs, tickets, or PR. Writes test cases in seconds.
          icon: /images/claude.svg
        - title: Offline with Git versioning
          text: No cloud, no vendor lock-in. Test cases live in your project directory. Everything works offline, versioned with Git.
          icon: /images/offline.svg
  - code:
      label: Data format
      heading: Your test cases are plain Markdown. No vendor lock-in, ever.
      subtitle: Test cases and runs stored as plain Markdown.
      language: markdown
      code: |
        ---
        id: 42
        title: User login with email and password
        status: ready
        priority: critical
        environment: [Browser]
        tags: [ui, security]
        ---

        Verify that authorized users can successfully authenticate
        using valid email and password.

        Preconditions:
        User has a verified account in the system.

        Scenario:

        1. Open the login page
        2. Enter email address in the input field
        3. Enter a valid password into the password field
        4. Click the "Sign In" button
           > The user is redirected to the dashboard
           > The user sees their profile data
  - cta:
      heading: Free, open source - Apache 2.0
      subtitle: Every change is a Git-commit-able diff that you can review, revert, or branch - just like your code.
      button:
        label: Install from JetBrains Marketplace
        href: https://plugins.jetbrains.com/plugin/31268-speqa--test-management-system
---
