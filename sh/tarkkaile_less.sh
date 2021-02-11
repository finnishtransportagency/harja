#!/bin/bash
set -eu

source "$( dirname "${BASH_SOURCE[0]}" )/harja_dir.sh" || exit

LESS_WATCH_PID="$( pgrep -f [t]arkkaile_less_muutoksia.sh || echo '' )"

if [ -n "$LESS_WATCH_PID" ];
then
  echo "----- Löydettiin käynnissä oleva less tarkkailu -----"
  echo "$( ps -ax | grep [t]arkkaile_less_muutoksia.sh )"
  echo "Tapetaan prosessi $LESS_WATCH_PID"
  echo "-----------------------------------------------------"
  kill -9 $LESS_WATCH_PID
fi

echo "Generoidaan less -> CSS taustalla..."
bash ${HARJA_DIR}/sh/tarkkaile_less_muutoksia.sh "${1:-''}" &