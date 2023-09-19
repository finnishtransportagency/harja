#!/usr/bin/env bash

DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"

"${DIR}"/devdb_down.sh;
"${DIR}"/devdb_up_uusi.sh

# Vanha up-scripti säästetty vielä backup-toimintona, mikäli uusi ei lähde toimimaan.
# Vanha scripti poistetaan lopulta kokonaan.
# "${DIR}"/devdb_up.sh;
