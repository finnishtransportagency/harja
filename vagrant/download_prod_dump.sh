#!/usr/bin/env bash

if [ -z "$1" ];
then
    echo "[$(date +"%T")] Ladataan prod dump."
    echo "[$(date +"%T")] Voit pakottaa oletusvalinnat antamalla jotain parametrina."
else
    echo "[$(date +"%T")] Ladataan prod dump. Oletusvalinnat pakotettu."
fi

sh download_dump.sh "harja_prod_dump" "restored_prod_dump.sql" harja-db1 $1