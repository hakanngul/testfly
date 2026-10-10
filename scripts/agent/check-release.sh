#!/usr/bin/env bash
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$repo_root"

fail() {
  echo "release check: $*" >&2
  exit 1
}

version="$(mvn -B -q help:evaluate -Dexpression=project.version -DforceStdout)"
version_re='^[0-9]+\.[0-9]+\.[0-9]+(-[A-Za-z0-9.]+)?$'
[[ "$version" != *$'\n'* && "$version" != *$'\r'* && "$version" =~ $version_re ]] || \
  fail "project version '$version' is not release SemVer"

rg -Fq "## [$version]" CHANGELOG.md || fail "CHANGELOG.md has no $version section"
rg -Fq "## [$version]" docs-site/docs/changelog.md || fail "English docs changelog has no $version section"
rg -Fq "## [$version]" docs-site/i18n/tr/docusaurus-plugin-content-docs/current/changelog.md || \
  fail "Turkish docs changelog has no $version section"
rg -Fq "<version>$version</version>" README.md || fail "README.md has no Maven example for $version"
rg -Fq "<version>$version</version>" .github/profile/README.md || \
  fail "organization README has no Maven example for $version"

rg -Fq '<id>release</id>' pom.xml || fail "pom.xml has no release profile"
rg -Fq 'git merge-base --is-ancestor "$GITHUB_SHA" origin/main' .github/workflows/release.yml || \
  fail "release workflow has no main-ancestry check"
rg -Fq 'mvn -B verify --no-transfer-progress' .github/workflows/release.yml || \
  fail "release workflow does not run Maven verification"
rg -Fq 'mvn -B deploy -Prelease -DskipTests --no-transfer-progress' .github/workflows/release.yml || \
  fail "release workflow does not deploy through the release profile"
rg -Fq 'maven-3.9' .github/workflows/release.yml || \
  fail "release workflow does not pin Maven 3.9 for Central publishing"

git diff --check

if git rev-parse -q --verify "refs/tags/v$version" >/dev/null; then
  echo "release check: local tag v$version exists"
else
  echo "release check: local tag v$version does not exist yet (expected during preparation)"
fi
echo "release check: PASS for $version"
