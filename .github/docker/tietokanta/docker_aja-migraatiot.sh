#!/bin/bash
set -euo pipefail

if [[ $(whoami) = 'postgres' ]]
then
  cd ~;
else
  # shellcheck disable=SC2016
  POSTGRES_HOME="$(runuser -c 'echo $HOME' -l postgres)";
  cd "$POSTGRES_HOME"
fi

cd harja/tietokanta/
echo "Ajetaan kaikki migraatiot."
## Vanha tapa ajaa migraatio siten, että jatketaan viimeksi ajetusta migraatiosta.
##mvn -Dharja.tietokanta.port="${HARJA_TIETOKANTA_PORTTI:-5432}" -Dharja.tietokanta.host="${HARJA_TIETOKANTA_HOST:-localhost}" flyway:migrate

## -- Aja kaikki migraatiot uusiksi --

psql -h localhost -p "${HARJA_TIETOKANTA_PORTTI:-5432}" -U harja harja -c "DROP DATABASE IF EXISTS harjatest_template;"
psql -h localhost -p "${HARJA_TIETOKANTA_PORTTI:-5432}" -U harja harja -c "DROP DATABASE IF EXISTS harjatest;"

## Puhdista kanta
mvn -Dharja.tietokanta.port="${HARJA_TIETOKANTA_PORTTI:-5432}" -Dharja.tietokanta.host="${HARJA_TIETOKANTA_HOST:-localhost}" \
 flyway:clean

## Aja migraatiot alusta
mvn -Dharja.tietokanta.port="${HARJA_TIETOKANTA_PORTTI:-5432}" -Dharja.tietokanta.host="${HARJA_TIETOKANTA_HOST:-localhost}" \
-Dflyway.baselineVersion="0" -Dflyway.baselineOnMigrate="true" -Dflyway.table="schema_version" \
 flyway:migrate

echo "Migraatio ajettu."
