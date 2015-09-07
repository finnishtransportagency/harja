#!/bin/sh

echo "Luodaan uudestaan harja ja harjatesti tietokannat"

cd vagrant
sh migrate_and_clean.sh > /dev/null
sh migrate_test.sh > /dev/null
cd ..

lein do clean, test2junit > /dev/null

# tarkista junit virheet!

lein figwheel dev &
lein repl

kill %1

