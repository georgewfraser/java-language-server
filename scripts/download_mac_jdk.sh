#!/usr/bin/env bash
# Download a copy of mac JDK in jdks/mac

set -e

# Download mac jdk (OpenJDK 21, aarch64)
mkdir -p jdks/mac
cd jdks/mac
curl -L https://download.java.net/java/GA/jdk21/fd2272bbf8e04c3dbaee13770090416c/35/GPL/openjdk-21_macos-x64_bin.tar.gz > mac.tar.gz
tar xzf mac.tar.gz
rm mac.tar.gz
mv jdk-21.jdk jdk-21
cd ../..