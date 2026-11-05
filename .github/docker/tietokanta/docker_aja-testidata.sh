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

palauta_yhteydet() {
  psql_yllapito <<'SQL'
SELECT format('ALTER DATABASE %I ALLOW_CONNECTIONS true;', datname)
  FROM pg_database
 WHERE datname IN ('harja', 'harjatest_template')
   AND NOT datallowconn
\gexec
SQL
}

# Palautetaan yhteydet myös silloin, jos skripti päättyy virheeseen.
trap palauta_yhteydet EXIT
# Keskeytyksissä poistutaan signaalikoodeilla; EXIT-käsittelijä palauttaa yhteydet.
trap 'exit 130' INT
trap 'exit 143' TERM
trap 'exit 129' HUP

# PostgreSQL vaatii TEMPLATE-kopiointia varten, ettei lähdekantaan ole yhteyksiä.
# Estetään uudet yhteydet ja katkaistaan vanhat, jotta tietokannan kopiointi onnistuu varmasti.
echo "Estetään uudet harja-kannan yhteydet ja katkaistaan olemassa olevat yhteydet.";
psql_yllapito -c "ALTER DATABASE harja ALLOW_CONNECTIONS false;"
psql_yllapito -q -c "
SELECT pid,
       usename,
       application_name,
       client_addr,
       pg_terminate_backend(pid, 10000) AS katkaistu
  FROM pg_stat_activity
 WHERE datname = 'harja';"

echo "Luodaan harjatest_template kanta harja-kannan pohjalta";
psql_yllapito -c "CREATE DATABASE harjatest_template WITH TEMPLATE harja OWNER harjatest ALLOW_CONNECTIONS false;";
echo "Luodaan harjatest kanta harjatest_template kannan pohjalta";
psql_yllapito -c "CREATE DATABASE harjatest WITH TEMPLATE harjatest_template OWNER harjatest;";

palauta_yhteydet
trap - EXIT INT TERM HUP

echo "Done. Testidata importattu harja-kantaan!"
