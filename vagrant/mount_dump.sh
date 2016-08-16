#!/bin/sh

set -e

if [ -z "$2" ]; then
    echo "PROTIP: Käytä mount_prod_dump.sh tai mount_stg_dump.sh"
    echo "Käyttö: $0 <raaka dumppi> <purettu dumppi> [yes]"
    exit 1
fi

RAAKA=$1
PURETTU=$2
DEFAULT_YES=$3

if [ ! -f ../tietokanta/$RAAKA ]; then
    echo "[$(date +"%T")] /tietokanta/$RAAKA tiedostoa ei löytynyt. aja download_dump.sh?"
    exit 1;
fi

# Varmistetaan, että dumppi on jo purettu.
if [ ! -f ../tietokanta/$PURETTU ]; then
    echo "[$(date +"%T")] Löydettiin ladattu dump, mutta sitä ei ole purettu sql-komennoiksi? Puretaan tiedostoon /harja-tietokanta/$PURETTU"
    # Jos muutat tätä, muuta sama rivi myös download_dump.sh
    vagrant ssh -c "pg_restore -Fc -C /harja-tietokanta/$RAAKA > /harja-tietokanta/$PURETTU"
fi

date="$(stat -f "%Sm" ../tietokanta/$PURETTU)"

echo "\n[$(date +"%T")] Otetaan käyttöön staging dump. Dump on luotu: ${date}\n"

vagrant ssh -c "sudo -u postgres psql -f /harja-tietokanta/drop_before_restore.sql && sudo -u postgres psql -q -f /harja-tietokanta/$PURETTU > /dev/null"

echo "\n[$(date +"%T")] Dumppi ajettu sisään."

if [ -n "$DEFAULT_YES" ];
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

