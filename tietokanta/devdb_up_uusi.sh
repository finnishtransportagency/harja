#!/usr/bin/env bash

# Lue Ohjeet GitHub Container Registryn käyttöön:
# https://github.com/finnishtransportagency/harja/blob/develop/README.md#kehitt%C3%A4j%C3%A4n-kirjautuminen-container-registryyn

set -e

DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
IMAGE=ghcr.io/finnishtransportagency/harja_harjadb:latest

# Alla saatavilla olevia versioita kokeiluja varten
# https://github.com/finnishtransportagency/harja/pkgs/container/harja_harjadb/versions


devdb_image_lkm=$(docker image list -q --filter=reference=${IMAGE} 2>/dev/null | wc -l)
if [[ "${devdb_image_lkm}" != *1 ]]; then # wc tulostaa välilyöntejä ennen numeroa, siksi *1 glob
    echo "Imagea" $IMAGE "ei löydetty. Yritetään pullata."
    if ! docker pull $IMAGE; then
        echo $IMAGE "ei ole GitHub Container Registryssä. Buildataan."
        (
            cd ../.github/docker/tietokanta
            ./build-image.sh
        )
    fi
    echo ""
fi

docker run -p "127.0.0.1:${HARJA_TIETOKANTA_PORTTI:-5432}:${HARJA_TIETOKANTA_PORTTI:-5432}" \
    --name "${POSTGRESQL_NAME:-harjadb}" -dit -v "$DIR":/var/lib/postgresql/harja/tietokanta \
    ${IMAGE} >/dev/null

echo "Käynnistetään Docker-image" $IMAGE
echo ""
docker images | head -n1
docker images | grep "$(echo "$IMAGE" | sed "s/:.*//")" | grep "$(echo "$IMAGE" | sed "s/.*://")"

echo ""
echo "Odotetaan, että PostgreSQL on käynnissä ja vastaa yhteyksiin portissa ${HARJA_TIETOKANTA_PORTTI:-5432}"

until docker exec "${POSTGRESQL_NAME:-harjadb}" pg_isready; do
    echo "nukutaan..."
    sleep 0.5
done

# shellcheck disable=SC2088
docker exec --user postgres -e HARJA_TIETOKANTA_HOST -e HARJA_TIETOKANTA_PORTTI "${HARJA_TIETOKANTA_HOST:-harjadb}" /bin/bash -c "~/aja-migraatiot.sh"
# shellcheck disable=SC2088
docker exec --user postgres -e HARJA_TIETOKANTA_HOST -e HARJA_TIETOKANTA_PORTTI "${HARJA_TIETOKANTA_HOST:-harjadb}" /bin/bash -c "~/aja-testidata.sh"

echo ""
echo "Harjan tietokanta käynnissä! Imagen tiedot:"
echo ""

docker image list --filter=reference=${IMAGE}
