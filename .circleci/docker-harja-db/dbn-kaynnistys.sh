#!/bin/bash

set -euo pipefail

bash hae-migraatiot-ja-testidata.sh;
pg_ctl start;
bash odota-kaynnistymista.sh;
bash aja-migraatiot.sh;
bash aja-testidata.sh;