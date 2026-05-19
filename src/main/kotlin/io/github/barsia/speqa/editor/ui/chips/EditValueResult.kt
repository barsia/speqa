// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package io.github.barsia.speqa.editor.ui.chips

/**
 * Apply an "edit" of [oldValue] to [newValue] over a list of metadata values
 * (tags, environments). Returns the resulting list.
 *
 * Rules:
 *  - Empty or whitespace-only [newValue]: no-op (returns original list).
 *  - [oldValue] not found: no-op.
 *  - [newValue] equals [oldValue] after trim: no-op.
 *  - [newValue] (trimmed) already exists elsewhere in the list: collapses to a
 *    delete-of-old (the duplicate stays at its original index).
 *  - Otherwise: [oldValue] is replaced in place by the trimmed [newValue].
 */
fun editValueResult(values: List<String>, oldValue: String, newValue: String): List<String> {
  val trimmedNew = newValue.trim()
  if (trimmedNew.isEmpty()) return values
  if (trimmedNew == oldValue) return values
  val oldIndex = values.indexOf(oldValue)
  if (oldIndex < 0) return values
  if (trimmedNew in values) {
    return values.filterIndexed { i, _ -> i != oldIndex }
  }
  return values.toMutableList().apply { this[oldIndex] = trimmedNew }
}
