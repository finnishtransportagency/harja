#!/bin/sh

psql=psql

yell() { echo "$0: $*" >&2; }
die() { yell "$*"; exit 111; }
try() { "$@" || die "cannot $*"; }

$psql -p5432 -c "SELECT pg_terminate_backend(pg_stat_activity.pid) FROM pg_stat_activity WHERE pg_stat_activity.datname = 'harjatest' AND pid <> pg_backend_pid();"
$psql -p5432 -c "DROP DATABASE IF EXISTS harjatest_template;"
$psql -p5432 -c "CREATE USER harjatest WITH CREATEDB;"
$psql -p5432 -c "ALTER USER harjatest WITH SUPERUSER;"
# temppikanta, kts testi.clj
$psql -p5432 -c "CREATE DATABASE temp OWNER harjatest;"
$psql -p5432 -c "CREATE DATABASE harjatest_template OWNER harjatest;"
$psql -p5432 harjatest_template -c "CREATE EXTENSION IF NOT EXISTS postgis;"
$psql -p5432 harjatest_template -c "CREATE EXTENSION IF NOT EXISTS postgis_topology;"

$psql -p5432 harjatest_template -c "GRANT ALL PRIVILEGES ON ALL TABLES IN SCHEMA public TO harjatest;"

echo "** Aloitetaan migrate **"
try mvn clean compile -Pharjatest flyway:migrate

PGOPTIONS='--client-min-messages=warning'

echo "\n** Luodaan testidataa **"
try $psql -p5432 harjatest_template -X -q -a -l -v ON_ERROR_STOP=1 --pset pager=off -f testidata.sql > /dev/null
echo "** Testikannan template on valmis **"
