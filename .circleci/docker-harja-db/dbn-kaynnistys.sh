#!/bin/bash

set -euo pipefail

export BRANCH="$CIRCLE_BRANCH"
export COMMIT="$CIRCLE_SHA1"
bash hae-migraatiot-ja-testidata.sh; fi;
pg_ctl start;
bash odota-kaynnistymista.sh;
bash aja-migraatiot.sh;
bash aja-testidata.sh;