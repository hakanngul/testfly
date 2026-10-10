#!/usr/bin/env bash
# Scan a web page and print TestFly Locator declarations verified by the real SDK.
# Usage: find-locators.sh <url> [--class LoginPage] [--out f.md] [--json f.json]
#        [--screenshot f.png] [--wait ms] [--test-id-attr data-test] [--headed] [--include-hidden]
set -euo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
repo_root="$(cd "$script_dir/../../../.." && pwd)"
cache_dir="$repo_root/target/locator-finder"
mkdir -p "$cache_dir"

# SDK classes: compile once, reuse while sources are older than target/classes.
if [[ ! -f "$repo_root/target/classes/io/testfly/locator/Locator.class" ]] \
   || [[ -n "$(find "$repo_root/src/main/java/io/testfly/locator" -newer "$repo_root/target/classes/io/testfly/locator/Locator.class" -name '*.java' 2>/dev/null)" ]]; then
  echo "locator-finder: compiling TestFly (mvn -q compile)" >&2
  (cd "$repo_root" && mvn -q -DskipTests compile) >&2
fi

classpath_file="$cache_dir/classpath.txt"
if [[ ! -s "$classpath_file" || "$repo_root/pom.xml" -nt "$classpath_file" ]]; then
  echo "locator-finder: resolving runtime classpath" >&2
  (cd "$repo_root" && mvn -q dependency:build-classpath -Dmdep.includeScope=runtime -Dmdep.outputFile="$classpath_file") >&2
fi

exec java -cp "$repo_root/target/classes:$(cat "$classpath_file")" \
  "$script_dir/LocatorFinder.java" --scan-js "$script_dir/locator-scan.js" "$@"
