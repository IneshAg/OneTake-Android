#!/usr/bin/env bash
set -euo pipefail

# ==============================================================================
# OneTake Android — Automated Release Script
# Builds release APK/AAB, runs tests, generates checksums, and tags git release.
#
# Usage:
#   ./scripts/release.sh [version] [--publish]
# Example:
#   ./scripts/release.sh 0.2.0
#   ./scripts/release.sh 0.2.0 --publish
# ==============================================================================

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
cd "$ROOT_DIR"

PUBLISH=false
VERSION=""

for arg in "$@"; do
  case "$arg" in
    --publish)
      PUBLISH=true
      ;;
    v*.*.*|*.*.*)
      VERSION="${arg#v}"
      ;;
    *)
      echo "Unknown argument: $arg"
      echo "Usage: ./scripts/release.sh [version] [--publish]"
      exit 1
      ;;
  esac
done

# If version is not specified, parse it from app/build.gradle.kts
if [ -z "$VERSION" ]; then
  VERSION=$(grep 'val appVersionName' app/build.gradle.kts | head -n1 | sed -E 's/.*"([0-9]+\.[0-9]+\.[0-9]+)".*/\1/')
fi

if [ -z "$VERSION" ]; then
  echo "Error: Could not determine release version."
  exit 1
fi

TAG="v$VERSION"
echo "=================================================="
echo " Preparing OneTake Release: $TAG ($VERSION)"
echo "=================================================="

# Detect JAVA_HOME if not configured
if [ -z "${JAVA_HOME:-}" ]; then
  if [ -d "/Applications/Android Studio.app/Contents/jbr/Contents/Home" ]; then
    export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
    echo "Using Android Studio JBR: $JAVA_HOME"
  elif [ -x "/usr/libexec/java_home" ]; then
    export JAVA_HOME=$(/usr/libexec/java_home 2>/dev/null || true)
  fi
fi

# 1. Run unit test suite
echo ""
echo "--> Running unit tests..."
VERSION_NAME="$TAG" ./gradlew testDebugUnitTest

# 2. Build release and debug variants
echo ""
echo "--> Building release APK & debug APK..."
VERSION_NAME="$TAG" ./gradlew assembleRelease assembleDebug

# 3. Create dist directory and copy artifacts
echo ""
echo "--> Collecting release artifacts into dist/..."
mkdir -p dist
rm -f dist/*

RELEASE_APK="app/build/outputs/apk/release/app-release.apk"
UNSIGNED_APK="app/build/outputs/apk/release/app-release-unsigned.apk"
DEBUG_APK="app/build/outputs/apk/debug/app-debug.apk"
RELEASE_AAB="app/build/outputs/bundle/release/app-release.aab"

if [ -f "$RELEASE_APK" ]; then
  cp "$RELEASE_APK" "dist/OneTake-$TAG.apk"
elif [ -f "$UNSIGNED_APK" ]; then
  cp "$UNSIGNED_APK" "dist/OneTake-$TAG-unsigned.apk"
fi

if [ -f "$DEBUG_APK" ]; then
  cp "$DEBUG_APK" "dist/OneTake-$TAG-debug.apk"
fi

if [ -f "$RELEASE_AAB" ]; then
  cp "$RELEASE_AAB" "dist/OneTake-$TAG.aab"
fi

# 4. Generate SHA256 checksums
echo ""
echo "--> Generating SHA256 checksums..."
(
  cd dist
  if command -v sha256sum >/dev/null 2>&1; then
    sha256sum * > SHA256SUMS.txt
  elif command -v shasum >/dev/null 2>&1; then
    shasum -a 256 * > SHA256SUMS.txt
  fi
)

echo ""
echo "Artifacts ready in dist/:"
ls -lh dist/

# 5. Git tag creation
if git rev-parse "$TAG" >/dev/null 2>&1; then
  echo "Git tag $TAG already exists locally."
else
  echo ""
  echo "--> Creating local git tag $TAG..."
  git tag -a "$TAG" -m "OneTake $TAG release"
  echo "Created git tag $TAG"
fi

# 6. Publish via gh CLI if requested
if [ "$PUBLISH" = true ]; then
  if command -v gh >/dev/null 2>&1; then
    echo ""
    echo "--> Publishing GitHub release $TAG via gh CLI..."
    gh release create "$TAG" dist/* --title "OneTake $TAG" --generate-notes
    echo "Release $TAG published successfully!"
  else
    echo "Error: gh CLI is not installed or not in PATH."
    exit 1
  fi
else
  echo ""
  echo "=================================================="
  echo " Release $TAG built successfully!"
  echo " Artifacts in: dist/"
  echo " Tag created:  $TAG"
  echo ""
  echo " To publish to GitHub:"
  echo "   git push origin main --tags"
  echo " Or using GitHub CLI:"
  echo "   gh release create $TAG dist/* --title \"OneTake $TAG\" --generate-notes"
  echo "=================================================="
fi
