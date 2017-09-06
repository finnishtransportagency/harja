#!/usr/bin/env bash

set -e
set -x
DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
cd $DIR
echo "Ajetaan migraatiot harja-kantaan"

until mvn flyway:info &>/dev/null; do
    echo "Odotetaan että flyway saa yhteyden kantaan..."
    sleep 0.5
done

echo "Yhteys saatu!"
mvn flyway:migrate
cd -
docker run -v $DIR:/tietokanta -it --link harjadb:postgres --rm postgres sh /tietokanta/devdb_testidata.sh
