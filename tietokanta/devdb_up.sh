#!/usr/bin/env bash

set -e

DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
if [ "$(uname -m)" == "arm64" ]; then
  IMAGE=solita/harjadb:postgis-3.1-arm64
else
  IMAGE=solita/harjadb:postgis-3.1
fi

devdb_image_lkm=$(docker image list -q --filter=reference=${IMAGE} 2>/dev/null | wc -l)
if [[ "${devdb_image_lkm}" != *1 ]]; then # wc tulostaa välilyöntejä ennen numeroa, siksi *1 glob
    echo "Imagea" $IMAGE "ei löydetty. Yritetään pullata."
    if ! docker pull $IMAGE; then
        echo $IMAGE "ei ole docker hubissa. Buildataan."
        docker build -t $IMAGE . ;
    fi
    echo ""
fi

docker run -p "127.0.0.1:${HARJA_TIETOKANTA_PORTTI:-5432}:${HARJA_TIETOKANTA_PORTTI:-5432}" \
  --name "${POSTGRESQL_NAME:-harjadb}" -dit -v "$DIR":/var/lib/pgsql/harja/tietokanta \
  ${IMAGE} /bin/bash -c \
       "sed -i 's/port = 5432/port = ${HARJA_TIETOKANTA_PORTTI:-5432}/g' /var/lib/pgsql/${POSTGRESQL_VERSION:-12}/data/postgresql.conf;
        sed -i 's/#port =/port =/g' /var/lib/pgsql/${POSTGRESQL_VERSION:-12}/data/postgresql.conf;
        sudo -iu postgres /usr/pgsql-${POSTGRESQL_VERSION:-12}/bin/pg_ctl start -D /var/lib/pgsql/${POSTGRESQL_VERSION:-12}/data;
        /bin/bash"; > /dev/null

echo "Käynnistetään Docker-image" $IMAGE
echo ""
docker images | head -n1
docker images | grep "$(echo "$IMAGE" | sed "s/:.*//")" | grep "$(echo "$IMAGE" | sed "s/.*://")"

echo ""
echo "Odotetaan, että PostgreSQL on käynnissä ja vastaa yhteyksiin portissa ${HARJA_TIETOKANTA_PORTTI:-5432}"

# Tarkista onko linux vai osx
if [[ "$OSTYPE" == "darwin"* ]]
then
    # Hostina toimii Mac OSX
    OMA_OS_TYPE='OSX'
else
    OMA_OS_TYPE=''
fi

# Jos osx, niin aja vähä eri komennolla
if [[ ${OMA_OS_TYPE} == 'OSX' ]]
    then
      while ! nc -z localhost "${HARJA_TIETOKANTA_PORTTI:-5432}"; do
          echo "nukutaan..."
          sleep 0.5;
      done;
else
    while ! pg_isready -d harja -h localhost -p ${HARJA_TIETOKANTA_PORTTI:-5432}; do
        echo "nukutaan..."
        sleep 0.5;
    done;
fi

# shellcheck disable=SC2088
docker exec --user postgres -e HARJA_TIETOKANTA_HOST -e HARJA_TIETOKANTA_PORTTI "${HARJA_TIETOKANTA_HOST:-harjadb}" /bin/bash -c "~/aja-migraatiot.sh"
# shellcheck disable=SC2088
docker exec --user postgres -e HARJA_TIETOKANTA_HOST -e HARJA_TIETOKANTA_PORTTI "${HARJA_TIETOKANTA_HOST:-harjadb}" /bin/bash -c "~/aja-testidata.sh"

echo ""
echo "Harjan tietokanta käynnissä! Imagen tiedot:"
echo ""

docker image list --filter=reference=${IMAGE}
