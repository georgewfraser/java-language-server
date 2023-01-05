#!/bin/sh
DIST_DIR=$(dirname $(readlink -f "$0"))
${DIST_DIR}/launch_mac.sh org.javacs.debug.JavaDebugServer $@
