#!/bin/bash
# Download a copy of mac JDK in jdks/mac

set -e

# Download mac jdk if it doesn't exist
if [[ -x jdks/mac/jdk-18/Contents/Home/bin/java ]];then
  exit 0
fi

mkdir -p jdks/mac
cd jdks/mac
curl https://download.java.net/java/GA/jdk18.0.1.1/65ae32619e2f40f3a9af3af1851d6e19/2/GPL/openjdk-18.0.1.1_macos-x64_bin.tar.gz > mac.tar.gz
gunzip -c mac.tar.gz | tar xopf -
rm mac.tar.gz
ln -s jdk-18.0.1.1.jdk jdk-18
cd ../..
