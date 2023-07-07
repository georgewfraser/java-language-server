#!/usr/bin/env bash
# Create self-contained copy of java in dist/mac

set -e

# Set env variables to build with mac toolchain but linux target
JAVA_HOME="./jdks/mac/jdk-20"

# Build using jlink
rm -rf dist/mac
jlink \
  --module-path $JAVA_HOME/Contents/Home/jmods \
  --add-modules java.base,java.compiler,java.logging,java.sql,java.xml,jdk.compiler,jdk.jdi,jdk.unsupported,jdk.zipfs \
  --output dist/mac \
  --no-header-files \
  --no-man-pages \
  --compress 2