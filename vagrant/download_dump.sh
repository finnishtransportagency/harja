#!/bin/sh

set -e

if [ -z "$3" ]; then
    echo "PROTIP: Käytä download_prod_dump.sh tai download_stg_dump.sh"
    echo "Käyttö: $0 <ladataan tiedostoon> <puretaan tiedostoon> <mistä?> [yes]"
    exit 1
fi

RAAKA=$1
PURETTU=$2
LAHDE=$3
DEFAULT_YES=$4


if [ ! -f $RAAKA ];
then
    echo "[$(date +"%T")] Vanhaa dumppia (../tietokanta/$RAAKA) ei löytynyt. Ladataan uusi."
else
    date="$(stat -f "%Sm" "../tietokanta/$RAAKA")"

    if [ -z "$DEFAULT_YES" ];
    then
        read -p "[$(date +"%T")] Dumppi on ladattu $date, haluatko varmasti ladata uuden? [Y n]" -n 1 -r
        echo
        if [[ $REPLY =~ ^[Nn]$ ]]
        then
            echo "[$(date +"%T")] Tyydytään vanhaan dumppiin."
            exit 0
        else
            echo "[$(date +"%T")] Selvä, ladataan uusin dump."
        fi
    else
        echo "[$(date +"%T")] Pakotetaan uuden dumpin lataus. Vanha oli ladattu $date"
    fi
fi

echo "[$(date +"%T")] Aloitetaan $LAHDE dumpin lataus! Hae vaikka kahvia."
echo "[$(date +"%T")] (Älä välitä 'Could not change directory to /home/tunnus: Permission denied' -virheestä)\n"

ssh $LAHDE "sudo -u postgres pg_dump -Fc --exclude-table-data=integraatioviesti --exclude-table-data=liite harja" > "../tietokanta/$RAAKA"

echo "\n[$(date +"%T")] Dumppi ladattu. Puretaan dummpi .sql komennoiksi.\n"

# Jos muutat tätä, muuta sama rivi myös mount_dump.sh
vagrant ssh -c "pg_restore -Fc -C /harja-tietokanta/$RAAKA > /harja-tietokanta/$PURETTU"

echo "\n[$(date +"%T")] Dumppi ladattu ja purettu!"