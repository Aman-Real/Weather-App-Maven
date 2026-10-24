#!/bin/sh
# Maven Wrapper (only-script style): downloads the Maven version pinned in
# .mvn/wrapper/maven-wrapper.properties on first use, then runs it.
set -e

BASE_DIR=$(cd "$(dirname "$0")" && pwd)
PROPS="$BASE_DIR/.mvn/wrapper/maven-wrapper.properties"
DIST_URL=$(sed -n 's/^distributionUrl=//p' "$PROPS" | tr -d '\r')
[ -n "$DIST_URL" ] || { echo "distributionUrl missing in $PROPS" >&2; exit 1; }

DIST_ZIP=${DIST_URL##*/}
DIST_NAME=${DIST_ZIP%-bin.zip}
MAVEN_DIR="${MAVEN_USER_HOME:-$HOME/.m2}/wrapper/dists/$DIST_NAME"

if [ ! -x "$MAVEN_DIR/bin/mvn" ]; then
  echo "Downloading $DIST_URL"
  TMP_DIR=$(mktemp -d)
  if command -v curl >/dev/null 2>&1; then
    curl -fsSL -o "$TMP_DIR/maven.zip" "$DIST_URL"
  else
    wget -q -O "$TMP_DIR/maven.zip" "$DIST_URL"
  fi
  unzip -q "$TMP_DIR/maven.zip" -d "$TMP_DIR"
  mkdir -p "$(dirname "$MAVEN_DIR")"
  mv "$TMP_DIR/$DIST_NAME" "$MAVEN_DIR"
  rm -rf "$TMP_DIR"
fi

export MAVEN_PROJECTBASEDIR="$BASE_DIR"
exec "$MAVEN_DIR/bin/mvn" "$@"
