---
title: Sync Scroll
---

Sync scroll keeps the text editor and the preview panel in the same position as you scroll. When you scroll on one side, the other side follows automatically.

SpeQA enables sync scroll by default for all `.tc.md` and `.tr.md` files.

## How It Works

The text editor and the preview use different layouts: the source file is plain text with uniform line heights, while the preview renders step cards with variable heights depending on content (code blocks, long expected results, multi-line actions). SpeQA syncs by step anchor rather than by scroll fraction, so the matching step stays visible on both sides regardless of how tall its card is.

## Toggling Sync Scroll

Click the **...** button on the editor tab (the three-dot overflow menu) and choose **Sync Scroll**. The setting is stored per project and remembered across restarts.

## Tips

- If you scroll to the very end of the text editor, the preview also scrolls to the very end.
- Scrolling in the preview moves the text editor to the same step.
- Sync scroll is independent of [soft wrap](./soft-wrap.md) - you can use either or both.
