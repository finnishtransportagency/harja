#!/usr/bin/env bash

set -e

echo "Ajetaan migraatiot harja-kantaan"

until mvn flyway:info &> /dev/null; do
    echo "Odotetaan että flyway saa yhteyden kantaan..."
    sleep 0.5
done

echo "Yhteys saatu!"
mvn flyway:migrate

docker run -v `pwd`:/tietokanta -it --link harjadb:postgres --rm postgres sh /tietokanta/devdb_testidata.sh