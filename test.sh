#!/bin/sh

set -e

echo "\nLuodaan uudestaan harja ja harjatesti tietokannat\n"

cd vagrant
sh migrate_and_clean.sh
echo "Luotiin harja"
sh migrate_test.sh
echo "Luotiin harjatesti\n"
cd ..

echo "Tehdään clean"
lein clean > /dev/null

# Ajetaan unit testit
sh unit.sh

echo "\n\n*****************"
echo "Käynnistetään figwheel ja REPL"
echo "Odota rauhassa"
echo "Poistu kirjoittamalla (exit) tai painamalla ctrl-d"
echo "*****************\n"

lein trampoline figwheel &
lein repl

echo "Kiitos testaamisesta!"
kill %1
