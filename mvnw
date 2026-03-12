#!/bin/sh
set -e
MAVEN_WRAPPER_JAR="$( cd -- "$(dirname "$0")" >/dev/null 2>&1 ; pwd -P )/.mvn/wrapper/maven-wrapper.jar"
DOWNLOAD_URL=$(grep distributionUrl .mvn/wrapper/maven-wrapper.properties | cut -d= -f2)
MVN_DIST_DIR="$HOME/.m2/wrapper/dists"
DIST_ZIP_NAME=$(basename "$DOWNLOAD_URL")
MVN_HOME="$MVN_DIST_DIR/${DIST_ZIP_NAME%.zip}"

if [ ! -d "$MVN_HOME" ]; then
  mkdir -p "$MVN_DIST_DIR"
  echo "Downloading Maven from $DOWNLOAD_URL"
  curl -L "$DOWNLOAD_URL" -o "$MVN_DIST_DIR/$DIST_ZIP_NAME"
  unzip -q "$MVN_DIST_DIR/$DIST_ZIP_NAME" -d "$MVN_DIST_DIR/"
  MVN_HOME=$(find "$MVN_DIST_DIR" -name "mvn" -type f | head -1 | xargs dirname)
fi

JAVA_HOME_CMD=$(dirname $(readlink -f $(which java)))/../
exec "$MVN_HOME/mvn" "$@"
