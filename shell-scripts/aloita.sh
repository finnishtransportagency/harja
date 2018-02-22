#!/bin/sh

set -e
echo "Luodaan uudestaan harja-tietokanta"

cd vagrant
sh migrate_and_clean.sh
cd ..

echo "Käynnistetään repl."
lein do clean, compile, repl
