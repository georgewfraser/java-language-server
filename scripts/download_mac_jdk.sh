#!/usr/bin/env bash
# Download a copy of mac JDK in jdks/mac

set -e

# Download mac jdk
mkdir -p jdks/mac
cd jdks/mac
curl https://download.java.net/java/GA/jdk20.0.1/b4887098932d415489976708ad6d1a4b/9/GPL/openjdk-20.0.1_macos-x64_bin.tar.gz > mac.tar.gz
gunzip -c mac.tar.gz | tar xopf -
rm mac.tar.gz
mv jdk-20.0.1.1.jdk jdk-20
cd ../..