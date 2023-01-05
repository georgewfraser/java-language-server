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
DIST_DIR=$(dirname $(readlink -f "$0"))
CLASSPATH_JARS="$(find ${DIST_DIR}/classpath -type f -iname '*.jar'|xargs |sed 's/ /:/g')"
CLASSPATH_OPTIONS="-classpath ${CLASSPATH_JARS}"
${DIST_DIR}/linux/bin/java $JLINK_VM_OPTIONS $CLASSPATH_OPTIONS $@
