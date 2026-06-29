#!/bin/bash

# Exit immediately if a command exits with a non-zero status
set -e

# Check if version argument is provided
if [ -z "$1" ]; then
  echo "❌ Error: No version specified."
  echo "Usage: ./release.sh <version_number> (e.g., ./release.sh 1.0.0)"
  exit 1
fi

VERSION=$1

# Clean version (strip leading v if present)
CLEAN_VERSION=$(echo "$VERSION" | sed 's/^v//')
TAG_VERSION="v$CLEAN_VERSION"

echo "🚀 Preparing release for version: $TAG_VERSION"

# Update version in app/build.gradle.kts
GRADLE_FILE="app/build.gradle.kts"
if [ -f "$GRADLE_FILE" ]; then
  echo "📝 Updating version in $GRADLE_FILE to $CLEAN_VERSION..."
  # Use sed to replace versionName
  if [[ "$OSTYPE" == "darwin"* ]]; then
    # macOS sed syntax
    sed -i '' "s/versionName = \"[0-9.]*\"/versionName = \"$CLEAN_VERSION\"/" "$GRADLE_FILE"
  else
    # Linux sed syntax
    sed -i "s/versionName = \"[0-9.]*\"/versionName = \"$CLEAN_VERSION\"/" "$GRADLE_FILE"
  fi
else
  echo "⚠️ Warning: $GRADLE_FILE not found, skipping version string update."
fi

# Git operations
git add "$GRADLE_FILE" || true
git commit -m "Bump version to $TAG_VERSION" || echo "No version changes to commit"

# Determine current branch
CURRENT_BRANCH=$(git branch --show-current)
if [ -z "$CURRENT_BRANCH" ]; then
  CURRENT_BRANCH="main"
fi

echo "➡️ Pushing latest commits on '$CURRENT_BRANCH' to origin..."
git push origin "$CURRENT_BRANCH" || echo "⚠️ Note: git push complete or nothing new to send."

# Delete local tag if it exists
if git rev-parse "$TAG_VERSION" >/dev/null 2>&1; then
  echo "🗑️ Removing local tag $TAG_VERSION..."
  git tag -d "$TAG_VERSION"
fi

# Delete remote tag if it exists (fails silently if it doesn't exist)
echo "🗑️ Removing remote tag $TAG_VERSION if it exists..."
git push --delete origin "$TAG_VERSION" 2>/dev/null || true

# Create the new tag
echo "🏷️ Creating new local tag $TAG_VERSION..."
git tag "$TAG_VERSION"

# Push the tag to trigger GitHub Action
echo "📤 Pushing tag $TAG_VERSION to GitHub..."
git push origin "$TAG_VERSION"

echo "✅ Success! Tag $TAG_VERSION pushed to GitHub."
echo "🔗 GitHub Action will build the APK and attach it to the Release."
