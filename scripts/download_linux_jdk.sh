#!/usr/bin/env bash
# Download a copy of linux JDK in jdks/linux

set -e

# Download linux jdk (OpenJDK 25, x64)
mkdir -p jdks/linux
cd jdks/linux
curl -L https://download.java.net/java/GA/jdk25.0.2/b1e0dfa218384cb9959bdcb897162d4e/10/GPL/openjdk-25.0.2_linux-x64_bin.tar.gz > linux.tar.gz
tar xzf linux.tar.gz
rm linux.tar.gz
mv jdk-25.0.2 jdk-25
cd ../..