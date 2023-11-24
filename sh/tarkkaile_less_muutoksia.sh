#!/bin/bash
set -uo pipefail

source "$( dirname "${BASH_SOURCE[0]}" )/harja_dir.sh" || exit

LESS_TIEDOSTOT=$( ls -l "${HARJA_DIR}"/dev-resources/less/ );

if ! command -v npx &> /dev/null
then
   echo "npx-komentoa ei löytynyt. Sen pitäisi kuulua npm-pakettiin version 7.0.0 jälkeen."
   exit 1
fi

# Ajetaan less-käännös kerran heti ja aina kun tiedostot muuttuvat
npx lessc "${HARJA_DIR}"/dev-resources/less/application/application.less "${HARJA_DIR}"/resources/public/css/application.css
npx lessc "${HARJA_DIR}"/dev-resources/less/laadunseuranta/application/laadunseuranta.less "${HARJA_DIR}"/resources/public/css/laadunseuranta.css

while true; do
  sleep 3;
  LESS_TIEDOSTOT_UUSI=$( ls -l "${HARJA_DIR}"/dev-resources/less/ );
  if [ "$LESS_TIEDOSTOT" != "$LESS_TIEDOSTOT_UUSI" ];
  then
    echo "Muutoksia huomattu LESS tiedostoissa";
    # shellcheck disable=SC2086
    set +e
    npx lessc "${HARJA_DIR}"/dev-resources/less/application/application.less ${HARJA_DIR}/resources/public/css/application.css
    npx lessc "${HARJA_DIR}"/dev-resources/less/laadunseuranta/application/laadunseuranta.less "${HARJA_DIR}"/resources/public/css/laadunseuranta.css

    echo "LESS käännös valmis";
  fi;
  LESS_TIEDOSTOT=$LESS_TIEDOSTOT_UUSI;
done
