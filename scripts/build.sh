#!/bin/bash

set -e

# Needed once
if [ ! -e node_modules ]; then
    npm install
fi

# Build standalone java
if [ ! -e jdks/linux/jdk-13 ]; then
    ./scripts/download_linux_jdk.sh
fi
if [ ! -e jdks/windows/jdk-13 ]; then
    ./scripts/download_windows_jdk.sh
fi
if [ ! -e dist/linux/bin/java ]; then
    ./scripts/link_linux.sh
fi
if [ ! -e dist/windows/bin/java.exe ]; then
    ./scripts/link_windows.sh
fi
if [ ! -e dist/mac/bin/java ]; then
    ./scripts/link_mac.sh
fi

# Compile sources
if [ ! -e src/main/java/com/google/devtools/build/lib/analysis/AnalysisProtos.java ]; then
    ./scripts/gen_proto.sh
fi
./scripts/format.sh
JAVA_HOME=`/usr/libexec/java_home -v 13` mvn package -DskipTests

# Build vsix
npm run-script vscode:build

code --install-extension build.vsix --force

echo 'Reload VSCode to update extension'
