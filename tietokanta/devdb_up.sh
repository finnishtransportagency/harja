#!/bin/sh

echo "Käynnistetään harjadb docker image (PostgreSQL 9.5 + PostGIS 2.3)"
docker run -p 5432:5432 --name harjadb -e POSTGRES_DB=harja -e POSTGRES_USER=harja -d mdillon/postgis:9.5

echo "Odotetaan, että postgres käynnissä"
sleep 10



echo "Ajetaan migraatiot"
mvn flyway:migrate


echo "Ajetaan testidata"
docker run -v `pwd`:/tietokanta -it --link harjadb:postgres --rm postgres sh /tietokanta/devdb_testidata.sh
