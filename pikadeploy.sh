#!/bin/sh

function msg {
    echo "**************************************************************"
    echo "$1"
    echo "**************************************************************"
}

function error_exit {
    echo "_*_*_*_*_*_*_*_*_*_*_*_*_*_*_*_*_*_*_*_*_*_*_*_*_*_*_*_*_*_*_*_"
    echo "$1"
    exit 1
}

set -e

START_TS=`date +%s`

HARJA_ENV=harja-dev$1

msg "Nyt tehdään quick and dirty deploy $HARJA_ENV, EI testejä, EI tietokantamigraatioita! #yolo"

lein tuotanto-notest
scp target/harja-*-standalone.jar root@$HARJA_ENV:/opt/harja/harja-nightly.jar
ssh root@$HARJA_ENV "service harja restart"


msg "Tehty, toivottavasti ei ole pahasti rikki!"


END_TS=`date +%s`
msg "Suorite kesti `echo "$END_TS-$START_TS"|bc` sekuntia."
