# GitHub Repository Setup

Recommended settings for the SpeQA GitHub repository.

## Branch protection (Settings → Branches)

**Branch:** `main`

- [x] Require conversation resolution before merging

Note: PR requirement is not enabled — the maintainer pushes directly to main. External contributors submit PRs per CONTRIBUTING.md.

## General (Settings → General)

- **Features:**
  - [x] Issues
  - [ ] Discussions
  - [ ] Wiki (use docs/ in repo instead)
  - [ ] Projects (not needed yet)
- **Pull Requests:**
  - [x] Allow squash merging (default)
  - [ ] Allow merge commits
  - [ ] Allow rebase merging
  - [x] Automatically delete head branches
