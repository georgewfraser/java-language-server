#!/bin/sh
DIST_DIR=$(dirname $(readlink -f "$0"))
${DIST_DIR}/launch_windows.sh org.javacs.debug.JavaDebugServer $@
