#!/usr/bin/env bash

set -e

if [ -z "$1" ]; then
    echo "Usage: $0 <env> [unit-tests?(=true)] [branch]"
    exit 1
fi

echo "\nLuodaan uudestaan harja ja harjatesti tietokannat\n"

cd vagrant
sh migrate_and_clean.sh
echo "Luotiin harja"
sh migrate_test.sh
echo "Luotiin harjatesti\n"
cd ..

echo "Tehdään clean"
lein clean > /dev/null

sh deploy2.sh $1