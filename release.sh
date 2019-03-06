#!/bin/bash
set -e

# Check that we aren't doing something wrong
if ! git diff-index --quiet --cached HEAD --; then
	echo "There are files staged for commit, aborting"
	exit 1
elif [ "$(git rev-parse --abbrev-ref HEAD)" != "master" ]; then
	echo "Must be on the branch 'master', aborting"
	exit 1
fi

# Determine what versions to use based on the current version
VERSION=$(awk -F= '$1=="version"{print $2}' gradle.properties)
read -p "Enter the release version (${VERSION%-SNAPSHOT}): " RELEASE_VERSION
read -p "Enter the post-release version (${VERSION}): " POST_RELEASE_VERSION
RELEASE_VERSION="${RELEASE_VERSION:=${VERSION%-SNAPSHOT}}"
POST_RELEASE_VERSION="${POST_RELEASE_VERSION:=${VERSION}}"
POST_RELEASE_VERSION="${POST_RELEASE_VERSION%-SNAPSHOT}-SNAPSHOT"

# Update the 'gradle.properties' file, commit and tag it
sed -i "" 's/^version=.*$/version='"${RELEASE_VERSION}"'/' gradle.properties
git add gradle.properties
git commit --quiet -m "Release ${RELEASE_VERSION}"
git tag "${RELEASE_VERSION}"

# Update the 'gradle.properties' file back to the next SNAPSHOT version
sed -i "" 's/^version=.*$/version='"${POST_RELEASE_VERSION}"'/' gradle.properties
git add gradle.properties
git commit --quiet -m "Change version back to ${POST_RELEASE_VERSION}"

read -p "Ready to push? (y/[n]): " PUSH_IT
if [ "$PUSH_IT" == "y" ]; then
	git push --tags origin HEAD
else
	git tag -d "${RELEASE_VERSION}"
	git reset --soft HEAD~2
fi
