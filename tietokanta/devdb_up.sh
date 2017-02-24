#!/bin/sh

set -e

docker images | grep harjadb >> /dev/null || sh build_migrated_db_image.sh

echo ""
echo "Käynnistetään valmiiksi migratoitu harjadb Docker-image"
echo ""
docker images | head -n1
docker images | grep harjadb

echo ""
docker run -p 5432:5432 --name harjadb -dit harjadb 1> /dev/null

echo "Odotetaan, että PostgreSQL on käynnissä ja vastaa yhteyksiin portissa 5432"
while ! nc -z localhost 5432; do
    sleep 0.5;
done;

sh devdb_migrate.sh

echo ""
echo "Harja käynnissä! Imagen tiedot:"
echo ""

docker images | head -n1
docker images | grep harjadb

echo ""
echo "Jos imagen rakentamisesta on kovin pitkä aika, voit nopeuttaa kehitysmpäristön käynnistymistä buildaamalla imagen uudelleen."
echo "Komento oh sh build_migrated_db_image.sh"