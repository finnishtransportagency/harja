#!/bin/bash
set -uo pipefail

HARJA_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && cd .. && pwd )"
LEIN_PROFILE="$1"

LESS_TIEDOSTOT=$( ls -l ${HARJA_DIR}/dev-resources/less/ );

while true; do
  sleep 3;
  LESS_TIEDOSTOT_UUSI=$( ls -l ${HARJA_DIR}/dev-resources/less/ );
  if [ "$LESS_TIEDOSTOT" != "$LESS_TIEDOSTOT_UUSI" ];
  then
    echo "Muutoksia huomattu LESS tiedostoissa";
    if [[ -z "$LEIN_PROFILE" ]]
    then
      lein less once;
    else
      lein with-profile +${LEIN_PROFILE} less once;
    fi
  fi;
  LESS_TIEDOSTOT=$LESS_TIEDOSTOT_UUSI;
done
