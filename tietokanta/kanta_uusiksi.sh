#!/bin/sh

psql=psql

$psql -p5432 -c "SELECT pg_terminate_backend(pg_stat_activity.pid) FROM pg_stat_activity WHERE pg_stat_activity.datname = 'harja' AND pid <> pg_backend_pid();"
$psql -p5432 -c "DROP DATABASE harja;"
$psql -p5432 -c "CREATE DATABASE harja OWNER harja TEMPLATE harja_template;"
