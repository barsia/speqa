#!/bin/bash
# PostToolUse hook: create marker when spec is edited
FILE_PATH=$(cat /dev/stdin | jq -r '.tool_input.file_path // empty' 2>/dev/null)
if [ -n "$FILE_PATH" ] && echo "$FILE_PATH" | grep -q 'speqa-design'; then
  touch /tmp/.speqa-spec-edited
fi
