#!/usr/bin/env bash

set -e

echo "Ajetaan migraatiot harja-kantaan"
DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
cd $DIR
until mvn flyway:info &> /dev/null; do
    echo "Odotetaan että flyway saa yhteyden kantaan..."
    sleep 0.5
done

echo "Yhteys saatu!"
mvn flyway:migrate
