#!/bin/bash

set -euo pipefail

apt-get update
apt-get -y install musl-tools

wget https://github.com/madler/zlib/releases/download/v1.2.13/zlib-1.2.13.tar.gz
tar -xzf zlib-1.2.13.tar.gz
cd zlib-1.2.13

CC=musl-gcc ./configure --static --prefix="/usr/local"
make CC=musl-gcc
make install

cd ..
install -Dm644 "/usr/local/lib/libz.a" "/usr/lib/x86_64-linux-musl/libz.a"
ln -s /usr/bin/musl-gcc /usr/bin/x86_64-linux-musl-gcc
