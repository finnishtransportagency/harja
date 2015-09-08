#!/bin/sh

echo "Luodaan uudestaan harja ja harjatesti tietokannat"

cd vagrant
sh migrate_and_clean.sh > /dev/null
sh migrate_test.sh > /dev/null
cd ..

echo "Käynnistetään repl. Muista käynnistää myös fighweel!"
lein do clean, repl

