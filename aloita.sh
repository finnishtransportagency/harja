#!/bin/sh

set -e
echo "Luodaan uudestaan harja ja harjatesti tietokannat"

cd vagrant
sh migrate_and_clean.sh
sh migrate_test.sh
cd ..

echo "Käynnistetään repl. Muista käynnistää myös fighweel!"
lein do clean, repl
