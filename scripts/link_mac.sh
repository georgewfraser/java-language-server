#!/bin/bash
# Create self-contained copy of java in dist/mac

set -e

# Set env variables to build with mac toolchain but linux target
JAVA_HOME="./jdks/mac/jdk-18/Contents/Home"

# Build using jlink
rm -rf dist/mac
${JAVA_HOME}/bin/jlink \
  --module-path ${JAVA_HOME}/jmods \
  --add-modules java.base,java.compiler,java.logging,java.sql,java.xml,jdk.compiler,jdk.jdi,jdk.unsupported,jdk.zipfs \
  --output dist/mac \
  --no-header-files \
  --no-man-pages \
  --compress 2

mvn package -DskipTests
