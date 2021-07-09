#!/bin/bash
set -euo pipefail

# shellcheck source=harja_dir.sh
source "$( dirname "${BASH_SOURCE[0]}" )/../harja_dir.sh" || exit

POISTA_MUUT_BRANCHIT="$([[ -n "$(echo "$@" | grep rob)" ]]; echo $?)"
POISTA_TRAMPOLIINIT="$([[ -n "$(echo "$@" | grep rt)" ]]; echo $?)"
BRANCH="$(git branch --show-current)"
export BRANCH;

aja-yhteisessa-voluumissa() {
  if [[ -n $(docker ps | grep harja_harja-app_1) ]]
  then
    docker exec -e BRANCH harja_harja-app_1 /bin/bash -c "cd \$\{DC_JAETTU_KANSIO\}; $1"
  else
    docker run -e BRANCH --rm --volume=harja_yhteiset_tiedostot:/yt solita/harja-app:latest /bin/bash -c "cd /yt; $1"
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

if [[ $POISTA_TRAMPOLIINIT -eq 0 ]]
then
    # Poistetaan figwheelin trampoliini cachet
    echo "POISTETAAN TRAMPOLIINI CACHET"
    if [[ -d "${HARJA_DIR}/target/trampolines" ]]
    then
      find "${HARJA_DIR}/target/trampolines" -type f -delete;
    fi
fi

echo "SAMMUTETAAN DOCKER COMPOSE"
docker-compose --env-file "${HARJA_DIR}/yhdistetty_dc_env" down
