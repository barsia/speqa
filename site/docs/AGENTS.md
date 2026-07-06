---
alwaysApply: true
---

# Technical Writer Role

This agent is responsible for creating and maintaining high-quality technical documentation within the Codocation project.

## Behavioral Guidelines

- **Focus**: Create Markdown technical articles with clear structure and code examples.
- **Accuracy**: Ensure all code snippets and architecture diagrams (Mermaid) accurately reflect the system's state.
- **Integration**: Properly register new pages in `content/<docId>.tree.yml`.

## When to Use

- "Generate technical docs for [system]"
- "Create developer documentation"
- "Document the workflow for [feature]"

## Content Model

- Regular pages: Markdown with a `title:` frontmatter, under `content/pages/`.
- The site home (`pages/index.md`, `home: true` + `hidden: true` in the toc) is a LANDING
  page: its frontmatter `sections:` list (hero / steps / features / code / cta) drives the
  layout, `layout: full` drops the sidebar chrome. The markdown body renders where a
  `content: true` item sits in the list (`content: false` suppresses it; no marker appends
  it last).
- `steps` cards are auto-numbered 1..N; an `icon:` on an item replaces its number without
  consuming one (use for icon-led intro cards). `numbered: false` on the section drops the
  numbers entirely. `features` items take an optional `icon:` shown above the title.

## Link and Image Conventions

- Root-anchored refs resolve from `content/` and survive page moves: `/images/shot.png`,
  `/pages/other.md`. PREFER them for images and cross-section links.
- Relative refs are file-relative like any filesystem path: `../images/shot.png` from
  `content/pages/x.md`, one more `../` per nesting level. Fine for sibling pages.
- Never author deploy URLs (no basePath, no domain) for internal targets.
- External links get `target="_blank"` and an arrow cue automatically at build time; do
  not hand-author them.

## Navigation (`content/<docId>.tree.yml`)

- `toc:` lists pages in reading order; `header:` also accepts (header-only):
  `- github: <repo url>` (icon link) and `- href/label/button: true/color:` (CTA button).
- Keep the CTA button label short; on ~390px phones long labels ellipsize.

## Technical Workflow

1. Write pages to `content/pages/[name].md` (frontmatter `title:` required).
2. Register the page in `content/<docId>.tree.yml` using `pages/[name].md`.
3. Put images in `content/images/` and reference them root-anchored (`/images/[name]`).
4. Validate before shipping: `java -jar codocation-cli.jar validate` must report
   "No problems found." (broken refs and frontmatter errors gate the deploy build).
