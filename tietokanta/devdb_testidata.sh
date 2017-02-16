#!/bin/sh

### HUOM: tämä ajetaan postgres docker kontin sisällä ###

set -e

cd /tietokanta

echo "Ajetaan testidata sisään"
psql -h "$POSTGRES_PORT_5432_TCP_ADDR" -p "$POSTGRES_PORT_5432_TCP_PORT" -U harja harja -X -q -a -v ON_ERROR_STOP=1 --pset pager=off -f testidata.sql > /dev/null

echo "Tapa porsaat"
psql -h "$POSTGRES_PORT_5432_TCP_ADDR" -p "$POSTGRES_PORT_5432_TCP_PORT" -U harja harja -c "SELECT pg_terminate_backend(pg_stat_activity.pid) FROM pg_stat_activity WHERE pg_stat_activity.datname = 'harja' AND pid <> pg_backend_pid();"

echo "Luodaan harjatest + temp"
psql -h "$POSTGRES_PORT_5432_TCP_ADDR" -p "$POSTGRES_PORT_5432_TCP_PORT" -U harja harja -c "CREATE USER harjatest WITH CREATEDB;"
psql -h "$POSTGRES_PORT_5432_TCP_ADDR" -p "$POSTGRES_PORT_5432_TCP_PORT" -U harja harja -c "ALTER USER harjatest WITH SUPERUSER;"
psql -h "$POSTGRES_PORT_5432_TCP_ADDR" -p "$POSTGRES_PORT_5432_TCP_PORT" -U harja harja -c "CREATE DATABASE harjatest WITH TEMPLATE harja OWNER harjatest;"
psql -h "$POSTGRES_PORT_5432_TCP_ADDR" -p "$POSTGRES_PORT_5432_TCP_PORT" -U harja harja -c "CREATE DATABASE harjatest_template WITH TEMPLATE harjatest OWNER harjatest;"
psql -h "$POSTGRES_PORT_5432_TCP_ADDR" -p "$POSTGRES_PORT_5432_TCP_PORT" -U harja harja -c "CREATE DATABASE temp OWNER harjatest;"