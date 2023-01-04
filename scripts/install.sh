#!/bin/bash
INSTALL_DIR=${INSTALL_DIR:="/usr/local/java-language-server"}
INSTALL_BIN=${INSTALL_BIN:="/usr/local/bin"}
OS="unknown"
if which -s uname;then
  OS=$(uname |tr '[:upper:]' '[:lower:]')
fi

uninstall() {
  if echo ${INSTALL_DIR}|grep -q "/usr/local" || echo ${INSTALL_BIN}|grep -q "/usr/local";then
    local sudoprompt="SUDO password required to remove language server from INSTALL_DIR=${INSTALL_DIR} and INSTALL_BIN=${INSTALL_BIN}: "
    sudo -p "${sudoprompt}" rm -rf ${INSTALL_DIR} ${INSTALL_BIN}/{java-debug-server,java-language-server}
  else
    rm -rf ${INSTALL_DIR} ${INSTALL_BIN}/{java-debug-server,java-language-server}
  fi
}

install() {
  if ! echo "${OS}" |grep -Eq '(darwin|linux)';then
    echo "Windows/Uknown OS easy install not implemented yet please manually install"
    exit 1
  fi

  if echo ${INSTALL_DIR}|grep -q "/usr/local" || echo ${INSTALL_BIN}|grep -q "/usr/local";then
    local sudoprompt="SUDO password required to install language server to INSTALL_DIR=${INSTALL_DIR} and INSTALL_BIN=${INSTALL_BIN}: "

    sudo -p "${sudoprompt}" -v
    uninstall
    sudo -p "${sudoprompt}" cp -r dist ${INSTALL_DIR}
    sudo -p "${sudoprompt}" ln -s "${INSTALL_DIR}/java-language-server" "${INSTALL_BIN}/java-language-server"
    sudo -p "${sudoprompt}" ln -s "${INSTALL_DIR}/java-debug-server" "${INSTALL_BIN}/java-debug-server"

  else
    uninstall
    cp -r dist ${INSTALL_DIR}
    ln -s "${INSTALL_DIR}/java-language-server" "${INSTALL_BIN}/java-language-server"
    ln -s "${INSTALL_DIR}/java-debug-server" "${INSTALL_BIN}/java-debug-server"
  fi
}

build() {
  if [[ "${OS}" == 'darwin' ]]; then
    ./scripts/download_mac_jdk.sh
    ./scripts/link_mac.sh
  elif [[ "${OS}" == 'linux' ]]; then
    ./scripts/download_linux_jdk.sh
    ./scripts/link_linux.sh
  else # hopefully windows
    ./scripts/download_windows_jdk.sh
    ./scripts/link_windows.sh
  fi
}

case "$1" in
  build)
    build
    ;;
  uninstall)
    uninstall
    ;;
  install)
    build
    install
    ;;
  help)
    echo "Usage: $0 { install(default) | build | uninstall }"
    echo "defaults to install"
    exit 1
    ;;
  *)
    echo -n "Do you want to install? Y/N: " && read -r
    r=$(echo "${REPLY}" |tr '[:upper:]' '[:lower:]')
    if [[ "${r}" == "y" ]];then
      build
      install
    else
      echo "Install cancelled..."
      exit 1
    fi
    ;;
esac
