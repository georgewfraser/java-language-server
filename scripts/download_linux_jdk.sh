#!/usr/bin/env bash
# Download a copy of linux JDK in jdks/linux

set -e

# Download linux jdk
mkdir -p jdks/linux
cd jdks/linux
curl https://download.java.net/java/GA/jdk18.0.1.1/65ae32619e2f40f3a9af3af1851d6e19/2/GPL/openjdk-18.0.1.1_linux-x64_bin.tar.gz > linux.tar.gz
gunzip -c linux.tar.gz | tar xopf -
rm linux.tar.gz
mv jdk-18.0.1.1 jdk-18
cd ../..