#!/bin/bash
set -euo pipefail

HARJA_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && cd .. && pwd )"

# Poistetaan lukkofilu, mutta jätetään käännetyt filut
if [[ -n $(docker ps | grep harja_harja-app_1) ]]
then
  docker exec harja_harja-app_1 /bin/bash -c 'rm -f ${DC_JAETTU_KANSIO}/compile.* ${DC_JAETTU_KANSIO}/repl.*'
else
  docker run --rm --volume=harja_yhteiset_tiedostot:/yt solita/harja-app:latest /bin/bash -c 'rm -f yt/compile.* yt/repl.*'
fi

docker-compose --env-file ${HARJA_DIR}/.docker_compose_env down