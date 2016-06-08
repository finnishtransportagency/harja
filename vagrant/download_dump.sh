#!/bin/sh

set -e

date="$(stat -f "%Sm" ../tietokanta/harja-stg-dump)"
AIKARAJA=7200

if [ ! "$1" = "force" ];
then
    # Jos alle 5 päivää vanha, varmistetaan että halutaanhan jatkaa
    if test `find "../tietokanta/harja-stg-dump" -mmin -$AIKARAJA`
    then
        if [ "$1" = "default" ];
        then
            echo "Dumppi on ladattu $date, ladataan tuoreempi. (Aikaraja on $AIKARAJA minuuttia.)"
        else
            read -p "Dumppi on ladattu $date, haluatko varmasti ladata uuden? [y N]" -n 1 -r
            echo    # (optional) move to a new line
            if [[ $REPLY =~ ^[Yy]$ ]]
            then
                echo "Selvä, ladataan uusin dump."
            else
                echo "Tyydytään vanhaan dumppiin."
                exit 0
            fi
        fi
    else
        echo "Ladataan uusi staging dump."
    fi
else
    echo "Pakotetaan uuden dumpin lataus. Vanha oli ladattu $date"
fi

echo "\nAloitetaan staging dumpin lataus! Hae vaikka kahvia."

ssh harja-db1-stg "sudo -u postgres pg_dump -v -Fc --exclude-table-data=integraatioviesti --exclude-table-data=liite harja" > ../tietokanta/harja-stg-dump

echo "\nDumppi ladattu. Puretaan dummpi .sql komennoiksi."

vagrant ssh -c "pg_restore /harja-tietokanta/harja-stg-dump > /harja-tietokanta/restored-stg-dump.sql"

echo "\nDumppi ladattu ja purettu!"