#!/bin/bash
set -euo pipefail

HARJA_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && cd .. && pwd )"
POISTA_MUUT_BRANCHIT="$([[ -n "$(echo $@ | grep rob)" ]]; echo $?)"
export BRANCH="$(git branch --show-current)"

aja-yhteisessa-voluumissa() {
  if [[ -n $(docker ps | grep harja_harja-app_1) ]]
  then
    sudo docker exec -e BRANCH harja_harja-app_1 /bin/bash -c "cd \$\{DC_JAETTU_KANSIO\}; $1"
  else
    sudo docker run -e BRANCH --rm --volume=harja_yhteiset_tiedostot:/yt solita/harja-app:latest /bin/bash -c "cd /yt; $1"
  fi
}

# Poistetaan lukkofilut, mutta jätetään käännetyt filut
echo "POISTETAAN LUKKO FILUT"
aja-yhteisessa-voluumissa 'find . -maxdepth 1 -mindepth 1 -type f -exec rm {} \;'

if [[ $POISTA_MUUT_BRANCHIT -eq 0 ]]
then
  # Poistetaan muiden branchien käännetyt filut
  echo "POISTETAAN MUIDEN BRANCHIEN COMPILE FILUT"
  aja-yhteisessa-voluumissa 'find . -maxdepth 1 -mindepth 1 -type d -exec /bin/bash -c "if [[ ! \$1 = */$BRANCH ]]; then rm -rf \$1; fi;" _ {} \;'
fi
echo "SAMMUTETAAN DOCKER COMPOSE"
sudo docker-compose --env-file "${HARJA_DIR}/dev-resources/tmp/yhdistetty_dc_env" down
