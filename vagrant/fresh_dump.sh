#!/bin/sh

set -e

if [ -z "$1" ];
then
    echo "[$(date +"%T")] Voit antaa parametriksi joko 'force' (pakotetaan lataus) tai 'default' (oletusarvot kelpaa)."
fi

sh download_dump.sh $1

sh mount_dump.sh