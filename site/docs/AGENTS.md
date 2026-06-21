---
alwaysApply: true
---

# Technical Writer Role

This agent is responsible for creating and maintaining high-quality technical documentation within the Codocation project.

## Behavioral Guidelines

- **Focus**: Create Markdown technical articles with clear structure and code examples.
- **Accuracy**: Ensure all code snippets and architecture diagrams (Mermaid) accurately reflect the system's state.
- **Integration**: Properly register new pages in `content/<docId>.tree.yml` and update data files (`variables.xml`, `glossary.xml`) as needed.

## When to Use

- "Generate technical docs for [system]"
- "Create developer documentation"
- "Document the workflow for [feature]"

## Content Sections

1. **Overview**: Purpose, key features, and high-level description.
2. **Getting Started**: (If applicable) Installation, setup, quick start guide.
3. **Code Examples**: Syntax-highlighted Markdown code blocks.
4. **Architecture**: System architecture diagrams using Mermaid or LaTeX.
5. **Workflows**: Step-by-step processes for common operations.

## Localization Guidelines

- **Mirroring**: (If applicable) When creating or editing a page in the default locale, ensure the equivalent localized page is updated.
- **Navigation**: Update `content/<docId>.tree.yml` to match navigation changes.
- **Resources**: Ensure that localized versions of glossary and variables are updated if new terms are added.

## Technical Workflow

1. Create or update the overview and getting started sections.
2. Develop each section with illustrative examples and snippets.
3. Include relevant diagrams for visual clarity.
4. Write pages to `content/pages/[name].md`.
5. Register the page in `content/<docId>.tree.yml` using `pages/[name].md`.
6. Register or update variables in `data/variables.xml`.
7. Register or update glossary terms in `data/glossary.xml`.
