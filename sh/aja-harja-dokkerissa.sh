#!/bin/bash
set -euo pipefail

HARJA_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && cd .. && pwd )"
COMPOSE_ENV_FILE="${HARJA_DIR}/.docker_compose_env"

source "${HARJA_DIR}/sh/os_riippuvaiset_fns.sh"
mkdir -p "${HARJA_DIR}/dev-resources/tmp"

lisaa_env_muuttuja() {
  NIMI=$1
  ARVO=$2
  if [[ -n $(awk "/^${NIMI}/ { print }" $COMPOSE_ENV_FILE) ]]
  then
    # Ympäristömuuttujalle on annettu jokin arvo
    os_sed_inplace -e "s/${NIMI}.*/${NIMI}=${ARVO}/g" $COMPOSE_ENV_FILE
  else
    # Ympäristömuuttujalle ei ole annettu arvoa
    printf "%s\n" "${NIMI}=${ARVO}" >> $COMPOSE_ENV_FILE
  fi
}

if [[ "$OMA_OS_TYPE" == 'OSX' ]]
then
    lisaa_env_muuttuja DC_VOLUME_CONSISTENCY delegated
else
    lisaa_env_muuttuja DC_VOLUME_CONSISTENCY consistent
fi

if [[ -n "$(echo $@ | grep clean)" ]]
then
    lisaa_env_muuttuja LEININGEN_CLEAN "'with-profile +dev-container clean'"
else
    lisaa_env_muuttuja LEININGEN_CLEAN '""'
fi

os_sed_inplace -e "s/BRANCH=.*/BRANCH=$(git branch --show-current)/g" "${HARJA_DIR}/.docker_compose_env"

echo "Käynnistetään compose ja ohjataan output ${HARJA_DIR}/dev-resources/tmp/dc_kaynnistys.log tiedostoon ..."
docker-compose --env-file $COMPOSE_ENV_FILE up > "${HARJA_DIR}/dev-resources/tmp/dc_kaynnistys.log" 2>&1 &

DOCKER_COMPOSE_PID=$!
FRONTEND_REPL_PORT="$(awk -F "=" '/FRONTEND_REPL_PORT/ { print $2 }' ${COMPOSE_ENV_FILE})"

echo "DOCKER_COMPOSE_PID=${DOCKER_COMPOSE_PID}"

while [[ $(curl -s -o /dev/null -w '%{http_code}' localhost:${FRONTEND_REPL_PORT} 2>&1) != '200' &&
         -n "$(ps -A | grep ${DOCKER_COMPOSE_PID} | grep -v grep)" ]]
do
    echo "$(ps -A | grep ${DOCKER_COMPOSE_PID} | grep -v grep)"
    echo "Harja ei vielä käynnissä..."
    sleep 20;
done;

if [[ -z "$(ps -A | grep ${DOCKER_COMPOSE_PID} | grep -v grep)" ]]
then
  echo "Docker compose processi kuoli. Katso logit."
fi
