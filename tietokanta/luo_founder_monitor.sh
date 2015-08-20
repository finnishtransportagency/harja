#!/bin/sh

psql='/Applications/Postgres.app/Contents/Versions/9.4/bin'/psql

$psql -p5432 -c "DROP DATABASE monitor;"
$psql -p5432 -c "CREATE DATABASE monitor;"

$psql -p5432 harja -c "CREATE USER monitor;"
$psql -p5432 harja -c "GRANT ALL PRIVILEGES ON ALL TABLES IN SCHEMA public TO monitor;"

'/Applications/Postgres.app/Contents/Versions/9.4/bin'/psql -p5432 monitor -f src/main/resources/db/scripts/founder_monitor.sql

