#!/usr/bin/env bash
# Download a copy of linux JDK in jdks/linux

set -e

# Download linux jdk (OpenJDK 21, x64)
mkdir -p jdks/linux
cd jdks/linux
curl -L https://download.java.net/java/GA/jdk21/fd2272bbf8e04c3dbaee13770090416c/35/GPL/openjdk-21_linux-x64_bin.tar.gz > linux.tar.gz
tar xzf linux.tar.gz
rm linux.tar.gz
# already named correctly
cd ../..