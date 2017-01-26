#!/usr/bin/env bash

set -e

echo "Ajetaan migraatiot"
# Ylempi tarkistus ei vielä takaa, että flyway saa yhteyden, vaan docker on käynnissä
until mvn flyway:info &> /dev/null; do
    echo "Odotetaan että flyway saa yhteyden kantaan.."
    sleep 0.5
done

echo "Yhteys saatu!"
mvn flyway:migrate

echo "Ajetaan testidata"
docker run -v `pwd`:/tietokanta -it --link harjadb:postgres --rm postgres sh /tietokanta/devdb_testidata.sh