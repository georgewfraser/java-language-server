#!/bin/sh
DIST_DIR=$(dirname $(readlink -f "${BASH_SOURCE[0]}"))
${DIST_DIR}/launch_linux.sh org.javacs.Main $@
