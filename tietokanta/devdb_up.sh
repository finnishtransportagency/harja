#!/usr/bin/env bash

# Lue Ohjeet GitHub Container Registryn käyttöön:
# https://github.com/finnishtransportagency/harja/blob/develop/README.md#kehitt%C3%A4j%C3%A4n-kirjautuminen-container-registryyn

set -e

DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
IMAGE=ghcr.io/finnishtransportagency/harja_harjadb:latest

# Alla saatavilla olevia versioita kokeiluja varten
# https://github.com/finnishtransportagency/harja/pkgs/container/harja_harjadb

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

# Luo yhteinen harja-verkko, mikäli sitä ei ole vielä olemassa
# Muut containerit voivat ottaa suoraan tietokantaan yhteyttä käyttämällä IP-osoitteen sijasta containerin nimeä: 'harjadb'
# Tätä verkkoa käytetään mm. testaustarkoituksessa, jossa Lambda-kontti ottaa yhteyttä tietokantaan.
# Palauttaa aina exit-koodin 0, vaikka verkko olisikin jo olemassa, jotta skripti ei keskeydy.
docker network create harja-net 2>/dev/null || true

# Jotta harjadb kontin tietoliikenne toimii oikein, täytyy IPv6 disabloida asetuksella: --sysctl net.ipv6.conf.all.disable_ipv6=1
# https://docs.docker.com/engine/release-notes/26.0/#bug-fixes-and-enhancements
docker run -p "127.0.0.1:${HARJA_TIETOKANTA_PORTTI:-5432}:5432" \
    --network harja-net \
    --name "${POSTGRESQL_NAME:-harjadb}" -dit -v "$DIR":/var/lib/postgresql/harja/tietokanta \
    --sysctl net.ipv6.conf.all.disable_ipv6=1 \
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

echo "PostgreSQL versiotiedot:"
docker exec --user postgres "${POSTGRESQL_NAME:-harjadb}" psql -c 'SELECT version();'
docker exec --user postgres "${POSTGRESQL_NAME:-harjadb}" psql -U harja -c 'SELECT extname, extversion FROM pg_extension;'

"${DIR}"/devdb_alusta.sh

echo ""
echo "Harjan tietokanta käynnissä! Imagen tiedot:"
echo ""

docker image list --filter=reference=${IMAGE}
