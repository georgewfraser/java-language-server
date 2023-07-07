#!/usr/bin/env bash
# Create self-contained copy of java in dist/linux

set -e

# Set env variables to build with mac toolchain but linux target
JAVA_HOME="./jdks/linux/jdk-20"

# Build in dist/linux
rm -rf dist/linux
jlink \
  --module-path $JAVA_HOME/jmods \
  --add-modules java.base,java.compiler,java.logging,java.sql,java.xml,jdk.compiler,jdk.jdi,jdk.unsupported,jdk.zipfs \
  --output dist/linux \
  --no-header-files \
  --no-man-pages \
  --compress 2