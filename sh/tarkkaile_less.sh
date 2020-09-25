#!/bin/bash
set -eu

LESS_WATCH_PID="$( pgrep -f [t]arkkaile_less_muutoksia.sh )"

if [ -n "$LESS_WATCH_PID" ];
then
  echo "----- Löydettiin käynnissä oleva less tarkkailu -----"
  echo "$( ps -ax | grep [t]arkkaile_less_muutoksia.sh )"
  echo "Tapetaan prosessi $LESS_WATCH_PID"
  echo "-----------------------------------------------------"
  kill -9 $LESS_WATCH_PID
fi

echo "Generoidaan less -> CSS taustalla..."
bash tarkkaile_less_muutoksia.sh ${1:-""} &