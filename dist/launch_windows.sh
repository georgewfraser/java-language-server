#!/bin/sh
JLINK_VM_OPTIONS="\
--add-exports jdk.compiler/com.sun.tools.javac.api=ALL-UNNAMED \
--add-exports jdk.compiler/com.sun.tools.javac.code=ALL-UNNAMED \
--add-exports jdk.compiler/com.sun.tools.javac.comp=ALL-UNNAMED \
--add-exports jdk.compiler/com.sun.tools.javac.main=ALL-UNNAMED \
--add-exports jdk.compiler/com.sun.tools.javac.tree=ALL-UNNAMED \
--add-exports jdk.compiler/com.sun.tools.javac.model=ALL-UNNAMED \
--add-exports jdk.compiler/com.sun.tools.javac.util=ALL-UNNAMED \
--add-opens jdk.compiler/com.sun.tools.javac.api=ALL-UNNAMED"
DIST_DIR=$(dirname $(readlink -f "${BASH_SOURCE[0]}"))
CLASSPATH_OPTIONS="-classpath ${DIST_DIR}/classpath/gson-2.8.9.jar;${DIST_DIR}/classpath/protobuf-java-3.19.3.jar;${DIST_DIR}/classpath/java-language-server.jar"
${DIST_DIR}/windows/bin/java $JLINK_VM_OPTIONS $CLASSPATH_OPTIONS $@
