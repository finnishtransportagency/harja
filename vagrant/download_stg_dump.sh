#!/usr/bin/env bash

if [ -z "$1" ];
then
    echo "[$(date +"%T")] Ladataan stg dump."
    echo "[$(date +"%T")] Voit pakottaa oletusvalinnat antamalla jotain parametrina."
else
    echo "[$(date +"%T")] Ladataan stg dump. Oletusvalinnat pakotettu."
fi

sh download_dump.sh "harja_stg_dump" "restored_stg_dump.sql" harja-db1-stg $1