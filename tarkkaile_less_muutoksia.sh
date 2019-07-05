#!/bin/bash
set -uo pipefail

LESS_TIEDOSTOT=$( ls -l dev-resources/less/ );

while true; do
  sleep 3;
  LESS_TIEDOSTOT_UUSI=$( ls -l dev-resources/less/ );
  if [ "$LESS_TIEDOSTOT" != "$LESS_TIEDOSTOT_UUSI" ];
  then
    echo "Muutoksia huomattu LESS tiedostoissa";
    lein less once;
  fi;
  LESS_TIEDOSTOT=$LESS_TIEDOSTOT;
done
