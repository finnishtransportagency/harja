#!/usr/bin/env bash

if [ -z "$1" ];
then
    echo "[$(date +"%T")] Mountataan stg dump."
    echo "[$(date +"%T")] Antamalla jotain paramaterina valitset automaattisesti oletusvalinnat."
else
    echo "[$(date +"%T")] Ladataan stg dump. Ei kysellä turhia."
fi

sh mount_dump.sh "harja_stg_dump" "restored_stg_dump.sql" $1