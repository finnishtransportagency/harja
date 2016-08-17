#!/usr/bin/env bash

if [ -z "$1" ];
then
    echo "[$(date +"%T")] Mountataan prod dump."
    echo "[$(date +"%T")] Antamalla jotain paramaterina valitset automaattisesti oletusvalinnat."
else
    echo "[$(date +"%T")] Ladataan prod dump. Ei kysellä turhia."
fi

sh mount_dump.sh "harja_prod_dump" "restored_prod_dump.sql" $1