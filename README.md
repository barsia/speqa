# SpeQA — Test Management System

[![JetBrains Marketplace](https://img.shields.io/badge/JetBrains%20Marketplace-SpeQA-000000?logo=jetbrains)](https://plugins.jetbrains.com/plugin/31268-speqa--test-management-system)

Write and run manual test cases right inside your JetBrains IDE. No external services, no vendor lock-in.

## What is SpeQA?

SpeQA is an IntelliJ platform plugin for manual test case management.
Test cases are Markdown files (`.tc.md`) stored in your project and versioned with Git.

## Features

- **Visual editor** — split view with native text editor on the left and interactive preview on the right
- **Test runs** — step-by-step execution with pass/fail/blocked verdicts (`.tr.md` files)
- **Full history in Git** — every change is a commit you can diff, blame, or revert
- **Status at a glance** — color-coded file icons in the project tree
- **Tag & environment support** — project-wide registry
- **Works offline** — everything runs locally

## Installation

In your JetBrains IDE: **Settings → Plugins → Marketplace** → search "SpeQA" → Install.

## Getting Started

1. Right-click a directory → **New → Test Case**
2. Click the **Run** button in the test case preview, or right-click a `.tc.md` file → **Run Test Case**

## Compatibility

| IDE                   | Versions          |
|-----------------------|-------------------|
| IntelliJ-platform IDE | 2025.3.4 — 2026.3 |

## Contributing

Contributions are welcome. Please read the [Contributing Guide](CONTRIBUTING.md) before submitting a PR.

**Key rule:** every PR must be linked to a GitHub issue. Create or find a relevant issue first, then implement.

## Privacy

See [Privacy Policy](PRIVACY_POLICY.md).

## License

Apache License 2.0 — see [LICENSE](LICENSE) and [EULA](EULA.md).
