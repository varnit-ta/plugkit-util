#!/usr/bin/env bash
set -euo pipefail

release_tags() {
  git tag --list 'v*' | grep -E '^v[0-9]+\.[0-9]+\.[0-9]+$' | sed 's/^v//' | sort -V || true
}

latest="$(release_tags | tail -n 1)"
latest="${latest:-0.0.0}"
IFS=. read -r major minor _ <<< "${latest}"
next="${major}.$((minor + 1)).0"

event="${GITHUB_EVENT_NAME:-push}"
branch="${GITHUB_REF_NAME:-$(git rev-parse --abbrev-ref HEAD)}"

if [[ "${GITHUB_REF_TYPE:-branch}" != "branch" ]]; then
  echo "Build & Release must run on a branch, not on ${GITHUB_REF_TYPE} '${branch}'" >&2
  exit 1
fi

version=""
publish="false"
release="false"

if [[ "${branch}" == "main" ]]; then
  pom_version="$(sed -nE 's#^[[:space:]]*<revision>([^<]+)</revision>.*#\1#p' pom.xml | head -n 1)"
  higher="$(printf '%s\n%s\n' "${latest}" "${pom_version}" | sort -V | tail -n 1)"
  if [[ "${pom_version}" =~ ^(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)$ \
        && "${pom_version}" != "${latest}" && "${higher}" == "${pom_version}" ]]; then
    if [[ "${BASH_REMATCH[3]}" != "0" ]]; then
      echo "pom.xml <revision> ${pom_version}: main releases X.Y.0 versions; patch versions come from hotfix/X.Y.Z branches" >&2
      exit 1
    fi
    version="${pom_version}"
  else
    version="${next}"
  fi
  publish="true"
  release="true"

elif [[ "${branch}" == hotfix* ]]; then
  fail() { echo "hotfix: $1" >&2; exit 1; }

  [[ "${branch}" =~ ^hotfix/(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)$ ]] \
    || fail "branch must be named hotfix/X.Y.Z, e.g. hotfix/0.2.1 (got '${branch}')"
  h_major="${BASH_REMATCH[1]}"
  h_minor="${BASH_REMATCH[2]}"
  h_patch="${BASH_REMATCH[3]}"
  version="${h_major}.${h_minor}.${h_patch}"

  (( h_patch >= 1 )) \
    || fail "${version} is not a patch version; hotfixes are X.Y.1 or higher (X.Y.0 comes from main)"

  git rev-parse -q --verify "refs/tags/v${h_major}.${h_minor}.0" > /dev/null \
    || fail "release line ${h_major}.${h_minor} does not exist (no tag v${h_major}.${h_minor}.0) - nothing to hotfix"

  nearest="$(git describe --tags --abbrev=0 --match 'v[0-9]*.[0-9]*.[0-9]*' 2> /dev/null | sed 's/^v//' || true)"
  [[ "${nearest}" =~ ^${h_major}\.${h_minor}\.[0-9]+$ ]] \
    || fail "branch must start from a v${h_major}.${h_minor}.x tag or an earlier hotfix/${h_major}.${h_minor}.x branch (it contains ${nearest:-no release tag})"

  if git rev-parse -q --verify "refs/tags/v${version}" > /dev/null; then
    fail "${version} is already released; for another fix use hotfix/${h_major}.${h_minor}.$((h_patch + 1))"
  fi

  publish="true"
  release="true"

else
  version="${next}-SNAPSHOT"
  publish="true"
fi

echo "branch=${branch} event=${event} latest-release=${latest}"
echo "version=${version} publish=${publish} release=${release}"

if [[ -n "${GITHUB_OUTPUT:-}" ]]; then
  {
    echo "version=${version}"
    echo "publish=${publish}"
    echo "release=${release}"
  } >> "${GITHUB_OUTPUT}"
fi
