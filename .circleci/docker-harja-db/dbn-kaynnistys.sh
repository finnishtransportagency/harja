#!/bin/bash

set -euo pipefail

echo "Haetaan migraatiot ja testidata"
bash hae-migraatiot-ja-testidata.sh;
echo "Käynnistetään possu"
pg_ctl start;
bash odota-kaynnistymista.sh;
bash aja-migraatiot.sh;
bash aja-testidata.sh;