#!/usr/bin/env bash
set -euxo pipefail

psql -h localhost -U harjatest template1 -c "DROP DATABASE IF EXISTS harjatest_template;"
psql -h localhost -U harjatest template1 -c "CREATE DATABASE harjatest_template OWNER harjatest;"

psql -h localhost -U harjatest harjatest_template -c "CREATE EXTENSION IF NOT EXISTS postgis;"
psql -h localhost -U harjatest harjatest_template -c "CREATE EXTENSION IF NOT EXISTS pg_trgm;"

cd tietokanta

mvn -f pom.vanha.xml clean compile flyway:migrate -Dflyway.url=jdbc:postgresql://localhost/harjatest_template -Dflyway.user=harjatest

sh ../Jenkins/skriptit/kannan_puukotus.sh

PGOPTIONS='--client-min-messages=warning' psql -h localhost -U harjatest harjatest_template  -X -q -a -l -v ON_ERROR_STOP=1 --pset pager=off -f testidata.sql

psql -h localhost -U harjatest template1 -c "DROP DATABASE IF EXISTS harjatest;"
psql -h localhost -U harjatest template1 -c "CREATE DATABASE harjatest OWNER harjatest TEMPLATE harjatest_template;"
