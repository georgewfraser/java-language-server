#!/usr/bin/env bash
# Download a copy of mac JDK in jdks/mac

set -e

# Download mac jdk (OpenJDK 25, aarch64)

mkdir -p jdks/mac
cd jdks/mac
curl -L "https://download.java.net/java/GA/jdk25.0.2/b1e0dfa218384cb9959bdcb897162d4e/10/GPL/openjdk-25.0.2_macos-aarch64_bin.tar.gz" > mac.tar.gz
tar xzf mac.tar.gz
rm mac.tar.gz
mv jdk-25.0.2.jdk jdk-25
cd ../..
