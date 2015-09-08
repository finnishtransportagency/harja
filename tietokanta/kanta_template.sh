#!/bin/sh

psql=psql

yell() { echo "$0: $*" >&2; }
die() { yell "$*"; exit 111; }
try() { "$@" || die "cannot $*"; }

# ei pitäisi olla tarvetta tappaa backend-prosesseja templatekannalle mutta tapetaan silti

$psql -p5432 -c "SELECT pg_terminate_backend(pg_stat_activity.pid) FROM pg_stat_activity WHERE pg_stat_activity.datname = 'harja_template' AND pid <> pg_backend_pid();"

$psql -p5432 -c "DROP DATABASE IF EXISTS harja_template;"
$psql -p5432 -c "CREATE USER harja WITH CREATEDB;"
$psql -p5432 -c "CREATE DATABASE harja_template OWNER harja;"
$psql -p5432 harja_template -c "CREATE EXTENSION IF NOT EXISTS postgis;"
$psql -p5432 harja_template -c "CREATE EXTENSION IF NOT EXISTS postgis_topology;"

$psql -p5432 harja_template -c "GRANT ALL PRIVILEGES ON ALL TABLES IN SCHEMA public TO harja;"

echo "** Aloitetaan migrate **"
try mvn clean compile -Pharja_template flyway:migrate

PGOPTIONS='--client-min-messages=warning'
echo "\n** Luodaan testidataa **"
try $psql -p5432 harja_template -X -q -a -l -v ON_ERROR_STOP=1 --pset pager=off -f testidata.sql > /dev/null

echo "** Harja-kannan template valmis! **"
