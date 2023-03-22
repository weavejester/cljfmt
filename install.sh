#!/bin/bash

set -euo pipefail

VERSION=0.13.0

case $(uname -s) in
    Linux*)
        PLATFORM=linux
        ;;
    Darwin*)
        PLATFORM=darwin
        ;;
esac

STATIC=

case $(uname -m) in
    aarch64)
        ARCH=aarch64
        ;;
    arm64)
        ARCH=aarch64
        ;;
    *)
        ARCH=amd64
        if [[ "$PLATFORM" == "linux" ]]; then
            STATIC="-static"
        fi
        ;;
esac

BASE_URL="https://github.com/weavejester/cljfmt/releases/download"
URL="${BASE_URL}/${VERSION}/cljfmt-${VERSION}-${PLATFORM}-${ARCH}${STATIC}.tar.gz"

echo -n "Downloading cljfmt binaries... "
curl -o /tmp/cljfmt.tar.gz -sL "$URL"
echo "Done!"

sudo mkdir -p /usr/local/bin
sudo tar -xzf /tmp/cljfmt.tar.gz -C /usr/local/bin
echo "Extracted cljfmt into /usr/local/bin"

rm /tmp/cljfmt.tar.gz
