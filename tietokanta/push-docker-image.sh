#!/usr/bin/env bash

## Tarkasta, että tämän scriptin käyttämä tag vastaa todellisuutta, jos tageihin tehdään muutoksia.
## Default: postgis-3.1
readonly tag_name=${1:-postgis-3.1}

echo "Pusketaan image solita/harjadb:${tag_name}..."

docker push "solita/harjadb:${tag_name}"

echo "Image solita/harjadb:${tag_name} puskettu."