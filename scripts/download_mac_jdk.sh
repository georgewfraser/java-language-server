#!/usr/bin/env bash
# Download a copy of mac JDK in jdks/mac

set -e

# Download mac jdk
mkdir -p jdks/mac
cd jdks/mac
curl https://download.java.net/java/GA/jdk18.0.1.1/65ae32619e2f40f3a9af3af1851d6e19/2/GPL/openjdk-18.0.1.1_macos-x64_bin.tar.gz > mac.tar.gz
gunzip -c mac.tar.gz | tar xopf -
rm mac.tar.gz
mv jdk-18.0.1.1.jdk jdk-18
cd ../..