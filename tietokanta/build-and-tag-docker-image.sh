#!/usr/bin/env bash

# Exit on any error or an unset variable (use optionally -x to print each command)
set -Eeu

## Huomaa, että circle-ci:tä varten buildataan eri image postgis-3.1-circle tagilla .circleci/docker-harja-db pathissa.

## Tarkasta, että tämän scriptin käyttämä tag vastaa todellisuutta, jos tageihin tehdään muutoksia.
## Default: postgis-3.1
if [ "$(uname -m)" == "arm64" ]; then
  readonly tag_name=${1:-postgis-3.1-arm64}
else
  readonly tag_name=${1:-postgis-3.1}
fi

echo "Buildataan harjadb imagea..."
docker build -t solita/harjadb .
echo "Build valmis."

echo "Tagataan harjadb image tagilla: ${tag_name}..."
docker tag solita/harjadb "solita/harjadb:${tag_name}"
echo "Tagaus valmis."