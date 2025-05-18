#!/usr/bin/env bash
# Download a copy of windows JDK in jdks/windows

set -e

# Download windows jdk (OpenJDK 21, x64)
mkdir -p jdks/windows
cd jdks/windows
curl -L https://download.java.net/java/GA/jdk21/fd2272bbf8e04c3dbaee13770090416c/35/GPL/openjdk-21_windows-x64_bin.zip > windows.zip
unzip windows.zip
rm windows.zip
# already named correctly
cd ../..