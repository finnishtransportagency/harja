#!/usr/bin/env bash

DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"

"${DIR}"/devdb_down.sh;

# Ajetaan macos käyttäjillä vanha devdb-up ARM tuella.
# Tämä käyttää vanhaa tietokanta-imageversiota
# Mikäli haluat kokeilla uutta imagea lue ensin ohjeet: https://github.com/finnishtransportagency/harja/blob/develop/README.md#kehitt%C3%A4j%C3%A4n-kirjautuminen-container-registryyn
# ja aja alla pelkästään "${DIR}"/devdb_up_uusi.sh.
# Linux-käyttäjillä uusi image toimii suoraan, eikä vaadi lisätoimenpiteitä.

if [ "$(uname -m)" == "arm64" ]; then
    "${DIR}"/devdb_up.sh;
else
    "${DIR}"/devdb_up_uusi.sh
fi
