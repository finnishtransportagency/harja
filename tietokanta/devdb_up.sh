#!/usr/bin/env bash

set -e

DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"

IMAGE=${1:-solita/harjadb:centos-12}

if ! docker images | grep $IMAGE >> /dev/null; then
    echo "Imagea" $IMAGE "ei löydetty. Yritetään pullata."
    if ! docker pull $IMAGE; then
        echo $IMAGE "ei ole docker hubissa. Buildataan."
        docker build -t $IMAGE . ;
    fi
    echo ""
fi

docker run -p 127.0.0.1:5432:5432 --name harjadb -dit $IMAGE 1> /dev/null

echo "Käynnistetään Docker-image" $IMAGE
echo ""
docker images | head -n1
docker images | grep $(echo $IMAGE | sed "s/:.*//") | grep $(echo $IMAGE | sed "s/.*://")

echo ""
echo "Odotetaan, että PostgreSQL on käynnissä ja vastaa yhteyksiin portissa 5432"
while ! nc -z localhost 5432; do
    echo "nukutaan..."
    sleep 0.5;
done;

bash $DIR/devdb_migrate.sh

echo ""
echo "Harjan tietokanta käynnissä! Imagen tiedot:"
echo ""

docker images | head -n1
docker images | grep $IMAGE
