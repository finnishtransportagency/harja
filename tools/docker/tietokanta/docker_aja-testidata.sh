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

echo "Ajetaan testidata harja-kantaan... Tässä voi kestää jonkin aikaa.";
# Laita psql alkuun PGOPTIONS='--client-min-messages=warning', jos haluat hiljentää testidata.sql lokituksen
psql -h "localhost" -p "${HARJA_TIETOKANTA_PORTTI:-5432}" -U harja harja -X -q -a -v ON_ERROR_STOP=1 --pset pager=off -f testidata.sql > /dev/null;

psql_yllapito() {
  psql -h "localhost" -p "${HARJA_TIETOKANTA_PORTTI:-5432}" -U harja -d postgres -X -v ON_ERROR_STOP=1 "$@"
}

echo "Luodaan harjatest_template kanta harja-kannan pohjalta";
psql_yllapito -c "CREATE DATABASE harjatest_template WITH TEMPLATE harja OWNER harjatest ALLOW_CONNECTIONS false;";
echo "Luodaan harjatest kanta harjatest_template kannan pohjalta";
psql_yllapito -c "CREATE DATABASE harjatest WITH TEMPLATE harjatest_template OWNER harjatest;";

echo "Done. Testidata importattu harja-kantaan!"
