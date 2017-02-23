#!/bin/sh

set -e

echo "Käynnistetään valmiiksi migratoitu harja-db Docker image"
docker run -p 5432:5432 --name harjadb -d jarzka/harja-db

echo "Odotetaan, että PostgreSQL on käynnissä ja vastaa yhteyksiin portissa 5432"
while ! nc -z localhost 5432; do
    sleep 0.5;
done;

sh devdb_migrate.sh
