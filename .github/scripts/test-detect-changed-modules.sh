#!/usr/bin/env bash
# Self-check for detect-changed-modules.sh. Run directly: ./test-detect-changed-modules.sh
set -euo pipefail
cd "$(dirname "$0")"

assert_contains() {
  local haystack="$1" needle="$2" msg="$3"
  case "$haystack" in
    *"$needle"*) ;;
    *) echo "FAIL: $msg (expected to find '$needle' in: $haystack)"; exit 1 ;;
  esac
}

# Single app module changed -> scoped, not full
out=$(printf 'todo-app/src/Foo.java\ntodo-app/pom.xml\n' | ./detect-changed-modules.sh)
assert_contains "$out" "full=false" "single module change should not be full"
assert_contains "$out" "modules=todo-app" "single module change should scope to todo-app"
assert_contains "$out" 'image_modules=["todo-app"]' "single module image list should be just todo-app"

# Shared lib changed -> full build
out=$(printf 'spring-boot-utils/src/Bar.java\n' | ./detect-changed-modules.sh)
assert_contains "$out" "full=true" "shared lib change should trigger full build"
assert_contains "$out" "modules=url-cleaner" "full build should list all app modules"

# Unrecognized path (workflow file) -> full build
out=$(printf '.github/workflows/ci.yml\n' | ./detect-changed-modules.sh)
assert_contains "$out" "full=true" "workflow file change should trigger full build"

# Two app modules changed -> scoped to both, no full
out=$(printf 'todo-app/pom.xml\nservice-finder/pom.xml\n' | ./detect-changed-modules.sh)
assert_contains "$out" "full=false" "two module change should not be full"
assert_contains "$out" "todo-app" "two module change should include todo-app"
assert_contains "$out" "service-finder" "two module change should include service-finder"

echo "All detect-changed-modules.sh checks passed."
