#!/bin/bash
set -euo pipefail

# shellcheck source=../harja_dir.sh
source "$( dirname "${BASH_SOURCE[0]}" )/../harja_dir.sh" || exit

COMPOSE_ENV_FILE="${HARJA_DIR}/.docker_compose_env"
LOCAL_ENV_FILE="${HARJA_DIR}/.local_env"

# shellcheck source=os_riippuvaiset_fns.sh
source "${HARJA_DIR}/sh/os_riippuvaiset_fns.sh"

if type -P lein >/dev/null 2>&1
then
  lein clean;
fi

mkdir -p "${HARJA_DIR}/dev-resources/tmp"

if [[ ! -f "$LOCAL_ENV_FILE" ]]
then
    touch ${LOCAL_ENV_FILE}
    echo "# Nämä muuttujat generoidaan tänne. Älä muokkaa käsin." > ${LOCAL_ENV_FILE}
fi

lisaa_env_muuttuja() {
    NIMI=$1
    shift
    ARVO=$1
    shift
    if [[ -z "$*" ]]
    then
        ENV_FILE=$COMPOSE_ENV_FILE
    else
        ENV_FILE=$LOCAL_ENV_FILE
    fi

    if [[ -n $(awk "/^${NIMI}/ { print }" $ENV_FILE) ]]
    then
        # Ympäristömuuttujalle on annettu jokin arvo
        os_sed_inplace -e "s/${NIMI}.*/${NIMI}=${ARVO//\//\\/}/g" $ENV_FILE
    else
        # Ympäristömuuttujalle ei ole annettu arvoa
        printf "%s\n" "${NIMI}=${ARVO}" >> $ENV_FILE
    fi
}

if [[ "$OMA_OS_TYPE" == 'OSX' ]]
then
    lisaa_env_muuttuja DC_VOLUME_CONSISTENCY delegated local
else
    lisaa_env_muuttuja DC_VOLUME_CONSISTENCY consistent local
fi

if [[ -n "$(echo "$@" | grep emacs)" ]]
then
    lisaa_env_muuttuja LEININGEN_EMACS_PROFILE "dev-emacs" local
else
    lisaa_env_muuttuja LEININGEN_EMACS_PROFILE '' local
fi

if [[ -n "$(echo "$@" | grep clean)" ]]
then
    lisaa_env_muuttuja LEININGEN_CLEAN "'with-profile +dev-container clean'"
else
    lisaa_env_muuttuja LEININGEN_CLEAN ''
fi

lisaa_env_muuttuja BRANCH "$(git branch --show-current)" local
lisaa_env_muuttuja HOST_USER_ID ${UID} local
lisaa_env_muuttuja DC_HARJA_KANSIO "${HARJA_DIR}" local

# --env-file ei tue useaa filua, joten tehdään näin
touch -f "${HARJA_DIR}/yhdistetty_dc_env"

{
    cat "${COMPOSE_ENV_FILE}" | grep -v '#'
    cat "${LOCAL_ENV_FILE}" | grep -v '#'
} > "${HARJA_DIR}/yhdistetty_dc_env"

echo "Käynnistetään compose ja ohjataan output ${HARJA_DIR}/dev-resources/tmp/dc_kaynnistys.log tiedostoon ..."
sudo docker-compose --env-file "${HARJA_DIR}/yhdistetty_dc_env" up > "${HARJA_DIR}/dev-resources/tmp/dc_kaynnistys.log" 2>&1 &

DOCKER_COMPOSE_PID=$!
FRONTEND_REPL_PORT="$(awk -F "=" '/FRONTEND_REPL_PORT/ { print $2 }' "${COMPOSE_ENV_FILE}")"

echo "DOCKER_COMPOSE_PID=${DOCKER_COMPOSE_PID}"

while [[ $(curl -s -o /dev/null -w '%{http_code}' localhost:"${FRONTEND_REPL_PORT}" 2>&1) != '200' &&
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
