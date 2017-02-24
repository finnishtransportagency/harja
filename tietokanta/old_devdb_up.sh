#!/bin/sh

set -e

echo "Käynnistetään harjadb Docker-image (PostgreSQL 9.5 + PostGIS 2.3)"
docker run -p 5432:5432 --name harjadb -e POSTGRES_DB=harja -e POSTGRES_USER=harja -d mdillon/postgis:9.5

echo "Odotetaan, että PostgreSQL on käynnissä ja vastaa yhteyksiin portissa 5432"
while ! nc -z localhost 5432; do
    sleep 0.5;
done;

sh old_devdb_migrate.sh
