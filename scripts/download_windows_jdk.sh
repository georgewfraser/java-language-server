#!/usr/bin/env bash
# Download a copy of windows JDK in jdks/windows

set -e

# Download windows jdk (OpenJDK 25, x64)
mkdir -p jdks/windows
cd jdks/windows
curl -L https://download.java.net/java/GA/jdk25.0.2/b1e0dfa218384cb9959bdcb897162d4e/10/GPL/openjdk-25.0.2_windows-x64_bin.zip > windows.zip
unzip windows.zip
rm windows.zip
mv jdk-25.0.2 jdk-25
cd ../..