#!/bin/bash
set -euxo -pipefail

LESS_WATCH_PID=$( pgrep -f [l]ein\ less\ auto )

if [ -n "$LESS_WATCH_PID" ];
then
  echo "----- Löydettiin käynnissä oleva less watch -----"
  echo "$( ps -ax | grep [l]ein\ less\ auto )"
  echo "Tapetaan prosessi $LESS_WATCH_PID"
  echo "-------------------------------------------------"
  kill -9 $LESS_WATCH_PID
fi

echo "Generoidaan less -> CSS taustalla..."
lein less auto &
echo "Käynnistetään figwheel"
lein build-dev