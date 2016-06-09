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

echo "\n[$(date +"%T")] Otetaan käyttöön staging dump. Dump on luotu: ${date}\n"

vagrant ssh -c "sudo -u postgres psql -f /harja-tietokanta/drop_before_restore.sql && sudo -u postgres psql -q -f /harja-tietokanta/restored-stg-dump.sql > /dev/null"

echo "\n[$(date +"%T")] Dumppi ajettu sisään."

if [ -n "$1" ];
then
    echo "[$(date +"%T")] Annoit skriptille parametrin. Ajetaan migrate.sh\n"
    sh migrate.sh > /dev/null
    echo "\n[$(date +"%T")] Valmis!"
else
    read -p "[$(date +"%T")] Ajetaanko migrate.sh (data ei katoa)? [Y n]" -n 1 -r
    echo
    if [[ $REPLY =~ ^[Nn]$ ]]
    then
        echo "[$(date +"%T")] Homma on hoidettu!"
    else
        echo "[$(date +"%T")] Ajetaan migrate.sh\n"
        sh migrate.sh > /dev/null
        echo "\n[$(date +"%T")] Se oli siinä!"
    fi
fi

