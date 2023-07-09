#!/usr/bin/env bash
# Download a copy of windows JDK in jdks/windows

set -e

# Download windows jdk
mkdir -p jdks/windows
cd jdks/windows
curl https://download.java.net/java/GA/jdk18.0.1.1/65ae32619e2f40f3a9af3af1851d6e19/2/GPL/openjdk-18.0.1.1_windows-x64_bin.zip > windows.zip
unzip windows.zip
rm windows.zip
mv jdk-18.0.1.1 jdk-18
cd ../..