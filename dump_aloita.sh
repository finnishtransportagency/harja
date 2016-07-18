#!/usr/bin/env bash

set -e
echo "Mountataan aiemmin ladattu stg dump. Uuden dumpin voit ladata ajamalla vagrant/fresh_dump.sh tai vagrant/download_dump.sh"

cd vagrant
sh mount_dump.sh 'default'
cd ..

echo "Käynnistetään REPL."
lein do clean, compile, repl
