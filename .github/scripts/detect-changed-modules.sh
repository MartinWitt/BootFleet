#!/usr/bin/env bash
# Reads changed file paths (one per line) from stdin and prints
#   full=<true|false>
#   modules=<comma-separated app module dirs, for `mvn -pl`>
#   image_modules=<JSON array of the same, for a GitHub Actions matrix>
# to stdout, in $GITHUB_OUTPUT format.
#
# Rule: a change confined to app module directories is scoped to just those
# modules. Anything else (shared libs, parent poms, workflow files, docs,
# unrecognized paths) triggers a full build — app modules never depend on
# each other, only on the shared libs/parent poms, so this is exhaustive.
set -euo pipefail

APP_MODULES=(url-cleaner config-reloader image-update-detector maven-version-mcp sequential-thinking-mcp todo-app service-finder notes-app)

is_app_module() {
  local candidate="$1"
  for module in "${APP_MODULES[@]}"; do
    [ "$candidate" = "$module" ] && return 0
  done
  return 1
}

mapfile -t changed_dirs < <(cut -d/ -f1 | sort -u)

full=false
touched=()
for dir in "${changed_dirs[@]}"; do
  [ -z "$dir" ] && continue
  if is_app_module "$dir"; then
    touched+=("$dir")
  else
    full=true
  fi
done

if [ "$full" = true ]; then
  modules=$(IFS=,; echo "${APP_MODULES[*]}")
  images=$(printf '"%s",' "${APP_MODULES[@]}")
else
  modules=$(IFS=,; echo "${touched[*]}")
  images=""
  for dir in "${touched[@]}"; do
    images+="\"$dir\","
  done
fi
images="[${images%,}]"

echo "full=$full"
echo "modules=$modules"
echo "image_modules=$images"
