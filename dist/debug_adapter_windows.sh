#!/bin/sh
DIST_DIR=$(dirname $(readlink -f "${BASH_SOURCE[0]}"))
${DIST_DIR}/launch_windows.sh org.javacs.debug.JavaDebugServer $@
