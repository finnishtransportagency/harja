#!/bin/sh

set -e

if [ ! -f ../tietokanta/harja-stg-dump ]; then
    echo "/tietokanta/harja-stg-dump tiedostoa ei löytynyt. aja download_dump.sh?"
    exit 1;
fi

# Varmistetaan, että dumppi on jo purettu.
if [ ! -f ../tietokanta/restored-stg-dump.sql ]; then
    # Jos muutat tätä, muuta sama rivi myös download_dump.sh
    vagrant ssh -c "pg_restore -Fc -C /harja-tietokanta/harja-stg-dump > /harja-tietokanta/restored-stg-dump.sql"
fi

date="$(stat -f "%Sm" ../tietokanta/restored-stg-dump.sql)"

echo "\nOtetaan käyttöön staging dump. Dump on luotu: ${date}"
echo "\n"

vagrant ssh -c "sudo -u postgres psql -f /harja-tietokanta/drop_before_restore.sql && sudo -u postgres psql -q -f /harja-tietokanta/restored-stg-dump.sql > /dev/null"

echo "\nValmis!"