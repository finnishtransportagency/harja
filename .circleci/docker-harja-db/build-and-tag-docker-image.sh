#!/usr/bin/env bash

# Exit on any error or an unset variable (use optionally -x to print each command)
set -Eeu

## Huomaa, että circle-ci:tä varten buildataan eri image postgis-3.1-circle tagilla .circleci/docker-harja-db pathissa.

## Tarkasta, että tämän scriptin käyttämä tag vastaa todellisuutta, jos tageihin tehdään muutoksia.
## Default: postgis-3.1-circle
readonly tag_name=${1:-postgis-3.1-circle}

echo "Buildataan harjadb-circle imagea..."
docker build -t solita/harjadb .
echo "Build valmis."

echo "Tagataan harjadb-circle image tagilla: ${tag_name}..."
docker tag solita/harjadb "solita/harjadb:${tag_name}"
echo "Tagaus valmis."